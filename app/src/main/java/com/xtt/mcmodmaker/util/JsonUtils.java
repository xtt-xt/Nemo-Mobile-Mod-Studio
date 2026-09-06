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
}