package com.xtt.mcmodmaker.core;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 项目配置（studio.json）与行为包 / 资源包目录的判断。
 *
 * 网易 MCStudio 建出来的包目录带随机后缀（behavior_pack_xxxxxxxx），
 * 而从 GitHub 上开源出来的模组一般是标准的 behavior_pack / resource_pack，两种都得认。
 * 一个项目里同时存在两份时，以有 manifest.json 的那份为准 ——
 * 空壳目录（只有 entities 之类）不该把真正的内容挡在后面。
 */
public final class StudioConfig {

    public static final String FILE = "studio.json";

    /** 从物品 / 方块 / 配方的 identifier 里取命名空间前缀。 */
    private static final Pattern IDENTIFIER =
            Pattern.compile("\"identifier\"\\s*:\\s*\"([a-zA-Z0-9_.\\-]+):");

    /** 推断命名空间时最多看这么多文件和这么大的文件，别在超大工程上卡住。 */
    private static final int MAX_SCAN_FILES = 600;
    private static final long MAX_SCAN_BYTES = 1024L * 1024L;

    private StudioConfig() {
    }

    // ==================== 目录判断 ====================

    /** 是不是行为包目录（带不带随机后缀都算）。 */
    public static boolean isBehaviorPack(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.equals("behavior_pack") || n.equals("behaviour_pack")
                || n.startsWith("behavior_pack_") || n.startsWith("behaviour_pack_");
    }

    /** 是不是资源包目录。 */
    public static boolean isResourcePack(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.equals("resource_pack") || n.startsWith("resource_pack_");
    }

    /** 目录名是完全不带后缀的标准名。 */
    private static boolean isPlainName(String name) {
        String n = name.toLowerCase();
        return n.equals("behavior_pack") || n.equals("behaviour_pack") || n.equals("resource_pack");
    }

    /**
     * 找项目的包目录。
     *
     * 优先有 manifest.json 的（那才是真正在用的包），同一档里优先不带后缀的标准名，
     * 再按名字排序，保证每次拿到的都是同一个目录。
     *
     * @param behavior true 找行为包，false 找资源包
     * @return 找不到返回 null
     */
    public static File findPack(File projectDir, boolean behavior) {
        List<File> list = packs(projectDir, behavior);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 所有匹配的包目录，已按「有 manifest → 标准名 → 名字」排好序。 */
    public static List<File> packs(File projectDir, boolean behavior) {
        List<File> out = new ArrayList<>();
        if (projectDir == null) return out;
        File[] entries = projectDir.listFiles();
        if (entries == null) return out;
        for (File f : entries) {
            if (!f.isDirectory()) continue;
            String name = f.getName();
            if (behavior ? isBehaviorPack(name) : isResourcePack(name)) out.add(f);
        }
        java.util.Collections.sort(out, new java.util.Comparator<File>() {
            @Override public int compare(File a, File b) {
                int sa = score(a);
                int sb = score(b);
                if (sa != sb) return sb - sa;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return out;
    }

    private static int score(File dir) {
        int s = 0;
        if (new File(dir, "manifest.json").isFile()) s += 2;
        if (isPlainName(dir.getName())) s += 1;
        return s;
    }

    // ==================== studio.json ====================

    public static File studioFile(File projectDir) {
        return new File(projectDir, FILE);
    }

    public static boolean hasStudio(File projectDir) {
        File f = studioFile(projectDir);
        return f.isFile() && f.length() > 0;
    }

    /**
     * 猜命名空间。
     *
     * 从行为包里物品 / 方块 / 配方 json 的 identifier 前缀统计，取出现最多的那个。
     * 开源仓库里没有 studio.json，但 identifier 里一定带着作者用的命名空间。
     *
     * @return 猜不出来返回 null
     */
    public static String guessNamespace(File projectDir) {
        if (projectDir == null) return null;
        Map<String, Integer> counter = new LinkedHashMap<>();
        int[] budget = {MAX_SCAN_FILES};
        // 行为包里的 identifier 最全，先扫它；没有再看资源包
        List<File> dirs = new ArrayList<>();
        dirs.addAll(packs(projectDir, true));
        dirs.addAll(packs(projectDir, false));
        for (File dir : dirs) {
            scanIdentifiers(dir, counter, budget, 0);
            if (!counter.isEmpty()) break;
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : counter.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    private static void scanIdentifiers(File dir, Map<String, Integer> counter,
                                        int[] budget, int depth) {
        if (dir == null || depth > 6 || budget[0] <= 0) return;
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File f : entries) {
            if (budget[0] <= 0) return;
            String name = f.getName();
            if (f.isDirectory()) {
                if (name.equals(GitRepo.GIT_DIR) || name.equals(".mcs")) continue;
                scanIdentifiers(f, counter, budget, depth + 1);
            } else if (name.toLowerCase().endsWith(".json") && f.length() <= MAX_SCAN_BYTES) {
                budget[0]--;
                String text = readQuietly(f);
                if (text == null) continue;
                Matcher m = IDENTIFIER.matcher(text);
                while (m.find()) {
                    String ns = m.group(1);
                    Integer old = counter.get(ns);
                    counter.put(ns, old == null ? 1 : old + 1);
                }
            }
        }
    }

    private static String readQuietly(File f) {
        try {
            return com.xtt.mcmodmaker.util.FileUtils.readText(f);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 补一份 studio.json。
     *
     * @param name      项目名，空则用目录名
     * @param namespace 命名空间，空则不写这个字段（读的时候会回退成 test）
     */
    public static File writeStudio(File projectDir, String name, String namespace) throws IOException {
        String projectName = name == null || name.trim().isEmpty()
                ? projectDir.getName() : name.trim();
        String ns = namespace == null ? "" : namespace.trim();
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", defaultProjectId(projectDir));
            obj.put("name", projectName);
            if (!ns.isEmpty()) obj.put("namespace", ns);
            File file = studioFile(projectDir);
            com.xtt.mcmodmaker.util.FileUtils.writeText(file, obj.toString(2));
            return file;
        } catch (org.json.JSONException e) {
            throw new IOException("写 studio.json 失败", e);
        }
    }

    /**
     * 项目的 id。
     *
     * 应用自己建的项目目录名就是去掉横线的 UUID，直接沿用；
     * 从 GitHub 克隆下来的目录名是仓库名，只能新生成一个。
     */
    public static String defaultProjectId(File projectDir) {
        if (projectDir == null) return UUID.randomUUID().toString().replace("-", "");
        String raw = projectDir.getName();
        if (raw.matches("[0-9a-fA-F]{32}")) return raw.toLowerCase();
        String stripped = raw.replace("-", "");
        if (stripped.matches("[0-9a-fA-F]{32}")) return stripped.toLowerCase();
        return UUID.randomUUID().toString().replace("-", "");
    }
}
