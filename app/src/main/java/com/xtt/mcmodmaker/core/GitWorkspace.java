package com.xtt.mcmodmaker.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 版本库的入口：提交身份从设置里取，按项目目录拿到对应的 {@link GitRepo}。 */
public final class GitWorkspace {

    /** 自动保存用的提交说明。 */
    public static String autoMessage(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return "自动保存 " + f.format(new Date(millis));
    }

    public static String authorName(Context context) {
        return prefs(context).getString(Constants.PREFS_GIT_NAME, "Nemo 模组制作器");
    }

    public static String authorEmail(Context context) {
        return prefs(context).getString(Constants.PREFS_GIT_EMAIL, "nemo@local");
    }

    public static void setIdentity(Context context, String name, String email) {
        prefs(context).edit()
                .putString(Constants.PREFS_GIT_NAME, name == null ? "" : name.trim())
                .putString(Constants.PREFS_GIT_EMAIL, email == null ? "" : email.trim())
                .apply();
    }

    public static GitRepo repo(Context context, File projectDir) {
        return new GitRepo(projectDir, authorName(context), authorEmail(context));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private GitWorkspace() {
    }
}
