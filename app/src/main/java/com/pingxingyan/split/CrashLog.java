package com.pingxingyan.split;

import android.content.Context;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 记录最近一次未捕获异常（便于排查“关闭分屏后整个应用退出”之类的问题），在设置页“关于与调试”中显示。 */
public final class CrashLog {
    public static final String K_LAST = "last_crash";
    private static boolean installed;

    private CrashLog() {}

    public static synchronized void install(Context c) {
        if (installed) return;
        installed = true;
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            record(app, "崩溃", e);
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    public static void record(Context c, String what, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String s = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(new Date()) + " " + what + "（v"
                    + BuildConfig.VERSION_NAME + "）\n" + sw;
            if (s.length() > 3000) s = s.substring(0, 3000);
            Prefs.get(c).edit().putString(K_LAST, s).commit();
        } catch (Throwable ignored) { }
    }
}
