package com.xtt.mcmodmaker.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import com.xtt.mcmodmaker.util.FileUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 方块贴图的取用中心。
 *
 * 两件事：
 *  1. 内置的「方块 id → 六个面的贴图路径」映射表（assets/block_faces.json），
 *     由 tools/gen_block_icons.py 从官方 blocks.json + terrain_texture.json 预解析而来，
 *     运行时不用再解析那两个几百 KB 的 json。
 *  2. 按路径取贴图：先找当前工程 resource_pack 里的同名文件，
 *     找不到再用内置的原版贴图。
 *
 * 为什么必须内置原版贴图：工程里的 terrain_texture.json 只写路径
 * （textures/blocks/glass.png），文件本身由游戏自带，工程不会复制一份。
 */
public final class BlockTextureLib {

    public static final String[] FACES = {"up", "down", "north", "south", "east", "west"};

    private static List<String> paths;
    private static Map<String, int[]> faces;

    /** 贴图缓存，按「工程目录 + 相对路径」做键。 */
    private static final LruCache<String, Bitmap> TEX_CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    /** 找不到贴图时也记一笔，免得反复去磁盘戳。 */
    private static final java.util.Set<String> MISSING = new java.util.HashSet<>();

    /** 当前工程里 blocks.json + terrain_texture.json 解析出来的方块六面，键是完整 id。 */
    private static final Map<String, String[]> PROJECT_FACES = new HashMap<>();
    private static String projectFacesDir;

    private BlockTextureLib() {
    }

