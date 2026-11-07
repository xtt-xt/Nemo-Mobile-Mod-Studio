package com.xtt.mcmodmaker.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.LruCache;

import com.xtt.mcmodmaker.core.BlockTextureLib;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 把方块的六面贴图渲染成一张等轴测图标，效果和创造模式物品栏里的方块一致。
 *
 * 画法：32×32 的画布上叠三个平行四边形，各自用仿射矩阵把 16×16 的贴图斜切过去，
 * 再用亮度滤镜压暗侧面（顶 100%、左 80%、右 60%，与游戏内的明暗关系一致）。
 * 不用逐像素计算，Canvas 的 drawBitmap 会走 native 路径。
 *
 * 贴图缺失时用灰色立方体顶上，别让格子里空着——同一屏里有的有图有的没图最难看。
 */
public final class BlockIconRenderer {

    /** 单个面的仿射矩阵，{a,b,c,d,e,f} 表示 x'=ax+by+c, y'=dx+ey+f，坐标系为 32×32。 */
    private static final float[] TOP = {1f, -1f, 16f, 0.5f, 0.5f, 0f};
    private static final float[] LEFT = {1f, 0f, 0f, 0.5f, 1f, 8f};
    private static final float[] RIGHT = {1f, 0f, 16f, -0.5f, 1f, 16f};

    /** 侧面压暗系数，与游戏的方块渲染一致。 */
    private static final int SHADE_LEFT = 204;   // 80%
    private static final int SHADE_RIGHT = 153;  // 60%

    /** 平原草地的生物群系色，取自官方 biomes 配置。 */
    private static final int GRASS = 0xFF91BD59;

    /**
     * 整面染色的贴图。
     *
     * 这些贴图本身就是灰阶的，整张乘一遍颜色就对了。
     */
    private static final Map<String, Integer> TINT_FULL = new HashMap<>();

    /**
     * 只染灰阶像素的贴图。
     *
     * 草方块侧面是「棕色泥土 + 顶上一圈灰色草边」，整面乘颜色会把泥土也染绿；
     * 所以只对饱和度极低的像素上色，泥土那部分原样保留。
     */
    private static final Map<String, Integer> TINT_GRAY = new HashMap<>();

    static {
        TINT_FULL.put("textures/blocks/grass_top", GRASS);
        TINT_FULL.put("textures/blocks/grass_carried_top", GRASS);
        TINT_FULL.put("textures/blocks/grass_side_overlay", GRASS);

        TINT_GRAY.put("textures/blocks/grass_side", GRASS);
        TINT_GRAY.put("textures/blocks/grass_side_carried", GRASS);
        TINT_GRAY.put("textures/blocks/grass_carried", GRASS);
    }

