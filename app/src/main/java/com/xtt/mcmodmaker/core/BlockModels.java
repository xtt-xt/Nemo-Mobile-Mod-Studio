package com.xtt.mcmodmaker.core;

import com.xtt.mcmodmaker.util.FileUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 方块 → 模型 → 贴图 的查找。
 *
 * 方块定义在行为包里，模型和贴图在资源包里，两边靠 identifier 对上：
 *
 *   behavior_pack/netease_blocks/**&#47;*.json
 *       components."minecraft:geometry" = "geometry.showcase_glass"
 *   resource_pack/models/entity/*.geo.json
 *       minecraft:geometry[].description.identifier = "geometry.showcase_glass"
 *   resource_pack/blocks.json + textures/terrain_texture.json
 *       方块 id → 贴图键 → 实际 png 路径
 *
 * 网易的自定义模型放在同样的 models 目录里、走同一套骨骼格式，
 * 只是组件名是 netease:block_model / netease:model，这里几种写法都认。
 * 找不到模型就返回 null，调用方退回原来的六面立方体。
 */
public final class BlockModels {

    /** 一个方块用到的模型和贴图。模型为 null 表示这个方块没有自定义模型。 */
    public static final class Spec {
        public final BlockModel model;
        /** 所有面共用一张贴图时的路径。 */
        public final String texture;
        /** 每个面单独的贴图路径（blocks.json 里按面写过才有）。 */
        public final Map<String, String> faceTextures;

        Spec(BlockModel model, String texture, Map<String, String> faceTextures) {
            this.model = model;
            this.texture = texture;
            this.faceTextures = faceTextures;
        }

        /** 某个面该用哪张贴图。 */
        public String textureFor(String faceKey) {
            if (faceTextures != null) {
                String p = faceTextures.get(faceKey);
                if (p != null) return p;
            }
            return texture;
        }
    }

    /** 缓存：工程目录 + 方块 id → Spec（没有模型时存一个空壳，免得反复扫盘）。 */
    private static final Map<String, Spec> CACHE = new HashMap<>();

    private BlockModels() {
    }

    /** 清掉缓存（工程换过文件、切项目时调用）。 */
    public static synchronized void clearCache() {
        CACHE.clear();
    }

    /**
     * 找方块的模型与贴图。
     *
     * @return 没有自定义模型时返回 null
     */
    public static synchronized Spec spec(File projectDir, String blockId) {
        if (projectDir == null || blockId == null || blockId.isEmpty()) return null;
        String key = projectDir.getAbsolutePath() + "|" + blockId;
        if (CACHE.containsKey(key)) {
            Spec cached = CACHE.get(key);
            return cached == null || cached.model == null ? null : cached;
        }
        Spec found = null;
        try {
            found = lookup(projectDir, blockId);
        } catch (Exception ignored) {
        }
        CACHE.put(key, found);
        return found;
    }

    /** 这个方块有没有可渲染的模型。 */
    public static boolean hasModel(File projectDir, String blockId) {
        Spec s = spec(projectDir, blockId);
        return s != null && s.model != null;
    }

