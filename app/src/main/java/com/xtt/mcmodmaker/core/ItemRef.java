package com.xtt.mcmodmaker.core;

import org.json.JSONObject;

/**
 * 配方里的一个物品引用。
 *
 * <p>基岩版里同一个物品有三种写法，本类统一表示后再按需输出：
 * <ul>
 *   <li>裸字符串 {@code "minecraft:iron_ingot"}</li>
 *   <li>带附加值 {@code "minecraft:wool:14"}（老写法，字符串形式）</li>
 *   <li>对象 {@code {"item":"minecraft:wool","data":14}}</li>
 * </ul>
 *
 * <p>{@link #data} 与 {@link #count} 用 -1 表示「文件里没写」，与 0 区分开：
 * 羊毛的白色就是 data=0，如果把它当成「没写」会丢数据。
 */
public final class ItemRef {

    /** 未指定。 */
    public static final int UNSET = -1;

    public String id;
    public int data = UNSET;
    public int count = UNSET;

    public ItemRef(String id) {
        this.id = id;
    }

    public ItemRef(String id, int data) {
        this.id = id;
        this.data = data;
    }

    public static ItemRef of(String id, int data, int count) {
        ItemRef r = new ItemRef(id, data);
        r.count = count;
        return r;
    }

    public boolean isEmpty() {
        return id == null || id.trim().isEmpty();
    }

    public boolean hasData() {
        return data != UNSET;
    }

    /**
     * 解析 {@code "minecraft:wool:14"} 这种字符串。
     *
     * <p>注意不能用 lastIndexOf(':') 之后简单判断：id 本身含一个冒号，
     * 所以按冒号切分后段数为 3 才说明最后一段是附加值。
     */
    public static ItemRef parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        String[] parts = t.split(":");
        if (parts.length >= 3) {
            String last = parts[parts.length - 1];
            try {
                int d = Integer.parseInt(last);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) sb.append(':');
                    sb.append(parts[i]);
                }
                return new ItemRef(sb.toString(), d);
            } catch (NumberFormatException ignored) {
                // 最后一段不是数字，整体当作 id
            }
        }
        return new ItemRef(t);
    }

    /** 从配方 JSON 里的任意形式读出物品引用。 */
    public static ItemRef fromJson(Object o) {
        if (o == null || o == JSONObject.NULL) return null;
        if (o instanceof String) return parse((String) o);
        if (o instanceof JSONObject) {
            JSONObject obj = (JSONObject) o;
            String id = obj.optString("item", "");
            if (id.isEmpty()) id = obj.optString("name", "");
            if (id.isEmpty()) return null;
            ItemRef r = new ItemRef(id);
            if (obj.has("data")) r.data = obj.optInt("data", UNSET);
            if (obj.has("count")) r.count = obj.optInt("count", UNSET);
            return r;
        }
        return null;
    }

    /** 输出成 {@code "minecraft:wool:14"}；没写附加值就只输出 id。 */
    public String asString() {
        if (id == null) return "";
        return hasData() ? id + ":" + data : id;
    }

    /** 输出成对象形式 {@code {"item":...,"data":...}}，count 另算。 */
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("item", id);
            if (hasData()) o.put("data", data);
        } catch (Exception ignored) {
        }
        return o;
    }

    /** 带 count 的对象形式，用于 result / input 需要数量的位置。 */
    public JSONObject toJsonWithCount(int defaultCount) {
        JSONObject o = toJson();
        try {
            o.put("count", count != UNSET ? count : defaultCount);
        } catch (Exception ignored) {
        }
        return o;
    }

    public ItemRef copy() {
        return of(id, data, count);
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ItemRef)) return false;
        ItemRef o = (ItemRef) obj;
        return eq(id, o.id) && data == o.data && count == o.count;
    }

    @Override
    public int hashCode() {
        return (id == null ? 0 : id.hashCode()) * 31 + data;
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
