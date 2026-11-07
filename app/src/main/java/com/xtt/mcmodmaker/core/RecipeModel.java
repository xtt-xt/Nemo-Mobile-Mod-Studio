package com.xtt.mcmodmaker.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一条配方（五种类型之一）的强类型模型。
 *
 * 编辑器的原则是「解析一次、改模型、整体序列化」，不再对原 JSON 做局部读写。
 * 为了不把用户手写、编辑器不认识的字段弄丢，模型会保留原文件里的未知字段，
 * 序列化时再补回去。
 */
public final class RecipeModel {

    public static final int SHAPED = 0;
    public static final int SHAPELESS = 1;
    public static final int FURNACE = 2;
    public static final int BREWING = 3;
    public static final int SMITHING = 4;
    /** 认不出来的配方类型：只允许源码编辑。 */
    public static final int UNSUPPORTED = -1;

    public static final int UNLOCK_ALWAYS = 0;
    public static final int UNLOCK_ITEMS = 1;
    public static final int UNLOCK_WATER = 2;
    public static final int UNLOCK_NONE = 3;

    private static final String[] TYPE_KEYS = {
            "minecraft:recipe_shaped",
            "minecraft:recipe_shapeless",
            "minecraft:recipe_furnace",
            "minecraft:recipe_brewing_mix",
            "minecraft:recipe_smithing_transform",
    };
    private static final String[] TYPE_NAMES = {
            "有序合成", "无序合成", "熔炉", "酿造", "锻造",
    };

    // ==================== 通用字段 ====================

    public int type = UNSUPPORTED;
    public String identifier = "";
    public String formatVersion = "1.12";
    public List<String> tags = new ArrayList<>();

    public int unlockMode = UNLOCK_ALWAYS;
    public List<ItemRef> unlockItems = new ArrayList<>();

    // ==================== 各类型字段 ====================

    /** 有序合成：3 行，每行 3 字符，空格表示空位。 */
    public String[] pattern = {"   ", "   ", "   "};
    /** 有序合成：字母到物品。用 LinkedHashMap 保持原文件里的字母顺序。 */
    public LinkedHashMap<Character, ItemRef> key = new LinkedHashMap<>();

    /** 无序合成原料。 */
    public List<ItemRef> ingredients = new ArrayList<>();

    /** 熔炉输入 / 输出。 */
    public ItemRef furnaceInput;
    public ItemRef furnaceOutput;

    /** 酿造：输入药水、试剂、产物，三者都是字符串形式的物品引用。 */
    public ItemRef brewInput;
    public ItemRef brewReagent;
    public ItemRef brewOutput;

    /** 锻造：模板、基底、添加物、产物。 */
    public ItemRef smithTemplate;
    public ItemRef smithBase;
    public ItemRef smithAddition;
    public ItemRef smithResult;

    /** 有序 / 无序 / 熔炉的产出。 */
    public ItemRef result;
    public int resultCount = 1;

    // ==================== 保真用 ====================

    /** 顶层除 format_version 与类型键之外的字段。 */
    private final List<Object[]> unknownTop = new ArrayList<>();
    /** 配方体里编辑器不认识的字段。 */
    private final List<Object[]> unknownBody = new ArrayList<>();

    private RecipeModel() {
    }

    public String typeKey() {
        return type >= 0 && type < TYPE_KEYS.length ? TYPE_KEYS[type] : "";
    }

    public String typeName() {
        return type >= 0 && type < TYPE_NAMES.length ? TYPE_NAMES[type] : "未知类型";
    }

    public boolean supported() {
        return type != UNSUPPORTED;
    }

    public String localName() {
        int i = identifier.indexOf(':');
        return i >= 0 ? identifier.substring(i + 1) : identifier;
    }

    // ==================== 解析 ====================

    /** 只判断类型键，用于文件列表分类，不做完整解析。 */
    public static int detectType(String json) {
        if (json == null) return UNSUPPORTED;
        for (int i = 0; i < TYPE_KEYS.length; i++) {
            if (json.contains("\"" + TYPE_KEYS[i] + "\"")) return i;
        }
        return UNSUPPORTED;
    }

    public static RecipeModel parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        RecipeModel m = new RecipeModel();

        int found = UNSUPPORTED;
        for (int i = 0; i < TYPE_KEYS.length; i++) {
            if (root.has(TYPE_KEYS[i])) {
                found = i;
                break;
            }
        }
        m.type = found;
        m.formatVersion = root.optString("format_version", "1.12");

