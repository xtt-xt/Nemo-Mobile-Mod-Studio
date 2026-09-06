package com.xtt.mcmodmaker;

import android.os.Environment;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 仅记录异常/崩溃信息；日志目录为 /storage/emulated/0/McMod/log。 */
public final class LogUtil {
    private static final File LOG_DIR = new File(
            Environment.getExternalStorageDirectory(), "McMod/log");
    private static final File LOG_FILE = new File(LOG_DIR, "mcmodmaker_crash.log");

    private LogUtil() { }

    public static void log(String msg) {
        try {
            if (!LOG_DIR.exists() && !LOG_DIR.mkdirs()) return;
            if (!LOG_FILE.exists() && !LOG_FILE.createNewFile()) return;
            BufferedWriter bw = new BufferedWriter(new FileWriter(LOG_FILE, true));
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());
            bw.write("[" + time + "] " + msg);
            bw.newLine();
            bw.close();
        } catch (IOException ignored) {
            // 无存储权限或目录不可写时绝不影响应用正常运行。
        }
    }

    public static void logException(Throwable e) {
        if (e == null) return;
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.close();
        log(sw.toString());
    }
}
