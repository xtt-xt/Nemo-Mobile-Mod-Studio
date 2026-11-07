package com.xtt.mcmodmaker.core;

import android.content.Context;

import com.xtt.mcmodmaker.util.FileUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 物品清单：内置的原版物品表 + 当前工程里的自定义物品。
 *
 * 内置表来自基岩版的真实物品清单（1929 条），带官方中文名和创造模式分类。
 * 自定义物品每次扫描工程目录得到，改完物品后调用 {@link #reloadProject(File)} 刷新。
 */
public final class ItemRegistry {

    /** 一条候选：物品 id、显示名、创造模式分类。 */
    public static final class Entry {
        public final String id;
        public final String name;
        public final String category;
        /** 是否来自当前工程（自定义物品）。 */
        public final boolean custom;

        Entry(String id, String name, String category, boolean custom) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.custom = custom;
        }

        public String display() {
            if (name == null || name.isEmpty()) return id;
            return name;
        }
    }

    private static List<Entry> vanilla;
    private static final List<Entry> custom = new ArrayList<>();

    private ItemRegistry() {
    }

    /** 读内置原版清单，只做一次。 */
    private static synchronized List<Entry> vanilla(Context context) {
        if (vanilla != null) return vanilla;
        List<Entry> list = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                context.getAssets().open("vanilla_items.json"), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONArray e = arr.optJSONArray(i);
                if (e == null || e.length() < 3) continue;
                list.add(new Entry(e.optString(0), e.optString(1), e.optString(2), false));
            }
        } catch (Exception ignored) {
        }
        vanilla = list;
        return vanilla;
    }

    /**
     * 扫描工程里的自定义物品并缓存。
     *
     * 读取 behavior_pack 下 netease_items_beh 目录里的 json 的 identifier 与 category，
     * 显示名从 resource_pack 下 texts 目录里的 lang 文件（item.<id>.name）取。
     */
    public static synchronized void reloadProject(Context context, File projectDir) {
        custom.clear();
        if (projectDir == null || !projectDir.isDirectory()) return;

        List<String> displayNames = readLangNames(projectDir);

        File[] roots = projectDir.listFiles();
        if (roots == null) return;
        for (File pack : roots) {
            if (!pack.isDirectory()) continue;
            String pn = pack.getName().toLowerCase();
            if (!pn.startsWith("behavior_pack") && !pn.startsWith("behaviour_pack")) continue;
            File itemsDir = new File(pack, "netease_items_beh");
            File[] files = itemsDir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.getName().toLowerCase().endsWith(".json")) continue;
                Entry e = readItemFile(f, displayNames);
                if (e != null) custom.add(e);
            }
        }
        Collections.sort(custom, (a, b) -> a.id.compareToIgnoreCase(b.id));
    }

    private static Entry readItemFile(File f, List<String> displayNames) {
        try {
            JSONObject root = new JSONObject(FileUtils.readText(f));
            JSONObject item = root.optJSONObject("minecraft:item");
            if (item == null) return null;
            JSONObject desc = item.optJSONObject("description");
            if (desc == null) return null;
            String identifier = desc.optString("identifier", "");
            if (identifier.isEmpty()) return null;
            String category = desc.optString("category", "Items");
            String name = lookupLang(displayNames, identifier);
            return new Entry(identifier, name, category, true);
        } catch (Exception e) {
            return null;
        }
    }

    /** 把所有 lang 读成 "键=值" 的平铺列表，避免依赖具体语言文件路径。 */
    private static List<String> readLangNames(File projectDir) {
        List<String> out = new ArrayList<>();
        File[] roots = projectDir.listFiles();
        if (roots == null) return out;
        for (File pack : roots) {
            if (!pack.isDirectory()) continue;
            if (!pack.getName().toLowerCase().startsWith("resource_pack")) continue;
            File texts = new File(pack, "texts");
            File[] langs = texts.listFiles();
            if (langs == null) continue;
            for (File l : langs) {
                if (!l.getName().toLowerCase().endsWith(".lang")) continue;
                try {
                    String content = FileUtils.readText(l);
                    if (content == null) continue;
                    for (String line : content.split("\r?\n")) {
                        if (line.startsWith("item.") && line.contains(".name=")) out.add(line);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    private static String lookupLang(List<String> lines, String identifier) {
        String key = "item." + identifier + ".name=";
        for (String line : lines) {
            if (line.startsWith(key)) {
                String v = line.substring(key.length()).trim();
                if (!v.isEmpty()) return v;
            }
        }
        return "";
    }

    /** 当前工程里的自定义物品。 */
    public static synchronized List<Entry> customItems() {
        return new ArrayList<>(custom);
    }

    /** 找某个 id 的显示名，找不到返回 id 本身。 */
    public static synchronized String nameOf(Context context, String id) {
        if (id == null) return "";
        for (Entry e : custom) {
            if (id.equals(e.id)) return e.display();
        }
        for (Entry e : vanilla(context)) {
            if (id.equals(e.id)) return e.display();
        }
        return id;
    }

    /** id 是否存在（本工程或原版）。 */
    public static synchronized boolean exists(Context context, String id) {
        if (id == null || id.isEmpty()) return false;
        for (Entry e : custom) if (id.equals(e.id)) return true;
        for (Entry e : vanilla(context)) if (id.equals(e.id)) return true;
        return false;
    }

    /**
     * 搜索候选。空查询时返回「本项目自定义物品 + 分类靠前的原版物品」，
     * 便于一打开就有东西可点。
     *
     * 排序规则：自定义物品优先，其次 id 前缀匹配，再次显示名前缀匹配，
     * 再次包含匹配。
     */
    public static List<Entry> search(Context context, String query, int limit) {
        List<Entry> pool = new ArrayList<>();
        synchronized (ItemRegistry.class) {
            pool.addAll(custom);
        }
        pool.addAll(vanilla(context));

        String q = query == null ? "" : query.trim().toLowerCase();
        List<Entry> out = new ArrayList<>();
        if (q.isEmpty()) {
            for (Entry e : pool) {
                if (e.custom || "Items".equals(e.category)) out.add(e);
                if (out.size() >= limit) break;
            }
            if (out.isEmpty()) out.addAll(pool.subList(0, Math.min(limit, pool.size())));
            return out;
        }

        List<Entry> tier1 = new ArrayList<>();
        List<Entry> tier2 = new ArrayList<>();
        List<Entry> tier3 = new ArrayList<>();
        for (Entry e : pool) {
            String id = e.id.toLowerCase();
            String nm = e.name == null ? "" : e.name.toLowerCase();
            String shortId = id.startsWith("minecraft:") ? id.substring(10) : id;
            if (id.startsWith(q) || shortId.startsWith(q)) tier1.add(e);
            else if (nm.startsWith(q)) tier2.add(e);
            else if (id.contains(q) || nm.contains(q)) tier3.add(e);
        }
        out.addAll(tier1);
        out.addAll(tier2);
        out.addAll(tier3);
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }
}
