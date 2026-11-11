package com.pingxingyan.split;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * Android 14/15 录屏会把输入法、通知涂黑（屏幕共享保护）。
 * 开发者选项「停用屏幕共享保护」对应的全局设置是
 * disable_screen_share_protections_for_apps_and_notifications。
 * 普通应用写不了这个值；已授权 Shizuku 时，分屏会话期间临时写成 1，停止后恢复原值。
 * 崩溃后下次打开应用也会恢复，避免把保护一直关掉。
 */
public final class ScreenShareGuard {
    private static final String TAG = "ParallelEyeSsp";
    static final String KEY = "disable_screen_share_protections_for_apps_and_notifications";
    static final String K_PREV = "ssp_prev";
    static final String K_OURS = "ssp_ours";

    private ScreenShareGuard() {}

    public static boolean supported() { return Build.VERSION.SDK_INT >= 34; }

    private static ShizukuHelper helper(Context c) {
        if (!supported()) return null;
        try {
            if (!ShizukuHelper.binderAlive()) return null;
        } catch (Throwable t) {
            return null;
        }
        ShizukuHelper h = ShizukuHelper.peek();
        if (h == null) h = ShizukuHelper.get(c);
        return h.granted() ? h : null;
    }

    /** 临时关闭屏幕共享保护。未授权 Shizuku 时返回 false，不改系统设置。 */
    public static boolean lift(Context c) {
        ShizukuHelper h = helper(c);
        if (h == null) return false;
        SharedPreferences p = Prefs.get(c);
        if (!p.getBoolean(K_OURS, false)) {
            String cur = h.execOut("/system/bin/settings", "get", "global", KEY);
            if (cur == null) {
                Log.w(TAG, "settings get failed");
                return false;
            }
            p.edit().putString(K_PREV, cur.trim()).putBoolean(K_OURS, true).commit();
        }
        String put = h.execOut("/system/bin/settings", "put", "global", KEY, "1");
        if (put == null) {
            Log.w(TAG, "settings put failed");
            return false;
        }
        return true;
    }

    /** 若本应用改过该设置，则恢复。未授权或命令失败时保留标记，下次再试。 */
    public static void restore(Context c) {
        if (!supported()) return;
        SharedPreferences p = Prefs.get(c);
        if (!p.getBoolean(K_OURS, false)) return;
        ShizukuHelper h = helper(c);
        if (h == null) return;
        String prev = p.getString(K_PREV, "null");
        String result;
        if (prev == null || prev.isEmpty() || "null".equals(prev)) {
            result = h.execOut("/system/bin/settings", "delete", "global", KEY);
        } else {
            result = h.execOut("/system/bin/settings", "put", "global", KEY, prev);
        }
        if (result == null) {
            Log.w(TAG, "restore failed, will retry");
            return;
        }
        p.edit().putBoolean(K_OURS, false).remove(K_PREV).commit();
    }
}