    private static Spec lookup(File projectDir, String blockId) {
        File[] roots = projectDir.listFiles();
        if (roots == null) return null;

        // 1. 在行为包里找这个方块的几何引用
        String geometryRef = null;
        String modelTexture = null;
        for (File pack : roots) {
            if (!pack.isDirectory() || !StudioConfig.isBehaviorPack(pack.getName())) continue;
            JSONObject block = findBlockJson(pack, blockId, 0);
            if (block == null) continue;
            JSONObject def = block.optJSONObject("minecraft:block");
            if (def == null) def = block.optJSONObject("minecraft:item");
            if (def == null) def = block;
            JSONObject comps = def.optJSONObject("components");
            if (comps == null) comps = def;

            String[] keys = {"minecraft:geometry", "netease:block_model", "netease:model",
                    "netease:geometry", "netease:block_geometry"};
            for (String k : keys) {
                Object raw = comps.opt(k);
                if (raw == null) continue;
                if (raw instanceof String) {
                    geometryRef = (String) raw;
                } else if (raw instanceof JSONObject) {
                    JSONObject o = (JSONObject) raw;
                    String ref = firstNonEmpty(o, "identifier", "model", "geometry", "path");
                    if (ref != null) geometryRef = ref;
                    String tex = firstNonEmpty(o, "texture", "textures");
                    if (tex != null) modelTexture = tex;
                }
                if (geometryRef != null) break;
            }
            if (geometryRef != null) break;
        }
        if (geometryRef == null) return null;

        // 2. 在资源包的 models 目录里按 identifier 找模型
        Map<String, String> terrain = new HashMap<>();
        JSONObject blocksJson = null;
        for (File pack : roots) {
            if (!pack.isDirectory() || !StudioConfig.isResourcePack(pack.getName())) continue;
            readTerrain(terrain, new File(pack, "textures/terrain_texture.json"));
            blocksJson = readJson(new File(pack, "blocks.json"));
        }

        BlockModel model = null;
        Object modelTextureFromGeo = null;
        for (File pack : roots) {
            if (!pack.isDirectory() || !StudioConfig.isResourcePack(pack.getName())) continue;
            File modelsDir = new File(pack, "models");
            if (!modelsDir.isDirectory()) continue;
            JSONObject geo = findGeometryFile(modelsDir, geometryRef, 0);
            if (geo == null) continue;
            try {
                model = BlockModel.find(geo, geometryRef);
            } catch (Exception e) {
                model = null;
            }
            if (model == null) continue;
            // 几何自带的贴图（实体模型那种写法）
            for (JSONObject entry : BlockModel.entries(geo)) {
                Object textures = entry.opt("textures");
                if (textures instanceof JSONObject) {
                    JSONObject t = (JSONObject) textures;
                    Iterator<String> it = t.keys();
                    if (it.hasNext()) modelTextureFromGeo = t.optString(it.next(), null);
                } else if (textures instanceof String) {
                    modelTextureFromGeo = textures;
                }
                if (modelTextureFromGeo != null) break;
            }
            break;
        }
        if (model == null) return null;

        // 3. 贴图：blocks.json 按面 > 组件里的 texture > 几何自带的 > 第一个面的路径
        String common = null;
        Map<String, String> perFace = new HashMap<>();
        JSONObject entry = blocksJson == null ? null : blocksJson.optJSONObject(blockId);
        if (entry != null) {
            Object tex = entry.opt("textures");
            if (tex instanceof String) {
                common = resolveTextureKey(String.valueOf(tex), terrain);
            } else if (tex instanceof JSONObject) {
                JSONObject o = (JSONObject) tex;
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    String path = resolveTextureKey(o.optString(k, ""), terrain);
                    if (path == null) continue;
                    if ("side".equals(k)) {
                        for (String f : new String[]{"north", "south", "east", "west"}) perFace.put(f, path);
                    } else {
                        perFace.put(k, path);
                    }
                }
            }
        }
        if (common == null && modelTexture != null) {
            common = resolveTextureKey(modelTexture, terrain);
        }
        if (common == null && modelTextureFromGeo != null) {
            common = resolveTextureKey(String.valueOf(modelTextureFromGeo), terrain);
        }
        if (common == null && !perFace.isEmpty()) {
            common = perFace.values().iterator().next();
        }
        return new Spec(model, common, perFace);
    }

    /** 递归找带指定 identifier 的 .geo.json。 */
    private static JSONObject findGeometryFile(File dir, String ref, int depth) {
        if (dir == null || depth > 6) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        // 名字能直接对上的先试，省得整个目录都解析一遍
        String wanted = ref.endsWith(".json") ? new File(ref).getName() : ref + ".geo.json";
        for (File f : files) {
            if (f.isFile() && f.getName().equalsIgnoreCase(wanted)) {
                JSONObject root = readJson(f);
                if (root != null && containsIdentifier(root, ref)) return root;
            }
        }
        for (File f : files) {
            if (f.isDirectory()) {
                JSONObject nested = findGeometryFile(f, ref, depth + 1);
                if (nested != null) return nested;
            } else if (f.getName().toLowerCase().endsWith(".json")) {
                JSONObject root = readJson(f);
                if (root != null && containsIdentifier(root, ref)) return root;
            }
        }
        return null;
    }

    private static boolean containsIdentifier(JSONObject root, String ref) {
        List<String> ids = BlockModel.identifiers(root);
        for (String id : ids) {
            if (id.equals(ref)) return true;
        }
        // 只有一个模型而作者命名不一致时也算命中
        return ids.size() == 1;
    }

    /** 在行为包里递归找 identifier 匹配的方块定义 json。 */
    private static JSONObject findBlockJson(File dir, String blockId, int depth) {
        if (dir == null || depth > 8) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                String n = f.getName();
                if (n.equals(GitRepo.GIT_DIR) || n.equals(".mcs")) continue;
                JSONObject nested = findBlockJson(f, blockId, depth + 1);
                if (nested != null) return nested;
                continue;
            }
            if (!f.getName().toLowerCase().endsWith(".json")) continue;
            JSONObject root = readJson(f);
            if (root == null) continue;
            JSONObject def = root.optJSONObject("minecraft:block");
            if (def == null) def = root.optJSONObject("minecraft:item");
            JSONObject desc = def == null ? null : def.optJSONObject("description");
            String id = desc == null ? "" : desc.optString("identifier", "");
            if (id.equals(blockId)) return root;
        }
        return null;
    }

    private static String firstNonEmpty(JSONObject o, String... keys) {
        for (String k : keys) {
            Object v = o.opt(k);
            if (v instanceof String && !((String) v).trim().isEmpty()) return (String) v;
        }
        return null;
    }

    // ==================== 贴图 ====================

    /** terrain 键 → 实际路径；已经是路径的（含 / 或 .png）直接用。 */
    private static String resolveTextureKey(String raw, Map<String, String> terrain) {
        if (raw == null) return null;
        String key = raw.trim();
        if (key.isEmpty()) return null;
        String byKey = terrain.get(key);
        if (byKey != null) key = byKey.trim();
        // 统一去掉 .png 后缀：取图那一层会自己补，带不带都能用，
        // 但缓存键要一致，否则同一张图会被当成两张
        if (key.toLowerCase().endsWith(".png")) key = key.substring(0, key.length() - 4);
        return key;
    }

    private static void readTerrain(Map<String, String> out, File f) {
        JSONObject root = readJson(f);
        if (root == null) return;
        JSONObject data = root.optJSONObject("texture_data");
        if (data == null) return;
        Iterator<String> it = data.keys();
        while (it.hasNext()) {
            String key = it.next();
            JSONObject entry = data.optJSONObject(key);
            if (entry == null) continue;
            Object t = entry.opt("textures");
            String path = firstPath(t);
            if (path != null) out.put(key, path);
        }
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

    private static JSONObject readJson(File f) {
        if (f == null || !f.isFile() || f.length() <= 0) return null;
        try {
            return new JSONObject(FileUtils.readText(f));
        } catch (Exception e) {
            return null;
        }
    }
}
