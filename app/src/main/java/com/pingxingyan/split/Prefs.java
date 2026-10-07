package com.pingxingyan.split;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.DisplayMetrics;

/** 参数持久化（SharedPreferences）。 */
public final class Prefs {
    public static final String NAME = "parallel_eye";
    public static final String K_SEP_MM = "sep_mm";          // 两个画面中心（两个红点）间距，毫米
    public static final String K_SIZE_PCT = "size_pct";      // 画面大小，百分比
    public static final String K_OFFSET_MM = "offset_mm";    // 垂直偏移，毫米（正数向下）
    public static final String K_MODE = "mode";              // 0=实时 1=间歇刷新
    public static final String K_INTERVAL_MS = "interval_ms";
    public static final String K_CONTROL_PANE = "control_pane"; // 可操控半屏 0关 1左 2右
    public static final String K_FIT_MODE = "fit_mode";         // 0=填满 1=完整显示
    public static final String K_SHOW_DOTS = "show_dots";
    public static final String K_SPLIT_ORIENT = "split_orient"; // 0=强制横屏 1=跟随系统方向
    public static final String K_LAND_DIR = "land_dir";         // 0=自动 1=向左 2=向右
    public static final String K_GESTURES = "gestures";        // 快捷手势开关
    public static final String K_CONTROL_SIDE = "control_side"; // 可操控半屏关闭时记住的一侧 1左 2右
    public static final String K_REALTIME = "realtime";        // 实时操控（Shizuku）
    public static final String K_QUALITY = "quality";          // 画质 0高 1标准 2省电
    public static final String K_SHOW_FPS = "show_fps";
    public static final int Q_HIGH = 0, Q_STD = 1, Q_SAVER = 2;
    public static final String K_SCOPE = "capture_scope";      // 0=强制整个屏幕 1=在系统弹窗中选择（默认）
    public static final int SCOPE_DISPLAY = 0, SCOPE_APP = 1;
    public static final String K_GPU = "gpu_direct";          // 高画质下 GPU 直接绘制（默认关）
    public static final String K_BUBBLE_X = "bubble_x";
    public static final String K_BUBBLE_Y = "bubble_y";

    public static final float DEF_SEP_MM = 60f;
    public static final float MIN_SEP_MM = 20f;
    public static final float MAX_SEP_MM = 120f;
    public static final int DEF_SIZE_PCT = 100;
    public static final int ORIENT_FORCE_LANDSCAPE = 0;
    public static final int ORIENT_FOLLOW = 1;
    public static final int DIR_AUTO = 0;
    public static final int DIR_LEFT = 1;   // 手机逆时针转（顶部朝左）
    public static final int DIR_RIGHT = 2;  // 手机顺时针转（顶部朝右）
    public static final int FIT_FILL = 0;
    public static final int FIT_CONTAIN = 1;
    public static final int MIN_SIZE_PCT = 30;
    public static final int MAX_SIZE_PCT = 200;
    public static final int CONTROL_OFF = 0;
    public static final int CONTROL_LEFT = 1;
    public static final int CONTROL_RIGHT = 2;
    public static final float DEF_OFFSET_MM = 0f;
    public static final float MAX_OFFSET_MM = 30f;
    public static final int MODE_LIVE = 0;
    public static final int MODE_INTERVAL = 1;
    public static final int DEF_INTERVAL_MS = 1000;
    public static final int MIN_INTERVAL_MS = 300;
    public static final int MAX_INTERVAL_MS = 5000;

    private Prefs() {}

    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** Android 15+ 普遍支持“单个应用”共享，默认实时；更早版本默认间歇刷新。 */
    public static int defaultMode() {
        return Build.VERSION.SDK_INT >= 35 ? MODE_LIVE : MODE_INTERVAL;
    }

