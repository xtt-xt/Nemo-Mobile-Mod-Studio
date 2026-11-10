package com.xtt.mcmodmaker.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.xtt.mcmodmaker.util.SettingsManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.eclipse.jgit.api.errors.GitAPIException;

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

    /**
     * 记一次改动。
     *
     * 只有真的有改动才提交，提交之前顺带把 studio.json 的最后编辑时间刷成现在，
     * 于是这次改动和它的时间一起进同一条记录，主页读这个时间就行。
     * 什么都没改时（只是翻了一眼配方就退出）不动 studio.json，历史里也不会多一条。
     *
     * @return 新提交；没有改动时返回 null
     */
    public static GitRepo.CommitInfo commit(Context context, File projectDir, String message)
            throws IOException, GitAPIException {
        GitRepo repo = repo(context, projectDir);
        repo.ensureInit("初始提交");
        if (!repo.hasChanges()) return null;
        com.xtt.mcmodmaker.util.JsonUtils.touchStudioUpdatedAt(projectDir);
        return repo.commitIfChanged(message);
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
