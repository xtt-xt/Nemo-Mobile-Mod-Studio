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

public class LogUtil {

    private static final File LOG_FILE = new File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "mcmodmaker_crash.log"
    );

    public static void log(String msg) {
        try {
            if (!LOG_FILE.exists()) {
                LOG_FILE.createNewFile();
            }
            BufferedWriter bw = new BufferedWriter(new FileWriter(LOG_FILE, true));
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", 
                                               Locale.getDefault()).format(new Date());
            bw.write("[" + time + "] " + msg);
            bw.newLine();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void logException(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.close();
        log(sw.toString());
    }
}
