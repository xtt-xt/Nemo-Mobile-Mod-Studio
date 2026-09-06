package com.xtt.mcmodmaker.util;

import android.content.Context;

import com.xtt.mcmodmaker.core.Constants;
import com.xtt.mcmodmaker.LogUtil;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 应用设置存储：保存到 /storage/emulated/0/McMod/setting.json。
 * 提供与 SharedPreferences 类似的 get/put/remove 接口（String/Boolean/Int/Long）。
 */
public final class SettingsManager {

    private static SettingsManager instance;

    private final File file;
    private JSONObject cache = new JSONObject();

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context.getApplicationContext());
        }
        return instance;
    }

    private SettingsManager(Context context) {
        file = new File(Constants.SETTING_FILE);
        load();
        // 首次运行：从旧 SharedPreferences 迁移已有设置（cookie/email/更新检查等）
        if (!file.exists()) {
            migrateFromSharedPreferences(context);
        }
    }

    /** 将旧版 SharedPreferences(mcmod_prefs) 中的数据迁移到 setting.json。 */
    private void migrateFromSharedPreferences(Context ctx) {
        try {
            android.content.SharedPreferences old =
                    ctx.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            if (old == null) return;
            java.util.Map<String, ?> all = old.getAll();
            if (all == null || all.isEmpty()) return;
            boolean changed = false;
            for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
                Object v = e.getValue();
                try {
                    if (v instanceof String) cache.put(e.getKey(), (String) v);
                    else if (v instanceof Boolean) cache.put(e.getKey(), (Boolean) v);
                    else if (v instanceof Integer) cache.put(e.getKey(), (Integer) v);
                    else if (v instanceof Long) cache.put(e.getKey(), (Long) v);
                    else continue;
                    changed = true;
                } catch (Exception ignored) {
                }
            }
            if (changed) save();
        } catch (Exception e) {
            LogUtil.logException(e);
        }
    }

    private synchronized void load() {
        try {
            File src = file;
            File legacy = new File(Constants.MOD_FOLDER_PATH + "/setting.json");
            // 新文件不存在但旧文件 setting.json 存在时，从旧文件读取并迁移
            if (!file.exists() && legacy.exists()) {
                src = legacy;
            }
            if (src.exists()) {
                byte[] bytes = new byte[(int) src.length()];
                FileInputStream fis = new FileInputStream(src);
                try {
                    int off = 0;
                    while (off < bytes.length) {
                        int r = fis.read(bytes, off, bytes.length - off);
                        if (r < 0) break;
                        off += r;
                    }
                } finally {
                    fis.close();
                }
                String text = new String(bytes, StandardCharsets.UTF_8).trim();
                if (!text.isEmpty()) {
                    cache = new JSONObject(text);
                }
                if (src != file) {
                    // 迁移到新文件名并删除旧文件
                    save();
                    legacy.delete();
                }
            }
        } catch (Exception e) {
            LogUtil.logException(e);
            cache = new JSONObject();
        }
    }

    private synchronized void save() {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            FileOutputStream fos = new FileOutputStream(file);
            try {
                fos.write(cache.toString(2).getBytes(StandardCharsets.UTF_8));
                fos.flush();
            } finally {
                fos.close();
            }
        } catch (Exception e) {
            LogUtil.logException(e);
        }
    }

    public synchronized String getString(String key, String def) {
        return cache.optString(key, def);
    }

    public synchronized boolean getBoolean(String key, boolean def) {
        return cache.optBoolean(key, def);
    }

    public synchronized int getInt(String key, int def) {
        return cache.optInt(key, def);
    }

    public synchronized long getLong(String key, long def) {
        return cache.optLong(key, def);
    }

    public synchronized boolean contains(String key) {
        return cache.has(key);
    }

    public synchronized void putString(String key, String value) {
        try {
            cache.put(key, value == null ? JSONObject.NULL : value);
        } catch (Exception e) {
            LogUtil.logException(e);
        }
        save();
    }

    public synchronized void putBoolean(String key, boolean value) {
        try {
            cache.put(key, value);
        } catch (Exception e) {
            LogUtil.logException(e);
        }
        save();
    }

    public synchronized void putInt(String key, int value) {
        try {
            cache.put(key, value);
        } catch (Exception e) {
            LogUtil.logException(e);
        }
        save();
    }

    public synchronized void putLong(String key, long value) {
        try {
            cache.put(key, value);
        } catch (Exception e) {
            LogUtil.logException(e);
        }
        save();
    }

    public synchronized void remove(String key) {
        cache.remove(key);
        save();
    }

    /** 文件是否存在（用于判断是否有持久化设置）。 */
    public synchronized boolean fileExists() {
        return file.exists();
    }
}