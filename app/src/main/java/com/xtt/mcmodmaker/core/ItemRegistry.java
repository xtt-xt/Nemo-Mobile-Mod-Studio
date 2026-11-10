package com.xtt.mcmodmaker.core;

import android.content.Context;

import com.xtt.mcmodmaker.util.FileUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品清单：内置的原版物品库 + 当前工程里的自定义物品 / 方块。
 *
 * 原版库按基岩版 1.21.120 生成（网易中国版当前对应的版本），
 * 含方块与物品，带官方中文名和创造模式分类。
 *
 * 自定义条目来自 behavior_pack 下的 netease_items_beh 与 netease_blocks，
 * 显示名从资源包 lang 里读 tile./item. 加完整 id 的键。
 */
public final class ItemRegistry {

    /** 一条候选。 */
    public static final class Entry {
        public final String id;
        public final String name;
        public final String category;
        /** 是否来自当前工程。 */
        public final boolean custom;
        /** 是否方块（影响图标与分组显示）。 */
        public final boolean block;

        Entry(String id, String name, String category, boolean custom, boolean block) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.custom = custom;
            this.block = block;
        }

        /** 有中文名就用中文名，否则用 id 的短名。 */
        public String display() {
            if (name != null && !name.isEmpty()) return name;
            return shortId(id);
        }

