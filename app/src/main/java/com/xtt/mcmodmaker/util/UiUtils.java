package com.xtt.mcmodmaker.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

/** UI 通用工具：主线程 Toast、间距占位、dp 换算。 */
public final class UiUtils {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UiUtils() {
    }

    /** 任意线程安全的 Toast（自动切主线程）。 */
    public static void toast(final Context context, final String msg) {
        if (context == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        } else {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /** 在主线程执行任务，当前已在主线程则直接执行。 */
    public static void runOnUiThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            MAIN.post(r);
        }
    }

    /** 在垂直 LinearLayout 中添加一个指定高度的占位间距。 */
    public static void addGap(Context context, LinearLayout parent, int height) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height));
        parent.addView(v);
    }

    /** dp 转 px。 */
    public static int dp(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}