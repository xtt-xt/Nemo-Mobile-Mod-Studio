package com.xtt.mcmodmaker.util;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/**
 * JSON 读写与 studio.json 字段解析工具。
 * 兼容旧版字段命名（name/EditName、namespace/NameSpace、id/Id）。
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /** 读取文件为 JSONObject，失败返回 null。 */
    public static JSONObject readJson(File file) {
        if (file == null || !file.exists() || file.length() <= 0) return null;
        try {
            return new JSONObject(FileUtils.readText(file));
        } catch (Exception e) {
            return null;
        }
    }

    /** 将 JSONObject 写回文件（缩进 2 空格）。 */
    public static void writeJson(File file, JSONObject obj) throws IOException {
        try {
            FileUtils.writeText(file, obj.toString(2));
        } catch (JSONException e) {
            throw new IOException("JSON 序列化失败", e);
        }
    }

    /** 从 studio.json 读取字符串字段，支持多个候选 key（按顺序取第一个存在的）。 */
    public static String getStudioString(File projectDir, String... keys) {
        JSONObject obj = readJson(new File(projectDir, "studio.json"));
        if (obj == null) return null;
        for (String key : keys) {
            if (obj.has(key)) return obj.optString(key, "");
        }
        return null;
    }

    /** 读取项目名称，缺失时回退为目录名。 */
    public static String getProjectName(File projectDir) {
        String name = getStudioString(projectDir, "name", "EditName");
        return (name == null || name.isEmpty()) ? projectDir.getName() : name;
    }

    /** 读取项目 ID，缺失时回退为目录名。 */
    public static String getProjectId(File projectDir) {
        String id = getStudioString(projectDir, "id", "Id");
        return (id == null || id.isEmpty()) ? projectDir.getName() : id;
    }

    /** 读取项目命名空间，缺失时回退为 "test"。 */
    public static String getProjectNamespace(File projectDir) {
        String ns = getStudioString(projectDir, "namespace", "NameSpace");
        return (ns == null || ns.isEmpty()) ? "test" : ns;
    }

    /** 更新 studio.json 中指定字符串字段（保持其他字段不变）。 */
    public static boolean updateStudioField(File projectDir, String key, String value) {
        File studioFile = new File(projectDir, "studio.json");
        JSONObject obj = readJson(studioFile);
        if (obj == null) return false;
        try {
            obj.put(key, value);
            FileUtils.writeTextQuietly(studioFile, obj.toString(2));
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    // ==================== 最后编辑时间 ====================

    /** 项目创建者在 studio.json 里留的名字。 */
    public static final String CREATOR = "Nemo Mobile Mod Studio";
    /** 最后编辑时间的字段名。 */
    public static final String KEY_UPDATED_AT = "updated_at";
    /** 创建者字段名。 */
    public static final String KEY_CREATED_BY = "created_by";

    /**
     * studio.json 里记的最后编辑时间（毫秒）。
     *
     * 没这个字段（老项目）或解析失败返回 -1，调用方自己决定退回什么。
     * 字段存的是 ISO 8601 带时区的字符串，直接打开文件也看得懂。
     */
    public static long getStudioUpdatedAt(File projectDir) {
        String raw = getStudioString(projectDir, KEY_UPDATED_AT);
        return parseIso(raw);
    }

    /**
     * 把 studio.json 的最后编辑时间改成现在。
     *
     * 文件不存在时什么也不做 —— 补 studio.json 是另一件事，不该在这里顺手造一个。
     */
    public static boolean touchStudioUpdatedAt(File projectDir) {
        return setStudioUpdatedAt(projectDir, System.currentTimeMillis());
    }

    /** 指定时间写进 studio.json；文件不存在时返回 false。 */
    public static boolean setStudioUpdatedAt(File projectDir, long millis) {
        File studioFile = new File(projectDir, "studio.json");
        JSONObject obj = readJson(studioFile);
        if (obj == null) return false;
        try {
            obj.put(KEY_UPDATED_AT, formatIso(millis));
            if (!obj.has(KEY_CREATED_BY)) obj.put(KEY_CREATED_BY, CREATOR);
            FileUtils.writeTextQuietly(studioFile, obj.toString(2));
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /** 现在的时间，ISO 8601 带时区，形如 2026-11-10T20:44:00+08:00。 */
    public static String nowIso() {
        return formatIso(System.currentTimeMillis());
    }

    public static String formatIso(long millis) {
        try {
            return java.time.OffsetDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(millis),
                            java.time.ZoneId.systemDefault())
                    .withNano(0)
                    .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            // desugar 之外的老设备兜底：退成不带时区的本地时间
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            return f.format(new java.util.Date(millis));
        }
    }

    /**
     * 解析 ISO 8601 时间，兼容带小数秒、带 Z、不带时区几种写法。
     *
     * @return 毫秒；解析不了返回 -1
     */
    public static long parseIso(String raw) {
        if (raw == null || raw.trim().isEmpty()) return -1;
        String s = raw.trim();
        try {
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            // 不带时区的，按本机时区算
            return java.time.LocalDateTime.parse(s)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        // 最后兜底：能当纯数字就当毫秒时间戳
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return -1;
        }
    }
}