    public static float sepMm(SharedPreferences p) { return p.getFloat(K_SEP_MM, DEF_SEP_MM); }
    public static int sizePct(SharedPreferences p) { return p.getInt(K_SIZE_PCT, DEF_SIZE_PCT); }
    public static float offsetMm(SharedPreferences p) { return p.getFloat(K_OFFSET_MM, DEF_OFFSET_MM); }
    public static int mode(SharedPreferences p) { return p.getInt(K_MODE, defaultMode()); }
    public static int controlPane(SharedPreferences p) { return p.getInt(K_CONTROL_PANE, CONTROL_OFF); }
    public static boolean gestures(SharedPreferences p) { return p.getBoolean(K_GESTURES, true); }
    public static int quality(SharedPreferences p) { return p.getInt(K_QUALITY, Q_HIGH); }
    public static boolean showFps(SharedPreferences p) { return p.getBoolean(K_SHOW_FPS, false); }
    public static int scope(SharedPreferences p) { return p.getInt(K_SCOPE, SCOPE_APP); }
    public static final String K_RES = "res_pct";     // 分辨率 100/75/50
    public static final String K_FPS = "fps_cap";     // 帧率 0=跟随屏幕 60 30
    /** 未设置时从 v1.5 的“画质”迁移。 */
    public static int resPct(SharedPreferences p) {
        if (p.contains(K_RES)) return p.getInt(K_RES, 100);
        int q = quality(p);
        return q == Q_SAVER ? 50 : q == Q_STD ? 75 : 100;
    }
    public static int fpsCap(SharedPreferences p) {
        if (p.contains(K_FPS)) return p.getInt(K_FPS, 0);
        int q = quality(p);
        return q == Q_SAVER ? 30 : q == Q_STD ? 60 : 0;
    }
    public static boolean gpu(SharedPreferences p) { return p.getBoolean(K_GPU, false); }
    public static boolean realtime(SharedPreferences p) { return p.getBoolean(K_REALTIME, false); }
    public static int controlSide(SharedPreferences p) {
        int c = controlPane(p);
        if (c != CONTROL_OFF) return c;
        return p.getInt(K_CONTROL_SIDE, CONTROL_RIGHT) == CONTROL_LEFT ? CONTROL_LEFT : CONTROL_RIGHT;
    }
    public static int splitOrient(SharedPreferences p) { return p.getInt(K_SPLIT_ORIENT, ORIENT_FORCE_LANDSCAPE); }
    public static int landDir(SharedPreferences p) { return p.getInt(K_LAND_DIR, DIR_AUTO); }
    public static int fitMode(SharedPreferences p) { return p.getInt(K_FIT_MODE, FIT_FILL); }
    public static boolean showDots(SharedPreferences p) { return p.getBoolean(K_SHOW_DOTS, true); }
    public static int intervalMs(SharedPreferences p) { return p.getInt(K_INTERVAL_MS, DEF_INTERVAL_MS); }

    public static void reset(SharedPreferences p) {
        p.edit().putFloat(K_SEP_MM, DEF_SEP_MM).putInt(K_SIZE_PCT, DEF_SIZE_PCT)
                .putFloat(K_OFFSET_MM, DEF_OFFSET_MM).putInt(K_MODE, defaultMode())
                .putInt(K_INTERVAL_MS, DEF_INTERVAL_MS).putInt(K_FIT_MODE, FIT_FILL)
                .putBoolean(K_SHOW_DOTS, true).putInt(K_SPLIT_ORIENT, ORIENT_FORCE_LANDSCAPE)
                .putInt(K_LAND_DIR, DIR_AUTO).apply();
    }

    /** 默认屏幕当前方向下的真实像素尺寸（含系统栏）。 */
    @SuppressWarnings("deprecation")
    public static android.graphics.Point realDisplaySize(Context c) {
        android.hardware.display.DisplayManager dm = c.getSystemService(android.hardware.display.DisplayManager.class);
        android.view.Display d = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY);
        android.graphics.Point p = new android.graphics.Point();
        d.getRealSize(p);
        return p;
    }

    /**
     * 每毫米像素数。优先使用物理 DPI（xdpi/ydpi 平均），若厂商上报值明显异常则退回 densityDpi。
     */
    public static float pxPerMm(DisplayMetrics dm) {
        float dpi = (dm.xdpi + dm.ydpi) / 2f;
        float ref = dm.densityDpi;
        if (!(dpi > 50f) || dpi < ref * 0.6f || dpi > ref * 1.6f) {
            dpi = ref;
        }
        return dpi / 25.4f;
    }
}
