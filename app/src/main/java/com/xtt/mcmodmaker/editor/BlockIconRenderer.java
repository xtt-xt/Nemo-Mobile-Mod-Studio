package com.xtt.mcmodmaker.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.LruCache;

import com.xtt.mcmodmaker.core.BlockTextureLib;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 把方块的六面贴图渲染成一张等轴测图标，效果和创造模式物品栏里的方块一致。
 *
 * 画法：32×32 的画布上叠三个平行四边形，各自用仿射矩阵把 16×16 的贴图斜切过去，
 * 再用亮度滤镜压暗侧面（顶 100%、左 80%、右 60%，与游戏内的明暗关系一致）。
 * 不用逐像素计算，Canvas 的 drawBitmap 会走 native 路径。
 *
 * 好处：自定义方块也能出图——只要工程里 blocks.json / terrain_texture.json
 * 指向的贴图存在（原版路径会自动用内置的那份）。
 */
public final class BlockIconRenderer {

    /** 单个面的仿射矩阵，{a,b,c,d,e,f} 表示 x'=ax+by+c, y'=dx+ey+f，坐标系为 32×32。 */
    private static final float[] TOP = {1f, -1f, 16f, 0.5f, 0.5f, 0f};
    private static final float[] LEFT = {1f, 0f, 0f, 0.5f, 1f, 8f};
    private static final float[] RIGHT = {1f, 0f, 16f, -0.5f, 1f, 16f};

    /** 侧面压暗系数，与游戏的方块渲染一致。 */
    private static final int SHADE_LEFT = 204;   // 80%
    private static final int SHADE_RIGHT = 153;  // 60%

    /**
     * 灰阶贴图要乘上的染色。
     *
     * 原版草、树叶这类贴图本身是灰阶的，靠生物群系颜色染色；
     * 这里固定用平原的色调，颜色值取自官方 biomes 配置。
     */
    private static final Map<String, Integer> TINT = new HashMap<>();

    static {
        int grass = 0xFF91BD59;   // 平原草地
        int foliage = 0xFF6BA941; // 树叶
        TINT.put("textures/blocks/grass_top", grass);
        TINT.put("textures/blocks/grass_side_overlay", grass);
        TINT.put("textures/blocks/grass_carried_top", grass);
        TINT.put("textures/blocks/foliage_oak", foliage);
        TINT.put("textures/blocks/foliage_birch", 0xFF80A755);
        TINT.put("textures/blocks/foliage_spruce", 0xFF619961);
        TINT.put("textures/blocks/foliage_jungle", 0xFF59C93C);
        TINT.put("textures/blocks/foliage_acacia", 0xFF6BA941);
        TINT.put("textures/blocks/foliage_big_oak", foliage);
    }

    private static final LruCache<String, Bitmap> ICON_CACHE =
            new LruCache<String, Bitmap>(4 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private BlockIconRenderer() {
    }

    /**
     * 渲染方块图标。
     *
     * @param blockId    如 minecraft:oak_planks
     * @param projectDir 当前工程根目录（自定义方块从里面取贴图），可为 null
     * @param sizePx     输出边长，建议 32 / 48 / 64
     * @return 没有贴图数据或贴图缺失时返回 null
     */
    public static Bitmap render(Context context, String blockId, File projectDir, int sizePx) {
        if (blockId == null) return null;
        String key = blockId + "@" + sizePx + "@"
                + (projectDir == null ? "" : projectDir.getAbsolutePath());
        Bitmap cached = ICON_CACHE.get(key);
        if (cached != null) return cached;

        String[] facePaths = BlockTextureLib.resolveFaces(context, blockId, projectDir);
        if (facePaths == null) return null;

        Bitmap up = BlockTextureLib.texture(context, facePaths[0], projectDir);
        Bitmap north = BlockTextureLib.texture(context, facePaths[2], projectDir);
        Bitmap east = BlockTextureLib.texture(context, facePaths[4], projectDir);
        // 顶面是必需的，缺了就没法画；侧面缺就退回顶面的贴图
        if (up == null) return null;
        if (north == null) north = up;
        if (east == null) east = up;

        Bitmap out = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setFilterBitmap(false);
        paint.setDither(false);

        drawFace(canvas, up, TOP, 255, facePaths[0], paint);
        drawFace(canvas, north, LEFT, SHADE_LEFT, facePaths[2], paint);
        drawFace(canvas, east, RIGHT, SHADE_RIGHT, facePaths[4], paint);

        if (sizePx != 32) {
            Bitmap scaled = Bitmap.createScaledBitmap(out, sizePx, sizePx, false);
            if (scaled != out) out.recycle();
            out = scaled;
        }
        ICON_CACHE.put(key, out);
        return out;
    }

    /**
     * 画一个面。
     *
     * 压暗和染色合成一个 LightingColorFilter 一次画完：
     * 分两次 drawBitmap 会让半透明贴图（玻璃、水）叠加变暗两遍。
     * LightingColorFilter 只乘 RGB、不动 alpha，正好适合这里。
     */
    private static void drawFace(Canvas canvas, Bitmap src, float[] m, int shade,
                                 String path, Paint paint) {
        Matrix matrix = new Matrix();
        matrix.setValues(new float[]{
                m[0], m[1], m[2],
                m[3], m[4], m[5],
                0f, 0f, 1f
        });

        float factor = shade / 255f;
        int mulR = 255, mulG = 255, mulB = 255;
        Integer tint = TINT.get(path);
        if (tint != null) {
            // 灰阶贴图（草、树叶）先按生物群系色乘一遍，再一起压暗
            mulR = (tint >> 16) & 0xFF;
            mulG = (tint >> 8) & 0xFF;
            mulB = tint & 0xFF;
        }
        int r = Math.round(mulR * factor);
        int g = Math.round(mulG * factor);
        int b = Math.round(mulB * factor);

        if (r != 255 || g != 255 || b != 255) {
            paint.setColorFilter(new LightingColorFilter((r << 16) | (g << 8) | b, 0));
        } else {
            paint.setColorFilter(null);
        }
        canvas.drawBitmap(src, matrix, paint);
        paint.setColorFilter(null);
    }

    public static void clearCache() {
        ICON_CACHE.evictAll();
    }
}
