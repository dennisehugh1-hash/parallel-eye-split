package com.pingxingyan.split;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

import rikka.shizuku.Shizuku;

/** Shizuku 状态、授权与实时注入 UserService 的管理（单例，进程内共享）。 */
public final class ShizukuHelper {
    private static final String TAG = "ParallelEyeShizuku";
    public static final int REQ_CODE = 7301;

    public static final int ST_NOT_RUNNING = 0;   // 未安装或未启动
    public static final int ST_NO_PERMISSION = 1; // 已运行，未授权
    public static final int ST_BINDING = 2;       // 已授权，正在连接注入服务
    public static final int ST_READY = 3;         // 可实时注入
    public static final int ST_OLD = 4;           // Shizuku 版本过旧

    private static ShizukuHelper inst;
    public static synchronized ShizukuHelper get(Context c) {
        if (inst == null) inst = new ShizukuHelper(c.getApplicationContext());
        return inst;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler worker;
    private volatile IInjector injector;
    private boolean binding;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private final Shizuku.UserServiceArgs args;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            binding = false;
            injector = b != null && b.pingBinder() ? IInjector.Stub.asInterface(b) : null;
            notifyChanged();
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            binding = false;
            injector = null;
            notifyChanged();
        }
    };

    private ShizukuHelper(Context app) {
        this.app = app;
        HandlerThread t = new HandlerThread("inject", android.os.Process.THREAD_PRIORITY_DISPLAY);
        t.start();
        worker = new Handler(t.getLooper());
        args = new Shizuku.UserServiceArgs(new ComponentName(app.getPackageName(), InjectUserService.class.getName()))
                .daemon(false).processNameSuffix("inject").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);
        Shizuku.addBinderReceivedListenerSticky(() -> { notifyChanged(); ensureBound(); });
        Shizuku.addBinderDeadListener(() -> { injector = null; binding = false; notifyChanged(); });
        Shizuku.addRequestPermissionResultListener((code, res) -> {
            if (res == PackageManager.PERMISSION_GRANTED) ensureBound();
            notifyChanged();
        });
    }

    public void addListener(Runnable r) { listeners.addIfAbsent(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }
    private void notifyChanged() { main.post(() -> { for (Runnable r : listeners) r.run(); }); }

    public static boolean binderAlive() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    public boolean granted() {
        try {
            return binderAlive() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    public int state() {
        if (!binderAlive()) return ST_NOT_RUNNING;
        try { if (Shizuku.isPreV11() || Shizuku.getVersion() < 10) return ST_OLD; } catch (Throwable ignored) {}
        if (!granted()) return ST_NO_PERMISSION;
        if (injector != null) return ST_READY;
        return ST_BINDING;
    }

    public boolean isReady() { return injector != null && granted(); }

    /** 返回 false 表示 Shizuku 未运行，无法请求。 */
    public boolean requestPermission() {
        if (!binderAlive()) return false;
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { ensureBound(); return true; }
            Shizuku.requestPermission(REQ_CODE);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "request permission failed", t);
            return false;
        }
    }

    public void ensureBound() {
        if (injector != null || binding || !granted()) return;
        if (!Prefs.realtime(Prefs.get(app))) return;
        try {
            binding = true;
            Shizuku.bindUserService(args, conn);
        } catch (Throwable t) {
            binding = false;
            Log.w(TAG, "bind failed", t);
        }
        notifyChanged();
    }

    public void unbind() {
        try { Shizuku.unbindUserService(args, conn, true); } catch (Throwable ignored) {}
        injector = null;
        binding = false;
        notifyChanged();
    }

    /** 异步注入一个单指触摸事件（真实屏幕坐标）。 */
    public void post(int action, float x, float y, long downTime, long eventTime, long delayMs) {
        final IInjector i = injector;
        if (i == null) return;
        worker.postDelayed(() -> {
            try { i.injectTouch(action, x, y, downTime, eventTime); }
            catch (Throwable t) { Log.w(TAG, "inject failed", t); }
        }, delayMs);
    }

    /** 在注入线程上排队执行（保证在已排队的注入之后）。 */
    public void after(Runnable r, long delayMs) { worker.postDelayed(() -> main.post(r), delayMs); }
}
