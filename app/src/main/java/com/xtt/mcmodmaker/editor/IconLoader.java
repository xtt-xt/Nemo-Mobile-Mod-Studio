package com.xtt.mcmodmaker.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.xtt.mcmodmaker.util.UiUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图标的异步加载。
 *
 * 渲染要读 png、做三次仿射变换，放主线程会让长列表卡顿，
 * 所以统一丢到两条后台线程上做，完成后回主线程填进 ImageView。
 * 具体画什么交给 {@link ItemIcons}：方块成立方体、物品用原版贴图、缺图给占位图。
 */
public final class IconLoader {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private IconLoader() {
    }

    /** 图标就绪回调，在主线程执行；一张图都没画出来时不会回调。 */
    public interface OnIcon {
        void onIcon(Bitmap bitmap);
    }

    /** 后台画图标并回调，供放不了 View 的地方用（例如按钮的 drawableTop）。 */
    public static void loadBitmap(final Context context, final String id,
                                  final File projectDir, final int sizePx,
                                  final OnIcon callback) {
        if (id == null || id.isEmpty() || callback == null) return;
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = ItemIcons.get(context, id, projectDir, sizePx);
                if (bmp == null) return;
                UiUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onIcon(bmp);
                    }
                });
            }
        });
    }

    /** 给 ImageView 加载图标，画不出来时保持原样。 */
    public static void load(final Context context, final String id,
                            final File projectDir, final int sizePx,
                            final ImageView target) {
        if (target == null) return;
        if (id == null || id.isEmpty()) {
            target.setTag(null);
            target.setImageDrawable(null);
            return;
        }
        // 打标记，列表复用同一个 View 时避免旧结果盖上去
        target.setTag(id);
        final WeakReference<ImageView> ref = new WeakReference<>(target);
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = ItemIcons.get(context, id, projectDir, sizePx);
                if (bmp == null) return;
                UiUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ImageView iv = ref.get();
                        if (iv == null) return;
                        // 期间这个 View 已经被别的条目占用了，丢弃这次结果
                        if (!id.equals(iv.getTag())) return;
                        iv.setImageBitmap(bmp);
                        iv.setVisibility(android.view.View.VISIBLE);
                    }
                });
            }
        });
    }

    public static void clearCaches() {
        ItemIcons.clearCache();
        BlockIconRenderer.clearCache();
        ModelRenderer.clearCache();
        com.xtt.mcmodmaker.core.BlockModels.clearCache();
        com.xtt.mcmodmaker.core.BlockTextureLib.clearCache();
    }
}