        Iterator<String> topKeys = root.keys();
        while (topKeys.hasNext()) {
            String k = topKeys.next();
            if (k.equals("format_version")) continue;
            if (k.equals(TYPE_KEYS[found])) continue;
            m.unknownTop.add(new Object[]{k, root.opt(k)});
        }

        if (found == UNSUPPORTED) return m;

        JSONObject body = root.optJSONObject(TYPE_KEYS[found]);
        if (body == null) {
            m.type = UNSUPPORTED;
            return m;
        }

        JSONObject desc = body.optJSONObject("description");
        if (desc != null) m.identifier = desc.optString("identifier", "");

        m.tags = readStringArray(body.optJSONArray("tags"));
        m.readUnlock(body);

        switch (found) {
            case SHAPED:
                m.readShaped(body);
                break;
            case SHAPELESS:
                m.readShapeless(body);
                break;
            case FURNACE:
                m.furnaceInput = ItemRef.fromJson(body.opt("input"));
                m.furnaceOutput = ItemRef.fromJson(body.opt("output"));
                break;
            case BREWING:
                m.brewInput = ItemRef.fromJson(body.opt("input"));
                m.brewReagent = ItemRef.fromJson(body.opt("reagent"));
                m.brewOutput = ItemRef.fromJson(body.opt("output"));
                break;
            case SMITHING:
                m.smithTemplate = ItemRef.fromJson(body.opt("template"));
                m.smithBase = ItemRef.fromJson(body.opt("base"));
                m.smithAddition = ItemRef.fromJson(body.opt("addition"));
                m.smithResult = ItemRef.fromJson(body.opt("result"));
                break;
            default:
                break;
        }

        if (m.type == SHAPED || m.type == SHAPELESS) {
            m.readResultObject(body.optJSONObject("result"));
        }

