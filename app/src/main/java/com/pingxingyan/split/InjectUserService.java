package com.pingxingyan.split;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;

/**
 * 运行在 Shizuku 启动的 shell(或 root) 进程中的 UserService：通过反射调用隐藏的
 * InputManager(Global).injectInputEvent 实时注入触摸事件。
 */
public class InjectUserService extends IInjector.Stub {
    private Object im;
    private Method inject;

    public InjectUserService() {
        try {
            Class<?> c = Class.forName("android.hardware.input.InputManagerGlobal"); // Android 14+
            im = c.getMethod("getInstance").invoke(null);
            inject = c.getMethod("injectInputEvent", InputEvent.class, int.class);
        } catch (Throwable ignored) { }
        if (inject == null) {
            try {
                Class<?> c = android.hardware.input.InputManager.class;
                im = c.getMethod("getInstance").invoke(null);
                inject = c.getMethod("injectInputEvent", InputEvent.class, int.class);
            } catch (Throwable ignored) { }
        }
    }

    @Override
    public boolean injectTouch(int action, float x, float y, long downTime, long eventTime) {
        if (inject == null) return false;
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords()};
        pc[0].x = x;
        pc[0].y = y;
        pc[0].pressure = action == MotionEvent.ACTION_UP ? 0f : 1f;
        pc[0].size = 1f;
        MotionEvent ev = MotionEvent.obtain(downTime > 0 ? downTime : now, eventTime > 0 ? eventTime : now,
                action, 1, pp, pc, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            Object r = inject.invoke(im, ev, 0 /* INJECT_INPUT_EVENT_MODE_ASYNC */);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            return false;
        } finally {
            ev.recycle();
        }
    }

    @Override
    public boolean injectMulti(int action, int index, int[] ids, float[] xs, float[] ys, long downTime) {
        if (inject == null || ids == null || ids.length == 0) return false;
        int n = ids.length;
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] pc = new MotionEvent.PointerCoords[n];
        for (int k = 0; k < n; k++) {
            pp[k] = new MotionEvent.PointerProperties();
            pp[k].id = ids[k];
            pp[k].toolType = MotionEvent.TOOL_TYPE_FINGER;
            pc[k] = new MotionEvent.PointerCoords();
            pc[k].x = xs[k];
            pc[k].y = ys[k];
            pc[k].pressure = 1f;
            pc[k].size = 1f;
        }
        int a = action;
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            a = action | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        }
        MotionEvent ev = MotionEvent.obtain(downTime > 0 ? downTime : now, now, a, n, pp, pc, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            Object r = inject.invoke(im, ev, 0);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            return false;
        } finally {
            ev.recycle();
        }
    }

    @Override
    public boolean setSkipScreenshot(android.view.SurfaceControl sc, boolean skip) {
        try {
            boolean ok = SkipCapture.applyLocal(sc, skip);
            sc.release();
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