    private static synchronized void ensureLoaded(Context context) {
        if (faces != null) return;
        List<String> p = new ArrayList<>();
        Map<String, int[]> m = new HashMap<>();
        try (InputStream in = context.getAssets().open("block_faces.json")) {
            String json = readAll(in);
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("paths");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) p.add(arr.optString(i));
            }
            JSONObject blocks = root.optJSONObject("blocks");
            if (blocks != null) {
                java.util.Iterator<String> it = blocks.keys();
                while (it.hasNext()) {
                    String id = it.next();
                    JSONArray six = blocks.optJSONArray(id);
                    if (six == null || six.length() < 6) continue;
                    int[] idx = new int[6];
                    for (int i = 0; i < 6; i++) idx[i] = six.optInt(i, -1);
                    m.put(id, idx);
                }
            }
        } catch (Exception ignored) {
        }
        paths = p;
        faces = m;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    /** 这个方块能不能渲染（有没有六面贴图数据）。 */
    public static boolean canRender(Context context, String blockId) {
        ensureLoaded(context);
        return blockId != null && faces.containsKey(blockId);
    }

    /** 取六面路径，顺序同 {@link #FACES}；不支持则返回 null。 */
    public static String[] resolveFaces(Context context, String blockId) {
        return resolveFaces(context, blockId, null);
    }

    /**
     * 取六面路径，先查工程里自己定义的方块，再查内置的原版方块。
     *
     * 自定义方块的 blocks.json / terrain_texture.json 只存在于工程里，
     * 内置表里没有；而它们引用的贴图路径往往就是原版的
     * （例如防盗玻璃指向 textures/blocks/glass.png），
     * 取图时会自动回退到内置的那份原版贴图。
     */
    public static String[] resolveFaces(Context context, String blockId, File projectDir) {
        if (blockId == null) return null;
        String[] fromProject = projectFaces(context, projectDir).get(blockId);
        if (fromProject != null) return fromProject;

        ensureLoaded(context);
        int[] idx = faces.get(blockId);
        if (idx == null) return null;
        String[] out = new String[6];
        for (int i = 0; i < 6; i++) {
            if (idx[i] < 0 || idx[i] >= paths.size()) return null;
            out[i] = paths.get(idx[i]);
        }
        return out;
    }

    /** 解析并缓存工程里的方块定义。工程切换时按目录重新解析一次。 */
    private static synchronized Map<String, String[]> projectFaces(Context context, File projectDir) {
        if (projectDir == null) return PROJECT_FACES;
        String dir = projectDir.getAbsolutePath();
        if (dir.equals(projectFacesDir)) return PROJECT_FACES;
        projectFacesDir = dir;
        PROJECT_FACES.clear();
        MISSING.clear();

        File[] roots = projectDir.listFiles();
        if (roots == null) return PROJECT_FACES;
        for (File pack : roots) {
            if (!pack.isDirectory()) continue;
            if (!StudioConfig.isResourcePack(pack.getName())) continue;
            Map<String, Object> terrain = readTerrain(new File(pack, "textures/terrain_texture.json"));
            if (terrain.isEmpty()) continue;
            readBlocksJson(new File(pack, "blocks.json"), terrain);
        }
        return PROJECT_FACES;
    }

    /** 读工程里的 terrain_texture.json，取出 键 → 贴图路径。 */
    private static Map<String, Object> readTerrain(File f) {
        Map<String, Object> out = new HashMap<>();
        if (!f.isFile()) return out;
        try {
            JSONObject root = new JSONObject(FileUtils.readText(f));
            JSONObject data = root.optJSONObject("texture_data");
            if (data == null) return out;
            java.util.Iterator<String> it = data.keys();
            while (it.hasNext()) {
                String key = it.next();
                JSONObject entry = data.optJSONObject(key);
                if (entry == null) continue;
                Object t = entry.opt("textures");
                if (t == null) continue;
                out.put(key, t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 读工程里的 blocks.json，把每个方块的 textures 展开成六面。 */
    private static void readBlocksJson(File f, Map<String, Object> terrain) {
        if (!f.isFile()) return;
        try {
            JSONObject root = new JSONObject(FileUtils.readText(f));
            java.util.Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String key = it.next();
                if (key.startsWith("format_version")) continue;
                JSONObject def = root.optJSONObject(key);
                if (def == null) continue;
                Object tex = def.opt("textures");
                String[] six = expandFaces(tex, terrain);
                if (six != null) PROJECT_FACES.put(key, six);
            }
        } catch (Exception ignored) {
        }
    }

    /** 把 blocks.json 的一项 textures 展开成六个面的实际路径。 */
    private static String[] expandFaces(Object textures, Map<String, Object> terrain) {
        Map<String, Object> byFace = new HashMap<>();
        if (textures instanceof String) {
            for (String face : FACES) byFace.put(face, textures);
        } else if (textures instanceof JSONObject) {
            JSONObject obj = (JSONObject) textures;
            java.util.Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String k = it.next();
                for (String face : FACES) {
                    if (face.equals(k)) byFace.put(face, obj.opt(k));
                }
            }
            // side = 四个侧面共用，原版 blocks.json 里很常见
            Object side = obj.opt("side");
            if (side != null) {
                for (String face : new String[]{"north", "south", "east", "west"}) {
                    if (!byFace.containsKey(face)) byFace.put(face, side);
                }
            }
        } else {
            return null;
        }
        String[] out = new String[6];
        for (int i = 0; i < 6; i++) {
            Object raw = byFace.get(FACES[i]);
            String path = resolveTerrainKey(raw, terrain);
            if (path == null) return null;
            out[i] = path;
        }
        return out;
    }

    /** terrain 键 → 实际贴图路径；数组取第一个，对象取 path。 */
    private static String resolveTerrainKey(Object raw, Map<String, Object> terrain) {
        if (raw == null) return null;
        if (raw instanceof JSONObject) {
            JSONObject o = (JSONObject) raw;
            String k = o.optString("path", o.optString("texture", null));
            if (k == null) return null;
            Object v = terrain.get(k);
            return v == null ? null : firstPath(v);
        }
        String key = String.valueOf(raw);
        Object v = terrain.get(key);
        return v == null ? null : firstPath(v);
    }

    private static String firstPath(Object v) {
        if (v instanceof String) return (String) v;
        if (v instanceof JSONArray) {
            JSONArray arr = (JSONArray) v;
            if (arr.length() == 0) return null;
            Object first = arr.opt(0);
            if (first instanceof String) return (String) first;
            if (first instanceof JSONObject) return ((JSONObject) first).optString("path", null);
        }
        if (v instanceof JSONObject) return ((JSONObject) v).optString("path", null);
        return null;
    }

    /**
     * 按路径取贴图。
     *
     * @param path     形如 textures/blocks/stone
     * @param projectDir 当前工程根目录，可为 null
     */
    public static Bitmap texture(Context context, String path, File projectDir) {
        if (path == null) return null;
        String key = (projectDir == null ? "" : projectDir.getAbsolutePath()) + "|" + path;
        Bitmap cached = TEX_CACHE.get(key);
        if (cached != null) return cached;
        if (MISSING.contains(key)) return null;

        Bitmap bmp = loadFromProject(path, projectDir);
        if (bmp == null) bmp = loadFromAssets(context, path);
        if (bmp == null) {
            MISSING.add(key);
            return null;
        }
        TEX_CACHE.put(key, bmp);
        return bmp;
    }

    /** 工程里自动给路径补 .png 后缀，也接受已经带后缀的写法。 */
    private static Bitmap loadFromProject(String path, File projectDir) {
        if (projectDir == null) return null;
        File[] roots = projectDir.listFiles();
        if (roots == null) return null;
        for (File pack : roots) {
            if (!pack.isDirectory()) continue;
            String pn = pack.getName().toLowerCase();
            if (!StudioConfig.isResourcePack(pn)) continue;
            File f = new File(pack, path.endsWith(".png") ? path : path + ".png");
            if (!f.isFile()) continue;
            try (FileInputStream in = new FileInputStream(f)) {
                return BitmapFactory.decodeStream(in);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Bitmap loadFromAssets(Context context, String path) {
        String rel = path.endsWith(".png") ? path : path + ".png";
        // 内置贴图放在 assets/block_tex/ 下，目录结构与资源包一致
        String assetPath = "block_tex/" + rel;
        try (InputStream in = context.getAssets().open(assetPath)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 内置贴图总数，关于页显示用。 */
    public static int blockCount(Context context) {
        ensureLoaded(context);
        return faces.size();
    }

    public static void clearCache() {
        TEX_CACHE.evictAll();
        MISSING.clear();
    }
}
