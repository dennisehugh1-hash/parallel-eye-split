package com.pingxingyan.split;

import android.os.Build;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.View;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

/**
 * 防套娃：给分屏层/悬浮按钮的窗口图层设置 SurfaceFlinger 的 SKIP_SCREENSHOT 标志（Android 12+ 隐藏 API）。
 * 带此标志的图层只显示在手机屏幕上，不会出现在截屏、录屏和镜像显示（包括 MediaProjection 的虚拟显示）里，
 * 于是可以实时录“整个屏幕”（含输入法、系统弹窗）而不把分屏层自己录进去。
 * 先在本应用进程尝试，失败再通过 Shizuku（shell 身份）设置。
 */
public final class SkipCapture {
    private static final String TAG = "ParallelEyeSkip";
    public static final int R_NONE = 0, R_LOCAL = 1, R_SHIZUKU = 2, R_FAIL = 3;

    private SkipCapture() {}

    public static boolean supported() { return Build.VERSION.SDK_INT >= 31; }

    /** 视图所在窗口的根 SurfaceControl（ViewRootImpl.getSurfaceControl，隐藏 API）。 */
    static SurfaceControl rootSurface(View v) {
        if (!supported()) return null;
        try {
            Object vri = v.getRootSurfaceControl(); // 实际为 ViewRootImpl
            if (vri == null) return null;
            Object sc = HiddenApiBypass.invoke(vri.getClass(), vri, "getSurfaceControl");
            if (sc instanceof SurfaceControl && ((SurfaceControl) sc).isValid()) return (SurfaceControl) sc;
        } catch (Throwable t) {
            Log.w(TAG, "rootSurface failed", t);
        }
        return null;
    }

    /** 在当前进程设置标志（shell 进程中同样走这里）。 */
    static boolean applyLocal(SurfaceControl sc, boolean skip) {
        try {
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            HiddenApiBypass.invoke(SurfaceControl.Transaction.class, t, "setSkipScreenshot", sc, skip);
            t.apply();
            t.close();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "applyLocal failed", t);
            return false;
        }
    }

    public static int apply(View v, ShizukuHelper sh) {
        if (!supported()) return R_FAIL;
        SurfaceControl sc = rootSurface(v);
        if (sc == null) return R_FAIL;
        if (sh != null && sh.isReady() && sh.setSkipScreenshot(sc, true)) return R_SHIZUKU;
        if (applyLocal(sc, true)) return R_LOCAL;
        return R_FAIL;
    }
}
