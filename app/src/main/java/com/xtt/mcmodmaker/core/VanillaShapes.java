package com.xtt.mcmodmaker.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版方块的真实形状。
 *
 * 基岩版资源包里没有原版方块的模型文件（blocks.json 里带 geometry 的方块是 0 个，
 * 台阶、楼梯、箱子这些形状是引擎里硬编码的），所以形状来自 Java 版的官方模型：
 * 逐方块摊平成「显式四边形」存进 assets/block_shapes.json，
 * 每个面带着从原贴图上切出来的 UV 和朝外的法线，这里读出来直接交给 ModelRenderer 画。
 *
 * 烘形状的工具在仓库 tools/blockshapes 下，改完重新跑一遍即可。
 */
public final class VanillaShapes {

    /** 六个面的顺序，和 BlockModel.FACES 一致。 */
    private static final String[] FACES = BlockModel.FACES;

    /** 每个方向的法线，顺序同 FACES。 */
    private static final float[][] NORMALS = {
            {0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}
    };

    private static JSONObject asset;
    private static JSONArray templates;
    private static JSONObject index;
    private static boolean tried;

    /** 一个面在 block_shapes.json 里的字段：方向、贴图槽、4 组 uv、4 个顶点。 */
    private static final int ROW_UV = 2;
    private static final int ROW_VERTS = 10;

    /** 已经拼好的形状，按方块 id 缓存。 */
    private static final Map<String, BlockModel> CACHE = new HashMap<>();

    private VanillaShapes() {
    }

    /**
     * 这个方块有没有内置形状。
     *
     * @param blockId 完整 id，如 minecraft:oak_stairs
     */
    public static boolean canShape(Context context, String blockId) {
        return templateIndex(context, blockId) >= 0;
    }

    /**
     * 生成形状，没有就返回 null（调用方会退回六面立方体）。
     *
     * @param sixFaces 六面贴图路径，顺序 up/down/north/south/east/west
     */
    public static BlockModel build(Context context, String blockId, String[] sixFaces) {
        if (blockId == null || sixFaces == null || sixFaces.length < 6) return null;
        int idx = templateIndex(context, blockId);
        if (idx < 0) return null;
        BlockModel cached = CACHE.get(blockId);
        if (cached != null) return cached;
        try {
            JSONArray quads = templates.getJSONArray(idx);
            List<BlockModel.Face> faces = new ArrayList<>(quads.length());
            for (int i = 0; i < quads.length(); i++) {
                JSONArray row = quads.getJSONArray(i);
                int dir = row.getInt(0);
                int slot = row.getInt(1);
                if (slot < 0 || slot >= sixFaces.length) slot = dir;
                BlockModel.Face f = new BlockModel.Face(FACES[dir]);
                for (int k = 0; k < 4; k++) {
                    f.uv[k * 2] = (float) row.getDouble(2 + k * 2);
                    f.uv[k * 2 + 1] = (float) row.getDouble(3 + k * 2);
                    f.verts[k][0] = (float) row.getDouble(10 + k * 3);
                    f.verts[k][1] = (float) row.getDouble(11 + k * 3);
                    f.verts[k][2] = (float) row.getDouble(12 + k * 3);
                }
                crossNormal(f);
                f.texture = sixFaces[slot];
                faces.add(f);
            }
            BlockModel model = BlockModel.of(blockId, 16f, 16f, faces);
            if (model != null) CACHE.put(blockId, model);
            return model;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 法线直接用三个顶点叉乘算。
     *
     * 不能拿「方向下标对应的法线」去校方向：方块状态转过之后（比如铁砧 facing=north
     * 是 180°），模型里的 north 面已经跑到南边去了，按下标校就会把法线翻到朝里，
     * 整圈面被背面剔除，看起来像缺了一半。烘焙时绕序已经统一成朝外，叉乘就够了，
     * 斜着转过的面（十字植物）也自然是对的。
     */
    private static void crossNormal(BlockModel.Face f) {
        float[] a = f.verts[0], b = f.verts[1], c = f.verts[2];
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-5f) {
            f.normal[0] = 0;
            f.normal[1] = 1;
            f.normal[2] = 0;
            return;
        }
        f.normal[0] = nx / len;
        f.normal[1] = ny / len;
        f.normal[2] = nz / len;
    }

    private static int templateIndex(Context context, String blockId) {
        load(context);
        if (index == null || templates == null || blockId == null) return -1;
        int i = index.optInt(blockId, -1);
        if (i < 0) {
            // 也认不带命名空间的写法
            int c = blockId.indexOf(':');
            if (c >= 0) i = index.optInt(blockId.substring(c + 1), -1);
            else i = index.optInt("minecraft:" + blockId, -1);
        }
        return i < templates.length() ? i : -1;
    }

    private static synchronized void load(Context context) {
        if (tried) return;
        tried = true;
        if (context == null) return;
        InputStream in = null;
        try {
            in = context.getAssets().open("block_shapes.json");
            ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 18);
            byte[] buf = new byte[1 << 15];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            asset = new JSONObject(bo.toString("UTF-8"));
            templates = asset.getJSONArray("t");
            index = asset.getJSONObject("b");
        } catch (Exception e) {
            asset = null;
            templates = null;
            index = null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    /** 贴图库清缓存时一起清，不同工程的原版贴图可能被换掉。 */
    public static synchronized void clearCache() {
        CACHE.clear();
    }
}
