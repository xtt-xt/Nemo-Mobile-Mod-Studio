package com.xtt.mcmodmaker.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.xtt.mcmodmaker.util.SettingsManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 版本库的入口：提交身份从外部设置文件取，按项目目录拿到对应的 {@link GitRepo}。
 *
 * 身份只是显示用的名字和邮箱，放 /storage/emulated/0/McMod/setting.json 里，
 * 和别的设置一起走、方便自己改；令牌那种能操作账号的东西另存在应用内部目录（见 GitAuth）。
 */
public final class GitWorkspace {

    /** 自动保存用的提交说明。 */
    public static String autoMessage(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return "自动保存 " + f.format(new Date(millis));
    }

    public static String authorName(Context context) {
        migrateLegacyPrefs(context);
        String name = settings(context).getString(Constants.KEY_GIT_NAME, "");
        return name.trim().isEmpty() ? "Nemo 模组制作器" : name.trim();
    }

    public static String authorEmail(Context context) {
        migrateLegacyPrefs(context);
        String email = settings(context).getString(Constants.KEY_GIT_EMAIL, "");
        return email.trim().isEmpty() ? "nemo@local" : email.trim();
    }

    public static void setIdentity(Context context, String name, String email) {
        SettingsManager s = settings(context);
        s.putString(Constants.KEY_GIT_NAME, name == null ? "" : name.trim());
        s.putString(Constants.KEY_GIT_EMAIL, email == null ? "" : email.trim());
    }

    public static GitRepo repo(Context context, File projectDir) {
        return new GitRepo(projectDir, authorName(context), authorEmail(context));
    }

    private static SettingsManager settings(Context context) {
        return SettingsManager.getInstance(context);
    }

    private static boolean migrated;

    /**
     * 早先版本把身份写在 SharedPreferences 里，改成写 setting.json 后搬一次。
     * 只搬一次，之后以 setting.json 为准。
     */
    private static void migrateLegacyPrefs(Context context) {
        if (migrated) return;
        migrated = true;
        try {
            SharedPreferences old = context.getSharedPreferences(
                    Constants.PREFS_NAME, Context.MODE_PRIVATE);
            if (!old.contains(Constants.PREFS_GIT_NAME)
                    && !old.contains(Constants.PREFS_GIT_EMAIL)) {
                return;
            }
            SettingsManager s = settings(context);
            if (!s.contains(Constants.KEY_GIT_NAME) && old.contains(Constants.PREFS_GIT_NAME)) {
                s.putString(Constants.KEY_GIT_NAME, old.getString(Constants.PREFS_GIT_NAME, ""));
            }
            if (!s.contains(Constants.KEY_GIT_EMAIL) && old.contains(Constants.PREFS_GIT_EMAIL)) {
                s.putString(Constants.KEY_GIT_EMAIL, old.getString(Constants.PREFS_GIT_EMAIL, ""));
            }
            old.edit()
                    .remove(Constants.PREFS_GIT_NAME)
                    .remove(Constants.PREFS_GIT_EMAIL)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private GitWorkspace() {
    }
}
