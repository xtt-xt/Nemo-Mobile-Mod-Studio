package com.xtt.mcmodmaker.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个方块模型的几何数据（基岩版 *.geo.json / 网易自定义模型）。
 *
 * 只做解析和坐标系变换，不碰 Android，方便在电脑上单独验证。
 * 坐标单位是 1/16 方块（和 .geo.json 一致），y 轴朝上。
 *
 * 支持的范围：
 *  - format_version 1.8 / 1.12 / 1.16 / 1.21 的 minecraft:geometry
 *  - bones 骨骼层级（parent）、每根骨骼的 pivot / rotation
 *  - cubes 的 origin / size / inflate / pivot / rotation / mirror
 *  - 每面单独指定 uv，也支持盒式展开（按 texture_width/height 算）
 */
public final class BlockModel {

    /** 六个面的顺序，和 BlockTextureLib.FACES 一致。 */
    public static final String[] FACES = {"up", "down", "north", "south", "east", "west"};

    /** 一个面：四个顶点（模型空间）+ 四个顶点的贴图坐标（像素）。 */
    public static final class Face {
        public final String key;
        /** 4 个顶点，每个是 {x,y,z}；第 0、1 是上边，2、3 是下边。 */
        public final float[][] verts = new float[4][3];
        /** 4 个顶点的 uv，u0,v0,u1,v1,u2,v2,u3,v3，单位是贴图像素。 */
        public final float[] uv = new float[8];
        /** 变换后的法线，用来做背面剔除。 */
        public final float[] normal = new float[3];

        Face(String key) {
            this.key = key;
        }
    }

    public final String identifier;
    public final float textureWidth;
    public final float textureHeight;
    public final List<Face> faces;

    private BlockModel(String identifier, float textureWidth, float textureHeight, List<Face> faces) {
        this.identifier = identifier;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.faces = faces;
    }