    private static final LruCache<String, Bitmap> ICON_CACHE =
            new LruCache<String, Bitmap>(6 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    /** 染色后的贴图缓存，键是「工程目录 + 路径」，工程换了贴图也不会串。 */
    private static final Map<String, Bitmap> TINTED = new HashMap<>();

    /** 灰色占位贴图，缺图的方块用它渲染立方体。 */
    private static Bitmap placeholderTexture;

    private BlockIconRenderer() {
    }

    /**
     * 渲染方块图标。
     *
     * @param blockId    如 minecraft:oak_planks
     * @param projectDir 当前工程根目录（自定义方块从里面取贴图），可为 null
     * @param sizePx     输出边长，建议 32 / 48 / 64
     * @return 没有这个方块的贴图数据时返回 null（调用方改用占位图）
     */
    public static Bitmap render(Context context, String blockId, File projectDir, int sizePx) {
        if (blockId == null) return null;
        String key = "b|" + blockId + "@" + sizePx + "@" + dirKey(projectDir);
        Bitmap cached = ICON_CACHE.get(key);
        if (cached != null) return cached;

        String[] facePaths = BlockTextureLib.resolveFaces(context, blockId, projectDir);
        if (facePaths == null) return null;

        Bitmap up = faceBitmap(context, facePaths[0], projectDir);
        Bitmap down = faceBitmap(context, facePaths[1], projectDir);
        Bitmap north = faceBitmap(context, facePaths[2], projectDir);
        Bitmap east = faceBitmap(context, facePaths[4], projectDir);
        // 顶面是必需的，缺了就没法画；侧面缺就退回顶面的贴图
        if (up == null) return null;
        // 草方块那类侧面贴图只画了上面一圈草边、泥土部分是透明的（游戏里由底面贴图垫底），
        // 直接画会缺一大块，这里按同样的办法补上
        north = underlay(north, down);
        east = underlay(east, down);
        if (north == null) north = up;
        if (east == null) east = up;

        Bitmap out = compose(up, north, east, sizePx);
        ICON_CACHE.put(key, out);
        return out;
    }

    /**
     * 灰色占位立方体：没有贴图的方块用它，保证格子里始终有图。
     *
     * @return 占位贴图也读不到时返回 null
     */
    public static Bitmap placeholder(Context context, int sizePx) {
        String key = "ph@" + sizePx;
        Bitmap cached = ICON_CACHE.get(key);
        if (cached != null) return cached;

        if (placeholderTexture == null) {
            try (InputStream in = context.getAssets().open("placeholder_block.png")) {
                placeholderTexture = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) {
            }
        }
        if (placeholderTexture == null) return null;

        Bitmap out = compose(placeholderTexture, placeholderTexture, placeholderTexture, sizePx);
        ICON_CACHE.put(key, out);
        return out;
    }

    private static String dirKey(File projectDir) {
        return projectDir == null ? "" : projectDir.getAbsolutePath();
    }

    /**
     * 半透明的侧面贴图垫上底面贴图。
     *
     * 判定条件：侧面大部分是透明的，而底面的贴图是实心的。
     * 玻璃这类整块半透明的方块底面也透明，不会误判；草方块则是 82% 透明 +
     * 实心泥土底，正好补成完整的侧面。
     */
    private static Bitmap underlay(Bitmap face, Bitmap base) {
        if (face == null || base == null) return face;
        if (face.getWidth() != base.getWidth() || face.getHeight() != base.getHeight()) return face;
        if (transparentRatio(face) <= 0.5f || transparentRatio(base) > 0.05f) return face;

        Bitmap out = Bitmap.createBitmap(face.getWidth(), face.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint();
        paint.setFilterBitmap(false);
        canvas.drawBitmap(base, 0, 0, paint);
        canvas.drawBitmap(face, 0, 0, paint);
        return out;
    }

    /** 完全透明的像素占比。方块贴图的 alpha 不是 0 就是 255，不用考虑半透明。 */
    private static float transparentRatio(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        int zero = 0;
        for (int p : px) {
            if ((p >>> 24) == 0) zero++;
        }
        return px.length == 0 ? 0f : (float) zero / px.length;
    }

    /** 取一个面的贴图，顺手按生物群系色染色。 */
    private static Bitmap faceBitmap(Context context, String path, File projectDir) {
        Bitmap src = BlockTextureLib.texture(context, path, projectDir);
        if (src == null) return null;
        boolean full = TINT_FULL.containsKey(path);
        boolean gray = TINT_GRAY.containsKey(path);
        if (!full && !gray) return src;

        int color = full ? TINT_FULL.get(path) : TINT_GRAY.get(path);
        String key = dirKey(projectDir) + "|" + path;
        Bitmap cached = TINTED.get(key);
        if (cached != null) return cached;
        Bitmap out = gray ? tintGrayOnly(src, color) : tintAll(src, color);
        TINTED.put(key, out);
        return out;
    }

    private static Bitmap tintAll(Bitmap src, int color) {
        return tint(src, color, false);
    }

    /**
     * 只给灰阶像素上色。
     *
     * 判断标准是 RGB 三通道极差很小——草地那一圈是纯灰阶，泥土是明显的棕色，
     * 两者在贴图里分得很开，不需要额外的蒙版图。
     */
    private static Bitmap tintGrayOnly(Bitmap src, int color) {
        return tint(src, color, true);
    }

    private static Bitmap tint(Bitmap src, int color, boolean grayscaleOnly) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int tr = (color >> 16) & 0xFF;
        int tg = (color >> 8) & 0xFF;
        int tb = color & 0xFF;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int a = p >>> 24;
            if (a == 0) continue;
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            if (grayscaleOnly) {
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                if (max - min > 10) continue;
            }
            r = r * tr / 255;
            g = g * tg / 255;
            b = b * tb / 255;
            px[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    /** 三个面叠成 32×32 的图标，再按需要放大。 */
    private static Bitmap compose(Bitmap up, Bitmap north, Bitmap east, int sizePx) {
        Bitmap out = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setFilterBitmap(false);
        paint.setDither(false);

        drawFace(canvas, up, TOP, 255, paint);
        drawFace(canvas, north, LEFT, SHADE_LEFT, paint);
        drawFace(canvas, east, RIGHT, SHADE_RIGHT, paint);

        if (sizePx != 32) {
            Bitmap scaled = Bitmap.createScaledBitmap(out, sizePx, sizePx, false);
            if (scaled != out) out.recycle();
            out = scaled;
        }
        return out;
    }

    /**
     * 画一个面。
     *
     * 压暗用 LightingColorFilter 一次画完：分两次 drawBitmap 会让
     * 半透明贴图（玻璃、水）叠加变暗两遍。它只乘 RGB、不动 alpha。
     */
    private static void drawFace(Canvas canvas, Bitmap src, float[] m, int shade, Paint paint) {
        Matrix matrix = new Matrix();
        matrix.setValues(new float[]{
                m[0], m[1], m[2],
                m[3], m[4], m[5],
                0f, 0f, 1f
        });

        if (shade != 255) {
            paint.setColorFilter(new LightingColorFilter((shade << 16) | (shade << 8) | shade, 0));
        } else {
            paint.setColorFilter(null);
        }
        canvas.drawBitmap(src, matrix, paint);
        paint.setColorFilter(null);
    }

    public static void clearCache() {
        ICON_CACHE.evictAll();
        TINTED.clear();
    }
}