        /** 显示名是否与本条 id 不同（不同才值得把 id 也显示出来）。 */
        public boolean hasDistinctName() {
            return name != null && !name.isEmpty() && !name.equals(id);
        }
    }

    /**
     * 原版库对应的版本。
     *
     * 网易中国版 3.10 = 基岩版 1.21.130（2026-10-30 全渠道推送），
     * 所以清单按 1.21.130 生成，界面上要把这两层版本都标出来，
     * 免得有人拿着 3.9 的工程来对不上。
     */
    public static final String VANILLA_VERSION = "1.21.130";
    /** 对应的网易中国版版本号。 */
    public static final String NETEASE_VERSION = "3.10";

    private static List<Entry> vanilla;
    private static final List<Entry> custom = new ArrayList<>();

    private ItemRegistry() {
    }

    private static synchronized List<Entry> vanilla(Context context) {
        if (vanilla != null) return vanilla;
        List<Entry> list = new ArrayList<>();
        try (InputStream in = context.getAssets().open("vanilla_items.json")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            JSONObject root = new JSONObject(bos.toString("UTF-8"));
            JSONArray arr = root.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONArray e = arr.optJSONArray(i);
                    if (e == null || e.length() < 3) continue;
                    list.add(new Entry(e.optString(0), e.optString(1), e.optString(2),
                            false, e.optInt(3, 0) == 1));
                }
            }
        } catch (Exception ignored) {
        }
        vanilla = list;
        return vanilla;
    }

    /**
     * 扫描工程里的自定义物品与方块并缓存。
     *
     * 定义文件：behavior_pack 下 netease_items_beh 与 netease_blocks 里的 json；
     * 显示名：resource_pack 下 texts 里的 lang，键形如
     * tile.命名空间:方块名.name 或 item.命名空间:物品名.name，
     * 也兼容不带命名空间的写法。
     */
    public static synchronized void reloadProject(Context context, File projectDir) {
        custom.clear();
        if (projectDir == null || !projectDir.isDirectory()) return;

        Map<String, String> names = readLangNames(projectDir);

        File[] roots = projectDir.listFiles();
        if (roots == null) return;
        for (File pack : roots) {
            if (!pack.isDirectory()) continue;
            String pn = pack.getName().toLowerCase();
            if (!StudioConfig.isBehaviorPack(pn)) continue;
            collectFrom(new File(pack, "netease_items_beh"), false, names);
            collectFrom(new File(pack, "netease_blocks"), true, names);
        }
        Collections.sort(custom, (a, b) -> a.id.compareToIgnoreCase(b.id));
    }

    private static void collectFrom(File dir, boolean block, Map<String, String> names) {
        collectFrom(dir, block, names, 0);
    }

    /**
     * 递归收集定义文件。
     *
     * 有的工程把方块按系列分了子目录（netease_blocks/blast_glass/*.json），
     * 只扫一层会一个都读不到，配方校验就会把本工程的东西全报成「找不到物品」。
     */
    private static void collectFrom(File dir, boolean block, Map<String, String> names, int depth) {
        if (dir == null || depth > 8) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                String dn = f.getName();
                if (dn.equals(GitRepo.GIT_DIR) || dn.equals(".mcs")) continue;
                collectFrom(f, block, names, depth + 1);
                continue;
            }
            if (!f.getName().toLowerCase().endsWith(".json")) continue;
            Entry e = readDefinition(f, block, names);
            if (e == null) continue;
            // 同一 id 可能既在物品目录又在方块目录，去重
            boolean dup = false;
            for (Entry old : custom) {
                if (old.id.equals(e.id)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) custom.add(e);
        }
    }

    private static Entry readDefinition(File f, boolean block, Map<String, String> names) {
        try {
            JSONObject root = new JSONObject(FileUtils.readText(f));
            JSONObject def = block ? root.optJSONObject("minecraft:block")
                    : root.optJSONObject("minecraft:item");
            if (def == null) {
                // 有的模板把方块写成 minecraft:block，物品写成 minecraft:item，
                // 目录放错时互相兜一下
                def = root.optJSONObject("minecraft:block");
                if (def != null) block = true;
                else {
                    def = root.optJSONObject("minecraft:item");
                    if (def == null) return null;
                    block = false;
                }
            }
            JSONObject desc = def.optJSONObject("description");
            if (desc == null) return null;
            String identifier = desc.optString("identifier", "");
            if (identifier.isEmpty()) return null;
            String category = desc.optString("category", "Items");
            // 自定义方块用的小写分类名，转成界面上的统一写法
            category = normalizeCategory(category);
            String name = lookupName(names, identifier);
            return new Entry(identifier, name, category, true, block);
        } catch (Exception e) {
            return null;
        }
    }

    /** 基岩版自定义方块的 category 是小写的 construction / nature，统一成原版库的写法。 */
    private static String normalizeCategory(String c) {
        if (c == null || c.isEmpty()) return "Items";
        switch (c.toLowerCase()) {
            case "construction": return "Construction";
            case "nature": return "Nature";
            case "equipment": return "Equipment";
            case "items": return "Items";
            default:
                // 已经是首字母大写就原样返回
                return c;
        }
    }

    /**
     * 各语言的优先级，数字小的优先。
     *
     * 一个物品在 zh_CN / zh_TW / en_US 里各有一条显示名，
     * 之前的写法是「带命名空间的键直接覆盖」，文件按目录顺序读完，
     * en_US 最后读就把中文顶掉了 —— 界面上全变成 Magenta Blast Glass。
     * 现在固定中文优先，其次繁体，最后才用英文。
     */
    private static int langPriority(String fileName) {
        String n = fileName.toLowerCase();
        if (n.startsWith("zh_cn") || n.startsWith("zh_hans")) return 0;
        if (n.startsWith("zh_tw") || n.startsWith("zh_hant") || n.startsWith("zh_hk")) return 1;
        if (n.startsWith("zh")) return 2;
        if (n.startsWith("en_us")) return 3;
        if (n.startsWith("en")) return 4;
        return 5;
    }

    /**
     * 读 lang：完整 id（带命名空间）和短名各存一份，前者优先；
     * 同一个键按语言优先级保留，中文不会被英文覆盖。
     */
    private static Map<String, String> readLangNames(File projectDir) {
        // 两份：带命名空间的完整键、不带命名空间的短名
        Map<String, String> full = new HashMap<>();
        Map<String, Integer> fullPri = new HashMap<>();
        Map<String, String> brief = new HashMap<>();
        Map<String, Integer> briefPri = new HashMap<>();

        File[] roots = projectDir.listFiles();
        if (roots == null) return new HashMap<>();
        for (File pack : roots) {
            if (!pack.isDirectory()) continue;
            if (!StudioConfig.isResourcePack(pack.getName())) continue;
            File texts = new File(pack, "texts");
            File[] langs = texts.listFiles();
            if (langs == null) continue;
            for (File l : langs) {
                if (!l.getName().toLowerCase().endsWith(".lang")) continue;
                int pri = langPriority(l.getName());
                String content = FileUtils.readText(l);
                if (content == null || content.isEmpty()) continue;
                if (content.startsWith("\uFEFF")) content = content.substring(1);
                for (String line : content.split("\r?\n")) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    if (value.isEmpty()) continue;
                    if (!(key.startsWith("tile.") || key.startsWith("item."))) continue;
                    if (!key.endsWith(".name")) continue;
                    String mid = key.substring(key.indexOf('.') + 1, key.length() - 5);
                    if (mid.isEmpty()) continue;
                    if (mid.contains(":")) {
                        putIfBetter(full, fullPri, mid, value, pri);
                    } else {
                        putIfBetter(brief, briefPri, mid, value, pri);
                    }
                }
            }
        }
        // 合成一张表：完整 id 放原键，短名照旧放原键。
        // 两边同名的极少数情况以完整 id 为准（先放短名，再用完整 id 覆盖它）
        Map<String, String> out = new HashMap<>(brief);
        out.putAll(full);
        return out;
    }

    /** 没有，或者已有的是更低优先级的语言，才写进去。 */
    private static void putIfBetter(Map<String, String> values, Map<String, Integer> prios,
                                    String key, String value, int pri) {
        Integer old = prios.get(key);
        if (old == null || pri < old) {
            values.put(key, value);
            prios.put(key, pri);
        }
    }

    private static String lookupName(Map<String, String> names, String identifier) {
        String v = names.get(identifier);
        if (v != null) return v;
        int i = identifier.indexOf(':');
        if (i >= 0) {
            v = names.get(identifier.substring(i + 1));
            if (v != null) return v;
        }
        return "";
    }

    /** 内置原版库的条目数量（关于页要显示）。 */
    public static int vanillaCount(Context context) {
        return vanilla(context).size();
    }

    /** 当前工程里的自定义物品与方块。 */
    public static synchronized List<Entry> customItems() {
        return new ArrayList<>(custom);
    }

    /** 找某个 id 的显示名，找不到返回 id 的短名。 */
    public static String nameOf(Context context, String id) {
        if (id == null || id.isEmpty()) return "";
        synchronized (ItemRegistry.class) {
            for (Entry e : custom) {
                if (id.equals(e.id)) return e.display();
            }
        }
        for (Entry e : vanilla(context)) {
            if (id.equals(e.id)) return e.display();
        }
        return shortId(id);
    }

    /** 清单里记着这个 id 是方块吗；不认识的 id 返回 false。 */
    public static boolean isBlock(Context context, String id) {
        if (id == null || id.isEmpty()) return false;
        synchronized (ItemRegistry.class) {
            for (Entry e : custom) {
                if (id.equals(e.id)) return e.block;
            }
        }
        for (Entry e : vanilla(context)) {
            if (id.equals(e.id)) return e.block;
        }
        return false;
    }

    /** 这个 id 是否在清单里。 */
    public static boolean exists(Context context, String id) {
        if (id == null || id.isEmpty()) return false;
        synchronized (ItemRegistry.class) {
            for (Entry e : custom) if (id.equals(e.id)) return true;
        }
        for (Entry e : vanilla(context)) if (id.equals(e.id)) return true;
        return false;
    }

    /**
     * 搜索候选。
     *
     * 排序：自定义条目优先（本工程的东西最常选），其次 id 前缀、名称前缀、包含。
     * 空查询时给自定义 + 分类为 Items 的原版条目，保证一打开就有东西可点。
     */
    public static List<Entry> search(Context context, String query, int limit) {
        List<Entry> mine = customItems();
        List<Entry> pool = new ArrayList<>(mine);
        pool.addAll(vanilla(context));

        String q = query == null ? "" : query.trim().toLowerCase();
        List<Entry> out = new ArrayList<>();
        if (q.isEmpty()) {
            // 没有关键字时也要能翻到底：自定义 → 常用物品 → 其余（多半是方块）
            out.addAll(mine);
            List<Entry> rest = new ArrayList<>();
            for (Entry e : vanilla(context)) {
                if ("Items".equals(e.category)) out.add(e);
                else rest.add(e);
            }
            out.addAll(rest);
            return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
        }

        List<Entry> t1 = new ArrayList<>();
        List<Entry> t2 = new ArrayList<>();
        List<Entry> t3 = new ArrayList<>();
        for (Entry e : pool) {
            String id = e.id.toLowerCase();
            String nm = e.name == null ? "" : e.name.toLowerCase();
            String sid = shortId(id);
            if (id.startsWith(q) || sid.startsWith(q)) t1.add(e);
            else if (nm.startsWith(q)) t2.add(e);
            else if (id.contains(q) || nm.contains(q)) t3.add(e);
        }
        out.addAll(t1);
        out.addAll(t2);
        out.addAll(t3);
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private static String shortId(String id) {
        if (id == null) return "";
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }
}
