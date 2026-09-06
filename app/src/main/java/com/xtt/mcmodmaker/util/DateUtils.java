package com.xtt.mcmodmaker.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 日期时间格式化工具。 */
public final class DateUtils {

    private DateUtils() {
    }

    /** 将 Unix 秒时间戳格式化为 "yyyy-MM-dd HH:mm"。 */
    public static String formatSeconds(long seconds) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(seconds * 1000L));
    }

    /** 将毫秒时间戳格式化为 "yyyy-MM-dd HH:mm"。 */
    public static String formatMillis(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(millis));
    }

    /** 今天的日期字符串 "yyyy-MM-dd"。 */
    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}