        // 收集本类型不认识的 body 字段
        List<String> known = new ArrayList<>();
        known.add("description");
        known.add("tags");
        known.add("unlock");
        switch (m.type) {
            case SHAPED:
                known.add("pattern");
                known.add("key");
                known.add("result");
                break;
            case SHAPELESS:
                known.add("ingredients");
                known.add("result");
                break;
            case FURNACE:
                known.add("input");
                known.add("output");
                break;
            case BREWING:
                known.add("input");
                known.add("reagent");
                known.add("output");
                break;
            case SMITHING:
                known.add("template");
                known.add("base");
                known.add("addition");
                known.add("result");
                break;
            default:
                break;
        }
        Iterator<String> bk = body.keys();
        while (bk.hasNext()) {
            String k = bk.next();
            if (!known.contains(k)) m.unknownBody.add(new Object[]{k, body.opt(k)});
        }
        return m;
    }

    private void readUnlock(JSONObject body) {
        unlockItems.clear();
        Object o = body.opt("unlock");
        if (o == null || o == JSONObject.NULL) {
            unlockMode = UNLOCK_NONE;
            return;
        }
        if (o instanceof JSONArray) {
            // 数组形式 = 获得其中任意物品时解锁
            unlockMode = UNLOCK_ITEMS;
            JSONArray arr = (JSONArray) o;
            for (int i = 0; i < arr.length(); i++) {
                ItemRef r = ItemRef.fromJson(arr.opt(i));
                if (r != null) unlockItems.add(r);
            }
            return;
        }
        if (o instanceof JSONObject) {
            String ctx = ((JSONObject) o).optString("context", "");
            if ("PlayerInWater".equals(ctx)) unlockMode = UNLOCK_WATER;
            else unlockMode = UNLOCK_ALWAYS;
            return;
        }
        unlockMode = UNLOCK_ALWAYS;
    }

    private void readShaped(JSONObject body) {
        JSONArray pat = body.optJSONArray("pattern");
        String[] rows = {"   ", "   ", "   "};
        if (pat != null) {
            for (int i = 0; i < pat.length() && i < 3; i++) {
                String s = pat.optString(i, "");
                StringBuilder sb = new StringBuilder(s);
                while (sb.length() < 3) sb.append(' ');
                rows[i] = sb.substring(0, 3);
            }
        }
        pattern = rows;

        JSONObject k = body.optJSONObject("key");
        if (k != null) {
            Iterator<String> it = k.keys();
            while (it.hasNext()) {
                String letter = it.next();
                if (letter.isEmpty()) continue;
                ItemRef r = ItemRef.fromJson(k.opt(letter));
                if (r != null) key.put(letter.charAt(0), r);
            }
        }
        // 网格里出现、但 key 里没有的字母，给个空引用以便 UI 提示
        for (String row : pattern) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c != ' ' && !key.containsKey(c)) key.put(c, null);
            }
        }
    }

    private void readShapeless(JSONObject body) {
        JSONArray arr = body.optJSONArray("ingredients");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            ItemRef r = ItemRef.fromJson(arr.opt(i));
            if (r != null) ingredients.add(r);
        }
    }

    private void readResultObject(JSONObject resultObj) {
        if (resultObj == null) return;
        ItemRef r = ItemRef.fromJson(resultObj);
        if (r == null) return;
        result = r;
        resultCount = resultObj.optInt("count", 1);
        if (resultCount <= 0) resultCount = 1;
    }

    private static List<String> readStringArray(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "");
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    // ==================== 序列化 ====================

    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("format_version", formatVersion);
            if (type == UNSUPPORTED) {
                for (Object[] kv : unknownTop) root.put((String) kv[0], kv[1]);
                return root;
            }

            JSONObject body = new JSONObject();
            JSONObject desc = new JSONObject();
            desc.put("identifier", identifier);
            body.put("description", desc);

            if (!tags.isEmpty()) {
                JSONArray t = new JSONArray();
                for (String s : tags) t.put(s);
                body.put("tags", t);
            }

            switch (type) {
                case SHAPED:
                    writeShaped(body);
                    break;
                case SHAPELESS:
                    writeShapeless(body);
                    break;
                case FURNACE:
                    if (furnaceInput != null && !furnaceInput.isEmpty()) body.put("input", furnaceInput.asString());
                    if (furnaceOutput != null && !furnaceOutput.isEmpty()) body.put("output", furnaceOutput.asString());
                    break;
                case BREWING:
                    if (brewInput != null && !brewInput.isEmpty()) body.put("input", brewInput.asString());
                    if (brewReagent != null && !brewReagent.isEmpty()) body.put("reagent", brewReagent.asString());
                    if (brewOutput != null && !brewOutput.isEmpty()) body.put("output", brewOutput.asString());
                    break;
                case SMITHING:
                    if (smithTemplate != null && !smithTemplate.isEmpty()) body.put("template", smithTemplate.asString());
                    if (smithBase != null && !smithBase.isEmpty()) body.put("base", smithBase.asString());
                    if (smithAddition != null && !smithAddition.isEmpty()) body.put("addition", smithAddition.asString());
                    if (smithResult != null && !smithResult.isEmpty()) body.put("result", smithResult.asString());
                    break;
                default:
                    break;
            }

            writeUnlock(body);

            // 补回未知字段，不覆盖已知字段
            for (Object[] kv : unknownBody) {
                if (body.has((String) kv[0])) continue;
                body.put((String) kv[0], kv[1]);
            }

            root.put(TYPE_KEYS[type], body);

            for (Object[] kv : unknownTop) {
                if (root.has((String) kv[0])) continue;
                root.put((String) kv[0], kv[1]);
            }
        } catch (Exception ignored) {
        }
        return root;
    }

    private void writeShaped(JSONObject body) throws Exception {
        JSONArray pat = new JSONArray();
        for (String row : trimTrailingEmptyRows(pattern)) pat.put(row);
        body.put("pattern", pat);

        JSONObject k = new JSONObject();
        for (Map.Entry<Character, ItemRef> e : key.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            // 只写网格里真的用到的字母，避免 key 里堆垃圾
            if (!patternUses(e.getKey())) continue;
            k.put(String.valueOf(e.getKey()), e.getValue().toJson());
        }
        body.put("key", k);

        if (result != null && !result.isEmpty()) {
            body.put("result", result.toJsonWithCount(resultCount));
        }
    }

    private void writeShapeless(JSONObject body) throws Exception {
        JSONArray arr = new JSONArray();
        for (ItemRef r : ingredients) {
            if (r != null && !r.isEmpty()) arr.put(r.toJson());
        }
        body.put("ingredients", arr);
        if (result != null && !result.isEmpty()) {
            body.put("result", result.toJsonWithCount(resultCount));
        }
    }

    private void writeUnlock(JSONObject body) throws Exception {
        switch (unlockMode) {
            case UNLOCK_ALWAYS: {
                JSONObject o = new JSONObject();
                o.put("context", "AlwaysUnlocked");
                body.put("unlock", o);
                break;
            }
            case UNLOCK_WATER: {
                JSONObject o = new JSONObject();
                o.put("context", "PlayerInWater");
                body.put("unlock", o);
                break;
            }
            case UNLOCK_ITEMS: {
                JSONArray arr = new JSONArray();
                for (ItemRef r : unlockItems) {
                    if (r != null && !r.isEmpty()) arr.put(r.toJson());
                }
                body.put("unlock", arr);
                break;
            }
            default:
                // UNLOCK_NONE：整个字段不写
                break;
        }
    }

    /**
     * 裁掉尾部整行为空的行，并去掉每行末尾空格。
     *
     * 中间的空行必须原样保留：["   ","XXX","   "] 表示材料在中间那一行，
     * 一旦被裁成 ["XXX"] 就变成第一行了，配方形状直接错掉。
     */
    private static String[] trimTrailingEmptyRows(String[] rows) {
        int last = rows.length - 1;
        while (last > 0 && rows[last].trim().isEmpty()) last--;
        String[] out = new String[last + 1];
        for (int i = 0; i <= last; i++) {
            String r = rows[i];
            int e = r.length() - 1;
            while (e >= 0 && r.charAt(e) == ' ') e--;
            out[i] = e < 0 ? "" : r.substring(0, e + 1);
        }
        return out;
    }

    private boolean patternUses(char letter) {
        for (String row : pattern) {
            if (row.indexOf(letter) >= 0) return true;
        }
        return false;
    }

    // ==================== 便捷操作 ====================

    /** 网格里用到的字母，按出现顺序。 */
    public List<Character> usedLetters() {
        List<Character> out = new ArrayList<>();
        for (String row : pattern) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c != ' ' && !out.contains(c)) out.add(c);
            }
        }
        return out;
    }

    /** 找一个还没被占用的字母，用于新建映射。 */
    public char nextFreeLetter() {
        List<Character> used = usedLetters();
        for (char c = 'A'; c <= 'Z'; c++) {
            if (!used.contains(c)) return c;
        }
        for (char c = 'a'; c <= 'z'; c++) {
            if (!used.contains(c)) return c;
        }
        for (char c = '0'; c <= '9'; c++) {
            if (!used.contains(c)) return c;
        }
        return '#';
    }

    /** 清掉网格里已经不用、且没有物品的字母映射。 */
    public void pruneKey() {
        Iterator<Map.Entry<Character, ItemRef>> it = key.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Character, ItemRef> e = it.next();
            if (!patternUses(e.getKey()) && (e.getValue() == null || e.getValue().isEmpty())) {
                it.remove();
            }
        }
    }

    /** 校验，返回给用户看的问题列表；空列表表示没发现问题。 */
    public List<String> validate() {
        List<String> errs = new ArrayList<>();
        if (type == UNSUPPORTED) {
            errs.add("不支持的配方类型，请用源码编辑");
            return errs;
        }
        if (identifier == null || identifier.trim().isEmpty() || !identifier.contains(":")) {
            errs.add("配方标识缺失或格式不对（应为 命名空间:名称）");
        }

        switch (type) {
            case SHAPED: {
                boolean anyCell = false;
                for (String row : pattern) {
                    if (!row.trim().isEmpty()) anyCell = true;
                }
                if (!anyCell) errs.add("配方图案是空的");
                for (Character c : usedLetters()) {
                    ItemRef r = key.get(c);
                    if (r == null || r.isEmpty()) errs.add("图案里的 " + c + " 还没指定物品");
                }
                if (result == null || result.isEmpty()) errs.add("还没有设置产出物品");
                break;
            }
            case SHAPELESS: {
                int n = 0;
                for (ItemRef r : ingredients) if (r != null && !r.isEmpty()) n++;
                if (n == 0) errs.add("还没有添加任何原料");
                if (result == null || result.isEmpty()) errs.add("还没有设置产出物品");
                break;
            }
            case FURNACE:
                if (furnaceInput == null || furnaceInput.isEmpty()) errs.add("还没有设置输入物品");
                if (furnaceOutput == null || furnaceOutput.isEmpty()) errs.add("还没有设置输出物品");
                break;
            case BREWING:
                if (brewInput == null || brewInput.isEmpty()) errs.add("还没有设置输入药水");
                if (brewReagent == null || brewReagent.isEmpty()) errs.add("还没有设置试剂");
                if (brewOutput == null || brewOutput.isEmpty()) errs.add("还没有设置产物");
                break;
            case SMITHING:
                if (smithTemplate == null || smithTemplate.isEmpty()) errs.add("还没有设置锻造模板");
                if (smithBase == null || smithBase.isEmpty()) errs.add("还没有设置基底");
                if (smithAddition == null || smithAddition.isEmpty()) errs.add("还没有设置添加物");
                if (smithResult == null || smithResult.isEmpty()) errs.add("还没有设置产物");
                break;
            default:
                break;
        }
        return errs;
    }
}
