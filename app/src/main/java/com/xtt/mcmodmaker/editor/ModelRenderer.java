package com.xtt.mcmodmaker.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

import com.xtt.mcmodmaker.core.BlockModel;
import com.xtt.mcmodmaker.core.BlockModels;
import com.xtt.mcmodmaker.core.BlockTextureLib;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 把方块模型（.geo.json）真正渲染成图标。
 *
 * 与 {@link BlockIconRenderer} 的分工：那个只认标准立方体，把六面贴图斜切贴上去；
 * 这里按模型的骨骼 / 立方体 / 每个面的 UV 逐个三角形光栅化，
 * 台阶、柱子、不规则造型都能画对，以后自定义模型的方块也直接走这一套。
 *
 * 视角固定成等轴测（和原版创造模式图标一致）：
 *   screenX = x - z
 *   screenY = (x + z) / 2 - y
 * 也就是从 (+x, +y, +z) 看过去，能看见顶面、+x 面和 +z 面；
 * 远近按 x + y + z 排，从远往近画，透明贴图也能正确叠加。
 *
 * 先按 4 倍分辨率画再缩回去，边缘不会毛刺。
 */
public final class ModelRenderer {

    /** 超采样倍数。 */
    private static final int SS = 4;

    /** 四周留白比例。 */
    private static final float PADDING = 0.06f;

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(8 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    /** 缺贴图时用的灰，保证形状看得见。 */
    private static final int FALLBACK_COLOR = 0xFFB4B4B4;

    private ModelRenderer() {
    }

    /**
     * 渲染模型图标。
     *
     * @return 这个方块没有自定义模型、或渲染失败时返回 null（调用方退回立方体 / 占位图）
     */
    public static Bitmap render(Context context, String blockId, File projectDir, int sizePx) {
        if (context == null || blockId == null || projectDir == null || sizePx <= 0) return null;
        BlockModels.Spec spec = BlockModels.spec(projectDir, blockId);
        if (spec == null || spec.model == null) return null;

        String key = "m|" + blockId + "@" + sizePx + "@" + projectDir.getAbsolutePath();
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;

        Bitmap out;
        try {
            out = draw(context, spec, projectDir, sizePx);
        } catch (Exception e) {
            out = null;
        }
        if (out != null) CACHE.put(key, out);
        return out;
    }

    /** 参与渲染的一个面：投影后的四个点和平均深度。 */
    private static final class Shot {
        final BlockModel.Face face;
        final float[][] pts = new float[4][2];
        float depth;

        Shot(BlockModel.Face face) {
            this.face = face;
        }
    }

    private static Bitmap draw(Context context, BlockModels.Spec spec, File projectDir, int sizePx) {
        BlockModel model = spec.model;
        int w = sizePx * SS;
        int h = sizePx * SS;

        // 1. 背面剔除 + 投影
        List<Shot> shots = new ArrayList<>();
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (BlockModel.Face f : model.faces) {
            if (!facingCamera(f)) continue;
            Shot s = new Shot(f);
            float depth = 0;
            for (int i = 0; i < 4; i++) {
                float x = f.verts[i][0];
                float y = f.verts[i][1];
                float z = f.verts[i][2];
                s.pts[i][0] = x - z;
                s.pts[i][1] = (x + z) * 0.5f - y;
                depth += x + y + z;
                if (s.pts[i][0] < minX) minX = s.pts[i][0];
                if (s.pts[i][0] > maxX) maxX = s.pts[i][0];
                if (s.pts[i][1] < minY) minY = s.pts[i][1];
                if (s.pts[i][1] > maxY) maxY = s.pts[i][1];
            }
            s.depth = depth / 4f;
            shots.add(s);
        }
        // 一个面都没留下说明模型是「朝里」的开放平面，那就干脆不剔除
        if (shots.isEmpty()) {
            for (BlockModel.Face f : model.faces) {
                Shot s = new Shot(f);
                float depth = 0;
                for (int i = 0; i < 4; i++) {
                    float x = f.verts[i][0];
                    float y = f.verts[i][1];
                    float z = f.verts[i][2];
                    s.pts[i][0] = x - z;
                    s.pts[i][1] = (x + z) * 0.5f - y;
                    depth += x + y + z;
                    if (s.pts[i][0] < minX) minX = s.pts[i][0];
                    if (s.pts[i][0] > maxX) maxX = s.pts[i][0];
                    if (s.pts[i][1] < minY) minY = s.pts[i][1];
                    if (s.pts[i][1] > maxY) maxY = s.pts[i][1];
                }
                s.depth = depth / 4f;
                shots.add(s);
            }
        }
        if (shots.isEmpty()) return null;

        // 2. 缩放到目标尺寸
        float spanX = Math.max(0.0001f, maxX - minX);
        float spanY = Math.max(0.0001f, maxY - minY);
        float usable = 1f - PADDING * 2f;
        float scale = Math.min(w * usable / spanX, h * usable / spanY);
        float offX = (w - spanX * scale) / 2f - minX * scale;
        float offY = (h - spanY * scale) / 2f - minY * scale;

        // 3. 远的先画
        Collections.sort(shots, new Comparator<Shot>() {
            @Override public int compare(Shot a, Shot b) {
                return Float.compare(a.depth, b.depth);
            }
        });

        // 4. 逐面光栅化
        int[] buf = new int[w * h];
        for (Shot s : shots) {
            int[][] screen = new int[4][2];
            for (int i = 0; i < 4; i++) {
                screen[i][0] = Math.round(s.pts[i][0] * scale + offX);
                screen[i][1] = Math.round(s.pts[i][1] * scale + offY);
            }
            Bitmap tex = textureFor(context, spec, projectDir, s.face.key);
            drawQuad(buf, w, h, screen, s.face.uv, tex,
                    model.textureWidth, model.textureHeight, shadeOf(s.face));
        }

        Bitmap big = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        big.setPixels(buf, 0, w, 0, 0, w, h);
        Bitmap scaled = Bitmap.createScaledBitmap(big, sizePx, sizePx, true);
        if (scaled != big) big.recycle();
        return scaled;
    }

    /** 面朝相机才算可见。相机在 (+x,+y,+z)，法线点乘 (1,1,1) 大于 0 就朝着它。 */
    private static boolean facingCamera(BlockModel.Face f) {
        return f.normal[0] + f.normal[1] + f.normal[2] > 0.0001f;
    }

    /** 明暗：顶面最亮，+z 面次之，+x 面最暗 —— 和原版图标一致。 */
    private static float shadeOf(BlockModel.Face f) {
        float[] n = f.normal;
        float ax = Math.abs(n[0]);
        float ay = Math.abs(n[1]);
        float az = Math.abs(n[2]);
        if (ay >= ax && ay >= az) return n[1] > 0 ? 1.0f : 0.45f;
        if (az >= ax) return n[2] > 0 ? 0.8f : 0.7f;
        return n[0] > 0 ? 0.6f : 0.7f;
    }

    private static Bitmap textureFor(Context context, BlockModels.Spec spec,
                                     File projectDir, String faceKey) {
        String path = spec.textureFor(faceKey);
        if (path == null) return null;
        return BlockTextureLib.texture(context, path, projectDir);
    }

    // ==================== 光栅化 ====================

    /** 画一个四边形（拆成两个三角形）。 */
    private static void drawQuad(int[] buf, int w, int h, int[][] p, float[] uv,
                                 Bitmap tex, float texW, float texH, float shade) {
        drawTriangle(buf, w, h, p[0], p[1], p[2],
                uv[0], uv[1], uv[2], uv[3], uv[4], uv[5], tex, texW, texH, shade);
        drawTriangle(buf, w, h, p[0], p[2], p[3],
                uv[0], uv[1], uv[4], uv[5], uv[6], uv[7], tex, texW, texH, shade);
    }

    private static void drawTriangle(int[] buf, int w, int h,
                                     int[] a, int[] b, int[] c,
                                     float ua, float va, float ub, float vb, float uc, float vc,
                                     Bitmap tex, float texW, float texH, float shade) {
        float ax = a[0], ay = a[1];
        float bx = b[0], by = b[1];
        float cx = c[0], cy = c[1];

        float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 0.0001f) return;

        int minX = (int) Math.floor(Math.min(ax, Math.min(bx, cx)));
        int maxX = (int) Math.ceil(Math.max(ax, Math.max(bx, cx)));
        int minY = (int) Math.floor(Math.min(ay, Math.min(by, cy)));
        int maxY = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));
        if (maxX < 0 || maxY < 0 || minX >= w || minY >= h) return;
        if (minX < 0) minX = 0;
        if (minY < 0) minY = 0;
        if (maxX > w) maxX = w;
        if (maxY > h) maxY = h;

        int texPxW = tex == null ? 0 : tex.getWidth();
        int texPxH = tex == null ? 0 : tex.getHeight();
        int[] texPixels = null;
        if (tex != null && texPxW > 0 && texPxH > 0) {
            texPixels = new int[texPxW * texPxH];
            tex.getPixels(texPixels, 0, texPxW, 0, 0, texPxW, texPxH);
        }

        float invArea = 1f / area;
        int fr = (FALLBACK_COLOR >> 16) & 0xFF;
        int fg = (FALLBACK_COLOR >> 8) & 0xFF;
        int fb = FALLBACK_COLOR & 0xFF;

        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;
                float w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) * invArea;
                float w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) * invArea;
                float w2 = 1f - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;

                float u = ua * w0 + ub * w1 + uc * w2;
                float v = va * w0 + vb * w1 + vc * w2;

                int r, g, bl, alpha;
                if (texPixels != null) {
                    float fx = u / texW * texPxW;
                    float fy = v / texH * texPxH;
                    int tx = (int) Math.floor(fx);
                    int ty = (int) Math.floor(fy);
                    if (tx < 0) tx = 0;
                    if (ty < 0) ty = 0;
                    if (tx >= texPxW) tx = texPxW - 1;
                    if (ty >= texPxH) ty = texPxH - 1;
                    int p = texPixels[ty * texPxW + tx];
                    alpha = p >>> 24;
                    if (alpha == 0) continue;
                    r = (int) (((p >> 16) & 0xFF) * shade);
                    g = (int) (((p >> 8) & 0xFF) * shade);
                    bl = (int) ((p & 0xFF) * shade);
                } else {
                    alpha = 255;
                    r = (int) (fr * shade);
                    g = (int) (fg * shade);
                    bl = (int) (fb * shade);
                }

                int i = y * w + x;
                if (alpha >= 255) {
                    buf[i] = 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
                } else {
                    int dst = buf[i];
                    int da = dst >>> 24;
                    int dr = (dst >> 16) & 0xFF;
                    int dg = (dst >> 8) & 0xFF;
                    int db = dst & 0xFF;
                    float t = alpha / 255f;
                    int oa = (int) (alpha + da * (1 - t));
                    int or = (int) (r * t + dr * (1 - t));
                    int og = (int) (g * t + dg * (1 - t));
                    int ob = (int) (bl * t + db * (1 - t));
                    buf[i] = (clamp(oa) << 24) | (clamp(or) << 16) | (clamp(og) << 8) | clamp(ob);
                }
            }
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    public static void clearCache() {
        CACHE.evictAll();
    }
}