    /**
     * 解析 minecraft:geometry 数组里的一项。
     *
     * @param entry      不含外层 minecraft:geometry 键
     * @param identifier 描述里没写 id 时用它
     * @return 一个方块都没有时返回 null
     */
    public static BlockModel parse(JSONObject entry, String identifier) throws Exception {
        if (entry == null) return null;
        JSONObject desc = entry.optJSONObject("description");
        String id = identifier;
        float texW = 64f;
        float texH = 64f;
        if (desc != null) {
            if (desc.has("identifier")) id = desc.optString("identifier", identifier);
            texW = (float) desc.optDouble("texture_width", 64);
            texH = (float) desc.optDouble("texture_height", 64);
        }
        if (texW <= 0) texW = 64;
        if (texH <= 0) texH = 64;

        List<JSONObject> bones = new ArrayList<>();
        JSONArray arr = entry.optJSONArray("bones");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject b = arr.optJSONObject(i);
                if (b != null) bones.add(b);
            }
        }
        Map<String, JSONObject> byName = new HashMap<>();
        for (JSONObject b : bones) {
            String n = b.optString("name", "");
            if (!n.isEmpty()) byName.put(n, b);
        }

        List<Face> out = new ArrayList<>();
        for (JSONObject bone : bones) {
            float[] m = boneMatrix(bone, byName, 0);
            JSONArray cubes = bone.optJSONArray("cubes");
            if (cubes == null) continue;
            for (int i = 0; i < cubes.length(); i++) {
                JSONObject cube = cubes.optJSONObject(i);
                if (cube == null) continue;
                addCubeFaces(cube, m, out);
            }
        }
        if (out.isEmpty()) return null;
        return new BlockModel(id == null ? "" : id, texW, texH, out);
    }

    /** 按 identifier 找 geometry；只有一个模型而 id 对不上时退回那一个。 */
    public static BlockModel find(JSONObject root, String geometryId) throws Exception {
        if (root == null || geometryId == null) return null;
        JSONArray arr = root.optJSONArray("minecraft:geometry");
        if (arr == null) {
            JSONObject single = root.optJSONObject("minecraft:geometry");
            if (single == null) return null;
            arr = new JSONArray();
            arr.put(single);
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject entry = arr.optJSONObject(i);
            if (entry == null) continue;
            JSONObject desc = entry.optJSONObject("description");
            String id = desc == null ? "" : desc.optString("identifier", "");
            if (id.equals(geometryId)) return parse(entry, geometryId);
        }
        if (arr.length() == 1) {
            JSONObject only = arr.optJSONObject(0);
            if (only != null) return parse(only, geometryId);
        }
        return null;
    }

    /** 文件里出现的所有 geometry identifier。 */
    public static List<String> identifiers(JSONObject root) {
        List<String> out = new ArrayList<>();
        for (JSONObject entry : entries(root)) {
            JSONObject desc = entry.optJSONObject("description");
            String id = desc == null ? "" : desc.optString("identifier", "");
            if (!id.isEmpty()) out.add(id);
        }
        return out;
    }

    /** 所有 geometry 项；没有时返回空列表。 */
    public static List<JSONObject> entries(JSONObject root) {
        List<JSONObject> out = new ArrayList<>();
        if (root == null) return out;
        JSONArray arr = root.optJSONArray("minecraft:geometry");
        if (arr == null) {
            JSONObject single = root.optJSONObject("minecraft:geometry");
            if (single != null) out.add(single);
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject entry = arr.optJSONObject(i);
            if (entry != null) out.add(entry);
        }
        return out;
    }

    // ==================== 骨骼变换 ====================

    /** 骨骼的模型空间矩阵：父骨骼 × 自身绕 pivot 的旋转。 */
    private static float[] boneMatrix(JSONObject bone, Map<String, JSONObject> byName, int depth) {
        if (depth > 16) return identity();
        String parent = bone.optString("parent", "");
        float[] base = identity();
        if (!parent.isEmpty()) {
            JSONObject p = byName.get(parent);
            if (p != null) base = boneMatrix(p, byName, depth + 1);
        }
        float[] pivot = vec3(bone.optJSONArray("pivot"), 0f, 0f, 0f);
        float[] rot = vec3(bone.optJSONArray("rotation"), 0f, 0f, 0f);
        return mul(base, rotateAroundPivot(pivot, rot));
    }

    /** 绕 pivot 按 Z→Y→X 的次序旋转。 */
    private static float[] rotateAroundPivot(float[] pivot, float[] deg) {
        float[] m = identity();
        if (deg[2] != 0) m = mul(m, rotZ(deg[2]));
        if (deg[1] != 0) m = mul(m, rotY(deg[1]));
        if (deg[0] != 0) m = mul(m, rotX(deg[0]));
        if (pivot[0] == 0 && pivot[1] == 0 && pivot[2] == 0) return m;
        return mul(mul(translate(pivot[0], pivot[1], pivot[2]), m),
                translate(-pivot[0], -pivot[1], -pivot[2]));
    }

    // ==================== 立方体 ====================

    private static void addCubeFaces(JSONObject cube, float[] bone, List<Face> out) {
        float[] origin = vec3(cube.optJSONArray("origin"), 0f, 0f, 0f);
        float[] size = vec3(cube.optJSONArray("size"), 0f, 0f, 0f);
        float inflate = (float) cube.optDouble("inflate", 0);
        boolean mirror = cube.optBoolean("mirror", false);

        float x0 = origin[0] - inflate;
        float y0 = origin[1] - inflate;
        float z0 = origin[2] - inflate;
        float x1 = origin[0] + size[0] + inflate;
        float y1 = origin[1] + size[1] + inflate;
        float z1 = origin[2] + size[2] + inflate;

        float[] pivot = vec3(cube.optJSONArray("pivot"), origin[0], origin[1], origin[2]);
        float[] rot = vec3(cube.optJSONArray("rotation"), 0f, 0f, 0f);
        float[] m = mul(bone, rotateAroundPivot(pivot, rot));

        JSONObject perFace = cube.optJSONObject("uv");
        JSONArray boxUv = cube.optJSONArray("uv");

        for (String key : FACES) {
            float[] rect = perFace == null ? null : faceRect(perFace, key, size);
            if (rect == null && boxUv != null) rect = boxRect(boxUv, key, size);
            if (rect == null) rect = new float[]{0, 0, faceWidth(key, size), faceHeight(key, size)};
            if (mirror) {
                // 镜像：u 方向反过来，贴图才不会左右颠倒
                rect[0] = rect[0] + rect[2];
                rect[2] = -rect[2];
            }
            Face f = buildFace(key, x0, y0, z0, x1, y1, z1, rect);
            transformFace(f, m);
            out.add(f);
        }
    }

    /** 按面构造四个顶点和 uv。 */
    private static Face buildFace(String key, float x0, float y0, float z0,
                                  float x1, float y1, float z1, float[] r) {
        Face f = new Face(key);
        float u = r[0];
        float v = r[1];
        float w = r[2];
        float h = r[3];
        switch (key) {
            case "north":
                set(f, 0, x0, y1, z0, u, v);
                set(f, 1, x1, y1, z0, u + w, v);
                set(f, 2, x1, y0, z0, u + w, v + h);
                set(f, 3, x0, y0, z0, u, v + h);
                break;
            case "south":
                set(f, 0, x1, y1, z1, u, v);
                set(f, 1, x0, y1, z1, u + w, v);
                set(f, 2, x0, y0, z1, u + w, v + h);
                set(f, 3, x1, y0, z1, u, v + h);
                break;
            case "east":
                set(f, 0, x1, y1, z1, u, v);
                set(f, 1, x1, y1, z0, u + w, v);
                set(f, 2, x1, y0, z0, u + w, v + h);
                set(f, 3, x1, y0, z1, u, v + h);
                break;
            case "west":
                set(f, 0, x0, y1, z0, u, v);
                set(f, 1, x0, y1, z1, u + w, v);
                set(f, 2, x0, y0, z1, u + w, v + h);
                set(f, 3, x0, y0, z0, u, v + h);
                break;
            case "up":
                set(f, 0, x0, y1, z0, u, v);
                set(f, 1, x1, y1, z0, u + w, v);
                set(f, 2, x1, y1, z1, u + w, v + h);
                set(f, 3, x0, y1, z1, u, v + h);
                break;
            default:
                set(f, 0, x0, y0, z1, u, v);
                set(f, 1, x1, y0, z1, u + w, v);
                set(f, 2, x1, y0, z0, u + w, v + h);
                set(f, 3, x0, y0, z0, u, v + h);
                break;
        }
        setLocalNormal(f);
        return f;
    }

    /** 每个面在本地方向上的外法线，变换时跟着骨骼一起转。 */
    private static void setLocalNormal(Face f) {
        switch (f.key) {
            case "north": f.normal[2] = -1f; break;
            case "south": f.normal[2] = 1f; break;
            case "east":  f.normal[0] = 1f; break;
            case "west":  f.normal[0] = -1f; break;
            case "up":    f.normal[1] = 1f; break;
            default:      f.normal[1] = -1f; break;
        }
    }

    private static void set(Face f, int i, float x, float y, float z, float u, float v) {
        f.verts[i][0] = x;
        f.verts[i][1] = y;
        f.verts[i][2] = z;
        f.uv[i * 2] = u;
        f.uv[i * 2 + 1] = v;
    }

    /** 用骨骼矩阵变换顶点和法线。 */
    private static void transformFace(Face f, float[] m) {
        for (int i = 0; i < 4; i++) {
            float[] p = apply(m, f.verts[i][0], f.verts[i][1], f.verts[i][2]);
            f.verts[i][0] = p[0];
            f.verts[i][1] = p[1];
            f.verts[i][2] = p[2];
        }
        // 法线用矩阵的旋转部分变换。不拿顶点叉积算：
        // 各面的顶点绕序是照着 UV 排的，方向不统一，叉积会算出朝里的法线。
        float nx = f.normal[0];
        float ny = f.normal[1];
        float nz = f.normal[2];
        float tx = m[0] * nx + m[1] * ny + m[2] * nz;
        float ty = m[4] * nx + m[5] * ny + m[6] * nz;
        float tz = m[8] * nx + m[9] * ny + m[10] * nz;
        float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len > 0.00001f) {
            tx /= len;
            ty /= len;
            tz /= len;
        }
        f.normal[0] = tx;
        f.normal[1] = ty;
        f.normal[2] = tz;
    }

    // ==================== UV ====================

    /**
     * 单独指定的 uv，两种写法都认：
     *   "uv": {"north": [0, 0]}                          尺寸由面大小决定
     *   "uv": {"north": {"uv": [0,0], "uv_size": [8,8]}}
     */
    private static float[] faceRect(JSONObject uv, String key, float[] size) {
        float w = faceWidth(key, size);
        float h = faceHeight(key, size);
        Object raw = uv.opt(key);
        if (raw instanceof JSONArray) {
            JSONArray a = (JSONArray) raw;
            return new float[]{(float) a.optDouble(0, 0), (float) a.optDouble(1, 0), w, h};
        }
        if (raw instanceof JSONObject) {
            JSONObject o = (JSONObject) raw;
            JSONArray a = o.optJSONArray("uv");
            JSONArray s = o.optJSONArray("uv_size");
            float u = a == null ? 0 : (float) a.optDouble(0, 0);
            float v = a == null ? 0 : (float) a.optDouble(1, 0);
            float ww = s == null ? w : (float) s.optDouble(0, w);
            float hh = s == null ? h : (float) s.optDouble(1, h);
            return new float[]{u, v, ww, hh};
        }
        return null;
    }

    /**
     * 盒式展开。
     *
     * 一块 w×h×d 的立方体展开后占 2(w+d) × (d+h)：
     *   上一行：up 在 (d,0)、down 在 (d+w,0)
     *   下一行：east(0,d)、north(d,d)、west(d+w,d)、south(2d+w,d)
     */
    private static float[] boxRect(JSONArray uv, String key, float[] size) {
        float u0 = (float) uv.optDouble(0, 0);
        float v0 = (float) uv.optDouble(1, 0);
        float w = size[0];
        float h = size[1];
        float d = size[2];
        switch (key) {
            case "up":    return new float[]{u0 + d, v0, w, d};
            case "down":  return new float[]{u0 + d + w, v0, w, d};
            case "east":  return new float[]{u0, v0 + d, d, h};
            case "north": return new float[]{u0 + d, v0 + d, w, h};
            case "west":  return new float[]{u0 + d + w, v0 + d, d, h};
            default:      return new float[]{u0 + d + w + d, v0 + d, w, h};
        }
    }

    private static float faceWidth(String key, float[] size) {
        if ("east".equals(key) || "west".equals(key)) return size[2];
        return size[0];
    }

    private static float faceHeight(String key, float[] size) {
        if ("up".equals(key) || "down".equals(key)) return size[2];
        return size[1];
    }

    // ==================== 矩阵 ====================

    private static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static float[] translate(float x, float y, float z) {
        float[] m = identity();
        m[3] = x;
        m[7] = y;
        m[11] = z;
        return m;
    }

    private static float[] rotX(float deg) {
        double r = Math.toRadians(deg);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        float[] m = identity();
        m[5] = c;  m[6] = -s;
        m[9] = s;  m[10] = c;
        return m;
    }

    private static float[] rotY(float deg) {
        double r = Math.toRadians(deg);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        float[] m = identity();
        m[0] = c;  m[2] = s;
        m[8] = -s; m[10] = c;
        return m;
    }

    private static float[] rotZ(float deg) {
        double r = Math.toRadians(deg);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        float[] m = identity();
        m[0] = c;  m[1] = -s;
        m[4] = s;  m[5] = c;
        return m;
    }

    /** 4×4 行主序乘法。 */
    private static float[] mul(float[] a, float[] b) {
        float[] out = new float[16];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) sum += a[r * 4 + k] * b[k * 4 + c];
                out[r * 4 + c] = sum;
            }
        }
        return out;
    }

    private static float[] apply(float[] m, float x, float y, float z) {
        return new float[]{
                m[0] * x + m[1] * y + m[2] * z + m[3],
                m[4] * x + m[5] * y + m[6] * z + m[7],
                m[8] * x + m[9] * y + m[10] * z + m[11]
        };
    }

    private static float[] vec3(JSONArray a, float dx, float dy, float dz) {
        if (a == null) return new float[]{dx, dy, dz};
        return new float[]{(float) a.optDouble(0, dx), (float) a.optDouble(1, dy),
                (float) a.optDouble(2, dz)};
    }
}
