package com.xtt.mcmodmaker.core;

import android.os.Handler;
import android.os.Looper;

import com.xtt.mcmodmaker.util.FileUtils;

import org.json.JSONObject;

import java.io.File;

/**
 * 配方文件与模型之间的桥梁，负责把保存从「每敲一个字符写一次盘」改成
 * 「改完停手 400ms 再写一次」。
 *
 * 加载模型期间用 {@link #suspend()} 挂起，避免界面初始化的 setText
 * 反过来触发一次重写把文件覆盖坏。
 */
public final class RecipeStore {

    public interface Listener {
        /** 保存失败时回调，参数是给用户看的说明。 */
        void onSaveFailed(String message);
    }

    private static final long DEBOUNCE_MS = 400;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private File file;
    private RecipeModel model;
    private boolean dirty;
    private boolean suspended = true;
    private Listener listener;

    private final Runnable pendingSave = new Runnable() {
        @Override
        public void run() {
            flush();
        }
    };

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void attach(File file, RecipeModel model) {
        MAIN.removeCallbacks(pendingSave);
        this.file = file;
        this.model = model;
        this.dirty = false;
        this.suspended = true;
    }

    public File file() {
        return file;
    }

    public RecipeModel model() {
        return model;
    }

    /** 加载完成，允许此后保存。 */
    public void resume() {
        suspended = false;
    }

    public void suspend() {
        MAIN.removeCallbacks(pendingSave);
        suspended = true;
    }

    public boolean hasUnsaved() {
        return dirty;
    }

    /** 模型被改过：安排一次延迟保存。 */
    public void markDirty() {
        if (suspended || file == null || model == null) return;
        dirty = true;
        MAIN.removeCallbacks(pendingSave);
        MAIN.postDelayed(pendingSave, DEBOUNCE_MS);
    }

    /** 立刻写入磁盘（切换文件、离开页面时调用）。 */
    public boolean flush() {
        MAIN.removeCallbacks(pendingSave);
        if (!dirty || file == null || model == null) return true;
        try {
            JSONObject json = model.toJson();
            FileUtils.writeText(file, json.toString(2));
            dirty = false;
            return true;
        } catch (Exception e) {
            if (listener != null) listener.onSaveFailed(String.valueOf(e.getMessage()));
            return false;
        }
    }

    /** 放弃未落盘的改动（重置按钮用）。 */
    public void discard() {
        MAIN.removeCallbacks(pendingSave);
        dirty = false;
    }
}
