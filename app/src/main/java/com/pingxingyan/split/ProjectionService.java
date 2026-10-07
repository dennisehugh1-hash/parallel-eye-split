package com.pingxingyan.split;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;

/**
 * 前台服务（类型 mediaProjection）：持有 MediaProjection，用 VirtualDisplay + ImageReader 以半分辨率持续抓屏，
 * 并管理全屏分屏悬浮层和可拖动悬浮按钮。
 */
public class ProjectionService extends Service {
    private static final String TAG = "ParallelEye";

    public static final String ACTION_START = "com.pingxingyan.split.START";
    public static final String ACTION_STOP = "com.pingxingyan.split.STOP";
    public static final String ACTION_TOGGLE = "com.pingxingyan.split.TOGGLE";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_DATA = "data";

    private static final String CHANNEL_ID = "running";
    private static final int NOTIF_ID = 1;
    private static final long LIVE_FRAME_MS = 30;       // ~30fps 上限
    private static final long IDLE_FRAME_MS = 200;      // 分屏关闭时只低频保留最新画面
    private static final long HIDE_SETTLE_MS = 180;     // 间歇刷新：隐藏悬浮层后等待合成完成的时间

    public static volatile boolean running = false;
    public static volatile boolean splitOnPublic = false;

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread captureThread;
    private Handler captureHandler;

    private WindowManager wm;
    private SharedPreferences prefs;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private int capW, capH, capDpi;
    private volatile boolean gotContentResize = false;

    // 帧缓冲：back 由抓帧线程在 lock 下写入；front 只在主线程使用；交换在主线程完成
    private final Object lock = new Object();
    private Bitmap backBmp, frontBmp;
    private int backW, backH, frontW, frontH;
    private boolean backNew;
    private boolean swapPosted;
    private ByteBuffer tmpBuf;
    private long lastFrameAt;

    private volatile boolean splitOn = false;
    private volatile boolean hiddenForCapture = false;

    private SplitView splitView;
    private WindowManager.LayoutParams splitLp;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleLp;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, key) -> {
        if (splitView != null) splitView.invalidate();
        if (Prefs.K_MODE.equals(key) || Prefs.K_INTERVAL_MS.equals(key)) restartIntervalCycle();
        if ((Prefs.K_RES.equals(key) || Prefs.K_GPU.equals(key)) && virtualDisplay != null) {
            Point sz = currentCaptureSource();
            captureHandler.post(() -> resizeCapture(scaled(sz.x), scaled(sz.y)));
        }
    };

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = Prefs.get(this);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        captureThread = new HandlerThread("capture", android.os.Process.THREAD_PRIORITY_DISPLAY);
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
        createChannel();
        if (Prefs.realtime(prefs)) ShizukuHelper.get(this).ensureBound();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopEverything();
            return START_NOT_STICKY;
        }
        if (ACTION_TOGGLE.equals(action)) {
            if (projection != null) toggleSplit(); else stopEverything();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            int code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data;
            if (Build.VERSION.SDK_INT >= 33) data = intent.getParcelableExtra(EXTRA_DATA, Intent.class);
            else data = intent.getParcelableExtra(EXTRA_DATA);
            if (code != Activity.RESULT_OK || data == null) {
                stopEverything();
                return START_NOT_STICKY;
            }
            // 必须先进入前台（类型 mediaProjection），再调用 getMediaProjection（Android 10+/14 要求）
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIF_ID, n);
            }
            // 每次会话使用新的授权（Android 14 起授权 Intent 不能复用）
            releaseProjection();
            try {
                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                projection = mpm.getMediaProjection(code, data);
            } catch (Exception e) {
                Log.e(TAG, "getMediaProjection failed", e);
                projection = null;
            }
            if (projection == null) {
                Toast.makeText(this, "获取录屏授权失败，请重新开始", Toast.LENGTH_LONG).show();
                stopEverything();
                return START_NOT_STICKY;
            }
            running = true;
            startCapture();
            showBubble();
            return START_NOT_STICKY;
        }
        // 被系统重启或未知意图：没有授权，无法继续
        if (projection == null) stopEverything();
        return START_NOT_STICKY;
    }

    // ---------------------------------------------------------------- 抓屏

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            // 用户在系统界面停止共享 / 锁屏等
            main.post(ProjectionService.this::stopEverything);
        }

        @Override
        public void onCapturedContentResize(int width, int height) {
            // Android 14+：捕获内容（整个屏幕或单个应用）尺寸变化时回调，以它为准保证宽高比一致
            gotContentResize = true;
            contentW = width;
            contentH = height;
            captureHandler.post(() -> resizeCapture(scaled(width), scaled(height)));
        }
    };

    private volatile int contentW, contentH;

    private Point currentCaptureSource() {
        return gotContentResize && contentW > 0 ? new Point(contentW, contentH) : realDisplaySize();
    }

    private Point realDisplaySize() { return Prefs.realDisplaySize(this); }

    /** 捕获的是整个屏幕（而不是单个应用窗口）时，才按屏幕尺寸调整虚拟显示。 */
    private boolean capturingWholeDisplay(Point oldDisplay) {
        if (!gotContentResize) return true;
        int w = contentW, h = contentH;
        return (w == oldDisplay.x && h == oldDisplay.y) || (w == oldDisplay.y && h == oldDisplay.x);
    }

    private Point lastDisplay = new Point();

    /** 屏幕旋转/尺寸变化：让虚拟显示与屏幕当前方向、宽高比一致。 */
    private void onDisplayMaybeChanged() {
        if (virtualDisplay == null) return;
        Point sz = realDisplaySize();
        if (sz.equals(lastDisplay)) return;
        Point old = lastDisplay;
        lastDisplay = sz;
        if (capturingWholeDisplay(old)) {
            captureHandler.post(() -> resizeCapture(scaled(sz.x), scaled(sz.y)));
        }
        clampBubble();
    }

    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int displayId) { }
        @Override public void onDisplayRemoved(int displayId) { }
        @Override public void onDisplayChanged(int displayId) {
            if (displayId == android.view.Display.DEFAULT_DISPLAY) onDisplayMaybeChanged();
        }
    };

    private void startCapture() {
        Point sz = realDisplaySize();
        lastDisplay = sz;
        getSystemService(DisplayManager.class).registerDisplayListener(displayListener, main);
        capDpi = getResources().getDisplayMetrics().densityDpi;
        capW = even(scaled(sz.x));
        capH = even(scaled(sz.y));
        reader = newReader(capW, capH);
        // Android 14 要求：createVirtualDisplay 之前注册回调；每个 MediaProjection 只能创建一次虚拟显示
        projection.registerCallback(projectionCallback, main);
        try {
            virtualDisplay = projection.createVirtualDisplay("ParallelEye", capW, capH, capDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, captureHandler);
        } catch (Exception e) {
            Log.e(TAG, "createVirtualDisplay failed", e);
            Toast.makeText(this, "创建录屏失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            stopEverything();
        }
    }

    private static int even(int v) { return Math.max(2, v & ~1); }

    /** 按画质设置缩放捕获分辨率：高=原生，标准=0.75，省电=0.5。 */
    private int scaled(int v) {
        return Math.round(v * Prefs.resPct(prefs) / 100f);
    }

    /** 帧间隔下限（毫秒）：高=屏幕刷新率，标准=60fps，省电=30fps。 */
    private long minFrameMs() {
        int f = Prefs.fpsCap(prefs);
        if (f == 30) return 32;
        if (f == 60) return 15;
        return 0; // 跟随屏幕：由虚拟显示按屏幕刷新率产生帧
    }

    /** Android 10+：GPU 可直接采样的 ImageReader，帧以 HardwareBuffer 零拷贝包装成硬件位图绘制。 */
    private boolean wantHw() {
        return Build.VERSION.SDK_INT >= 29 && Prefs.gpu(prefs);
    }
    private volatile boolean hwPath = false; // 当前 reader 的类型

    private ImageReader newReader(int w, int h) {
        ImageReader r;
        hwPath = wantHw();
        hwOk = true;
        if (hwPath) {
            r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 4,
                    android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        } else {
            r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3);
        }
        r.setOnImageAvailableListener(this::onImage, captureHandler);
        return r;
    }

    /** 在抓帧线程调用。旋转后调整虚拟显示尺寸（不重建，Android 14 不允许重建）。 */
    private void resizeCapture(int w, int h) {
        w = even(w); h = even(h);
        if (virtualDisplay == null || (w == capW && h == capH && hwPath == wantHw())) return;
        ImageReader old = reader;
        capW = w; capH = h;
        reader = newReader(w, h);
        try {
            virtualDisplay.resize(w, h, capDpi);
            virtualDisplay.setSurface(reader.getSurface());
        } catch (Exception e) {
            Log.w(TAG, "resize failed", e);
        }
        if (old != null) {
            old.setOnImageAvailableListener(null, null);
            synchronized (lock) {
                // 旧 reader 的图像随 reader 一起失效：丢弃待发布帧，保留显示中的位图到下一帧替换
                if (pendingImg != null) { pendingImg.close(); pendingImg = null; pendingHw = null; backNew = false; }
            }
            final ImageReader o = old;
            final Image a, b2;
            synchronized (lock) { a = shownImg; b2 = prevImg; }
            // 延迟关闭，避免正在显示的硬件位图对应缓冲被立即回收
            captureHandler.postDelayed(() -> {
                synchronized (lock) {
                    if (shownImg == a) shownImg = null;
                    if (prevImg == b2) prevImg = null;
                }
                o.close();
            }, 500);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        main.post(this::onDisplayMaybeChanged);
        main.post(this::clampBubble);
    }

    /** 抓帧线程。 */
    private void onImage(ImageReader r) {
        Image img;
        try {
            img = r.acquireLatestImage();
        } catch (Exception e) {
            return;
        }
        if (img == null) return;
        try {
            if (r != reader) return;
            long now = SystemClock.uptimeMillis();
            boolean publish;
            if (!splitOn) {
                if (now - lastFrameAt < IDLE_FRAME_MS) return;
                publish = true;
            } else if (mode() == Prefs.MODE_LIVE) {
                if (now - lastFrameAt < minFrameMs()) return;
                publish = true;
            } else {
                // 间歇刷新：只接收悬浮层隐藏期间的帧，恢复显示时再统一发布
                if (!hiddenForCapture) return;
                publish = false;
            }
            lastFrameAt = now;
            if (hwPath && hwOk) {
                Image keep = img;
                img = null; // 由 hw 队列负责关闭
                if (!queueHw(keep)) { hwOk = false; }
                if (publish) postSwap();
                return;
            }
            copyToBack(img);
            if (publish) postSwap();
        } catch (Exception e) {
            Log.w(TAG, "frame error", e);
        } finally {
            if (img != null) img.close();
        }
    }

    private volatile boolean hwOk = true;
    // 硬件路径：pendingImg 等待发布；shownImg / prevImg 为正在显示（及上一帧，可能仍在 RenderThread 绘制中）
    private Image pendingImg, shownImg, prevImg;
    private Bitmap pendingHw;

    /** 抓帧线程：把 Image 包装为硬件位图放入待发布槽。返回 false 表示不支持，改走拷贝路径。 */
    private boolean queueHw(Image img) {
        Bitmap b = null;
        try {
            android.hardware.HardwareBuffer hb = img.getHardwareBuffer();
            if (hb != null) {
                b = Bitmap.wrapHardwareBuffer(hb, android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB));
                hb.close();
            }
        } catch (Throwable t) {
            Log.w(TAG, "wrapHardwareBuffer failed", t);
        }
        if (b == null) { img.close(); return false; }
        Image old;
        synchronized (lock) {
            old = pendingImg;
            pendingImg = img;
            pendingHw = b;
            backW = img.getWidth(); backH = img.getHeight();
            backNew = true;
        }
        if (old != null) old.close();
        return true;
    }

    private void closeHwImages() {
        synchronized (lock) {
            for (Image i : new Image[]{pendingImg, shownImg, prevImg}) {
                if (i != null) try { i.close(); } catch (Exception ignored) {}
            }
            pendingImg = shownImg = prevImg = null;
            pendingHw = null;
        }
    }

    private void copyToBack(Image img) {
        Image.Plane plane = img.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int w = img.getWidth(), h = img.getHeight();
        int bw = rowStride / pixelStride;
        synchronized (lock) {
            if (backBmp == null || backBmp.getWidth() != bw || backBmp.getHeight() != h) {
                backBmp = Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888);
            }
            int need = bw * h * 4;
            buf.rewind();
            if (buf.remaining() >= need) {
                backBmp.copyPixelsFromBuffer(buf);
            } else {
                // 最后一行可能没有行尾填充，补齐后再拷贝
                if (tmpBuf == null || tmpBuf.capacity() < need) tmpBuf = ByteBuffer.allocateDirect(need);
                tmpBuf.clear();
                tmpBuf.put(buf);
                tmpBuf.rewind();
                backBmp.copyPixelsFromBuffer(tmpBuf);
            }
            backBmp.setHasAlpha(false);
            backW = w; backH = h;
            backNew = true;
        }
    }

    private void postSwap() {
        synchronized (lock) {
            if (swapPosted) return;
            swapPosted = true;
        }
        main.post(this::swapNow);
    }

    /** 主线程：交换前后缓冲并刷新视图。 */
    private void swapNow() {
        synchronized (lock) {
            swapPosted = false;
            if (backNew && pendingImg != null) {
                Image drop = prevImg;
                prevImg = shownImg;
                shownImg = pendingImg;
                pendingImg = null;
                frontBmp = pendingHw;
                pendingHw = null;
                frontW = backW; frontH = backH;
                backNew = false;
                if (drop != null) try { drop.close(); } catch (Exception ignored) {}
            } else if (backNew) {
                if (frontBmp != null && frontBmp.getConfig() == Bitmap.Config.HARDWARE) frontBmp = null;
                Bitmap t = frontBmp; frontBmp = backBmp; backBmp = t;
                frontW = backW; frontH = backH;
                backNew = false;
            }
        }
        if (splitView != null && frontBmp != null) splitView.setFrame(frontBmp, frontW, frontH);
    }

    // ---------------------------------------------------------------- 间歇刷新

    private final Runnable hideForCapture = new Runnable() {
        @Override public void run() {
            if (!splitOn || injecting || rtActive || mode() != Prefs.MODE_INTERVAL) return;
            hiddenForCapture = true;
            if (splitView != null) splitView.setTransparent(true);
            if (bubble != null) bubble.setAlpha(0f);
            main.postDelayed(commitCapture, HIDE_SETTLE_MS);
        }
    };

    private final Runnable commitCapture = new Runnable() {
        @Override public void run() {
            hiddenForCapture = false;
            swapNow();
            if (splitView != null) splitView.setTransparent(false);
            if (bubble != null) bubble.setAlpha(1f);
            if (splitOn && mode() == Prefs.MODE_INTERVAL) {
                main.postDelayed(hideForCapture, Prefs.intervalMs(prefs));
            }
        }
    };

    // ---------------------------------------------------------------- 防套娃（整个屏幕 + 实时）

    /** -1 未尝试；否则为 SkipCapture.R_*。 */
    private int skipResult = -1;

    /** 实际使用的刷新方式：录整个屏幕但分屏层未能从录屏中排除时，强制间歇刷新，避免无限套娃。 */
    private int mode() {
        int m = Prefs.mode(prefs);
        if (!skipEnabled()) return m; // 未开启实验功能：与 v1.5 行为一致
        if (m == Prefs.MODE_LIVE && capturingDisplay() && splitOn
                && skipResult != SkipCapture.R_LOCAL && skipResult != SkipCapture.R_SHIZUKU) return Prefs.MODE_INTERVAL;
        return m;
    }

    private boolean capturingDisplay() {
        if (!gotContentResize) return Prefs.scope(prefs) == Prefs.SCOPE_DISPLAY || Build.VERSION.SDK_INT < 34;
        Point d = realDisplaySize();
        return (contentW == d.x && contentH == d.y) || (contentW == d.y && contentH == d.x);
    }

    public static volatile int skipStatus = -1;

    /** 仅在“强制整个屏幕”且用户开启了实验开关时使用。 */
    private boolean skipEnabled() {
        return Prefs.skipCapture(prefs) && Prefs.scope(prefs) == Prefs.SCOPE_DISPLAY && SkipCapture.supported();
    }

    private void applySkip() {
        if (splitView == null || !skipEnabled()) { skipStatus = -1; return; }
        int r = SkipCapture.apply(splitView, ShizukuHelper.get(this));
        if (bubble != null) SkipCapture.apply(bubble, ShizukuHelper.get(this));
        boolean changed = r != skipResult;
        skipResult = r;
        skipStatus = r;
        if (changed) {
            if (r == SkipCapture.R_FAIL && Prefs.mode(prefs) == Prefs.MODE_LIVE && capturingDisplay()) {
                Toast.makeText(this, "无法把分屏层排除出录屏，已改用间歇刷新（防止套娃）", Toast.LENGTH_LONG).show();
            }
            restartIntervalCycle();
        }
    }

    private void restartIntervalCycle() {
        main.removeCallbacks(hideForCapture);
        main.removeCallbacks(commitCapture);
        if (hiddenForCapture) {
            hiddenForCapture = false;
            if (splitView != null) splitView.setTransparent(false);
            if (bubble != null) bubble.setAlpha(1f);
        }
        if (splitOn && mode() == Prefs.MODE_INTERVAL) {
            main.postDelayed(hideForCapture, 300);
        }
    }

    // ---------------------------------------------------------------- 分屏悬浮层

    private static int overlayType() {
        return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    public void toggleSplit() {
        if (splitOn) hideSplit(); else showSplit();
    }

    private void showSplit() {
        if (splitOn) return;
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予“显示在其他应用上层”权限", Toast.LENGTH_LONG).show();
            return;
        }
        splitView = new SplitView(this, prefs, splitListener);
        if (frontBmp != null) splitView.setFrame(frontBmp, frontW, frontH);
        splitLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        splitLp.gravity = Gravity.TOP | Gravity.START;
        // 不再强制横屏：跟随屏幕当前方向。尽量铺满整个屏幕（含刘海区域、系统栏下方）
        if (Build.VERSION.SDK_INT >= 30) {
            splitLp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            splitLp.setFitInsetsTypes(0);
            splitLp.setFitInsetsSides(0);
            splitLp.setFitInsetsIgnoringVisibility(true);
        } else if (Build.VERSION.SDK_INT >= 28) {
            splitLp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        splitLp.setTitle("ParallelEyeSplit");
        // 请求屏幕最高刷新率（90/120Hz 屏幕上避免被限制在 60Hz）
        try {
            android.view.Display d = getSystemService(DisplayManager.class).getDisplay(android.view.Display.DEFAULT_DISPLAY);
            android.view.Display.Mode cur = d.getMode(), best = cur;
            for (android.view.Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()
                        && m.getRefreshRate() > best.getRefreshRate()) best = m;
            }
            splitLp.preferredDisplayModeId = best.getModeId();
            splitLp.preferredRefreshRate = best.getRefreshRate();
        } catch (Exception ignored) {}
        try {
            wm.addView(splitView, splitLp);
        } catch (Exception e) {
            Log.e(TAG, "add split overlay failed", e);
            Toast.makeText(this, "无法显示悬浮层：" + e.getMessage(), Toast.LENGTH_LONG).show();
            splitView = null;
            return;
        }
        tryHideSystemBars(splitView);
        skipResult = -1;
        skipStatus = -1;
        if (skipEnabled()) { // 实验功能，默认关：不开启时完全不触碰默认路径
            splitView.post(this::applySkip);
            // 窗口重新布局（旋转等）后图层可能重建，重新设置
            splitView.addOnLayoutChangeListener((v, a, b, c, d, e, f, g, h) -> v.post(this::applySkip));
        }
        startOrientationListener();
        splitOn = true;
        splitOnPublic = true;
        // 让悬浮按钮保持在分屏层之上
        readdBubble();
        updateBubbleLook();
        restartIntervalCycle();
        updateNotification();
    }

    // ---------------------------------------------------------------- 可操控半屏

    private boolean injecting = false;
    private int injectSeq = 0;
    private long lastA11yToast = 0;

    private final SplitView.Listener splitListener = new SplitView.Listener() {
        @Override public void onDoubleTap() { hideSplit(); }

        @Override public void onControlTouchStart() {
            if (GestureService.instance == null) a11yMissingToast();
        }

        @Override public void onInjectStrokes(java.util.List<SplitView.Stroke> strokes) {
            injectStrokes(strokes);
        }

        @Override public void onRotationChanged(int deg) {
            if (bubble != null) bubble.setRotation(splitOn ? deg : 0);
        }

        @Override public boolean realtimeAvailable() {
            if (!Prefs.realtime(prefs)) return false;
            ShizukuHelper h = ShizukuHelper.get(ProjectionService.this);
            if (h.isReady()) return true;
            h.ensureBound();
            shizukuFallbackToast();
            return false;
        }

        @Override public void onRealtimeEvent(int action, float x, float y) {
            realtimeEvent(action, x, y);
        }
    };

    // ---------------------------------------------------------------- 实时操控（Shizuku）

    /** 等待 FLAG_NOT_TOUCHABLE 生效（同步到 InputDispatcher）后才开始注入。 */
    private static final long RT_SETTLE_MS = 60;
    private long rtStartAt, rtDownTime;
    private int rtSeq;
    private boolean rtActive;
    private long lastShizukuToast;

    private void shizukuFallbackToast() {
        long now = SystemClock.uptimeMillis();
        if (now - lastShizukuToast < 5000) return;
        lastShizukuToast = now;
        Toast.makeText(this, "Shizuku 未运行或未授权，已改用无障碍回放（松手后执行）", Toast.LENGTH_SHORT).show();
    }

    private void realtimeEvent(int action, float x, float y) {
        ShizukuHelper h = ShizukuHelper.get(this);
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            if (injecting) return; // 无障碍回放进行中
            rtActive = true;
            rtSeq++;
            main.removeCallbacks(hideForCapture);
            main.removeCallbacks(commitCapture);
            if (hiddenForCapture) {
                hiddenForCapture = false;
                if (splitView != null) splitView.setTransparent(false);
                if (bubble != null) bubble.setAlpha(1f);
            }
            // 当前这根手指的事件流仍发给分屏层（系统不会对进行中的触摸重新命中），新注入的事件则穿透到下面的应用
            setPassThrough(true);
            rtStartAt = now + RT_SETTLE_MS;
            rtDownTime = rtStartAt;
            h.post(action, x, y, rtDownTime, 0, RT_SETTLE_MS);
            return;
        }
        if (!rtActive) return;
        long delay = Math.max(0, rtStartAt - now);
        h.post(action, x, y, rtDownTime, 0, delay);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            final int seq = rtSeq;
            h.after(() -> {
                if (seq != rtSeq || !rtActive) return;
                rtActive = false;
                if (!injecting) {
                    setPassThrough(false);
                    restartIntervalCycle();
                }
            }, delay + 40);
        }
    }

    private void a11yMissingToast() {
        long now = SystemClock.uptimeMillis();
        if (now - lastA11yToast < 3000) return;
        lastA11yToast = now;
        Toast.makeText(this, "未开启无障碍服务“平行眼分屏·可操控半屏”，无法操控。请长按悬浮按钮进入设置页开启。", Toast.LENGTH_LONG).show();
    }

    /**
     * 回放前让分屏层和悬浮按钮“放行”触摸：加 FLAG_NOT_TOUCHABLE，并把窗口透明度降到系统允许触摸穿透的上限以下
     * （Android 12+ 会丢弃穿过不透明第三方悬浮窗的触摸，SYSTEM_ALERT_WINDOW 窗口透明度 ≤ 0.8 时例外）。
     * 窗口始终保持可见（不会闪烁消失）。
     */
    private void setPassThrough(boolean on) {
        float a = 1f;
        if (on && Build.VERSION.SDK_INT >= 31) {
            float max = 0.8f;
            try {
                android.hardware.input.InputManager im = getSystemService(android.hardware.input.InputManager.class);
                max = Math.min(max, im.getMaximumObscuringOpacityForTouch());
            } catch (Exception ignored) {}
            a = Math.max(0f, max - 0.05f);
        }
        if (splitView != null && splitLp != null) {
            if (on) splitLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            else splitLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            splitLp.alpha = a;
            try { wm.updateViewLayout(splitView, splitLp); } catch (Exception ignored) {}
        }
        if (bubble != null && bubbleLp != null) {
            if (on) bubbleLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            else bubbleLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            bubbleLp.alpha = on ? 0f : 1f;
            try { wm.updateViewLayout(bubble, bubbleLp); } catch (Exception ignored) {}
        }
    }

    private void injectStrokes(java.util.List<SplitView.Stroke> strokes) {
        final GestureService g = GestureService.instance;
        if (g == null) { a11yMissingToast(); return; }
        if (injecting || rtActive || !splitOn) return;
        injecting = true;
        final int seq = ++injectSeq;
        // 暂停间歇刷新，放行触摸
        main.removeCallbacks(hideForCapture);
        main.removeCallbacks(commitCapture);
        if (hiddenForCapture) {
            hiddenForCapture = false;
            if (splitView != null) splitView.setTransparent(false);
            if (bubble != null) bubble.setAlpha(1f);
        }
        setPassThrough(true);
        final Runnable done = () -> finishInject(seq);
        // 等窗口标志生效（输入窗口信息经 SurfaceFlinger 同步到 InputDispatcher）后再回放
        main.postDelayed(() -> {
            if (seq != injectSeq || !injecting) return;
            long dur = g.play(strokes, () -> main.post(done));
            if (dur < 0) {
                finishInject(seq);
            } else {
                main.postDelayed(done, dur + 3000); // 兜底：回调丢失时恢复
            }
        }, 80);
    }

    private void finishInject(int seq) {
        if (seq != injectSeq || !injecting) return;
        injecting = false;
        setPassThrough(false);
        restartIntervalCycle();
    }

    // ---------------------------------------------------------------- 方向传感器（自动判断向左/向右横拿）

    private android.view.OrientationEventListener orientationListener;

    private void startOrientationListener() {
        if (orientationListener != null) return;
        orientationListener = new android.view.OrientationEventListener(this) {
            @Override public void onOrientationChanged(int deg) {
                if (deg == ORIENTATION_UNKNOWN || splitView == null) return;
                // 90°：设备左侧朝上（顺时针转，顶部朝右）；270°：右侧朝上（逆时针转，顶部朝左）。带死区防抖
                if (deg >= 60 && deg <= 120) splitView.setSensorDir(Prefs.DIR_RIGHT);
                else if (deg >= 240 && deg <= 300) splitView.setSensorDir(Prefs.DIR_LEFT);
            }
        };
        if (orientationListener.canDetectOrientation()) orientationListener.enable();
    }

    private void stopOrientationListener() {
        if (orientationListener != null) {
            orientationListener.disable();
            orientationListener = null;
        }
    }

    /**
     * 尝试隐藏状态栏/导航栏。系统通常只允许获得焦点的应用窗口控制系统栏，悬浮窗（不可获焦）大多无效，
     * 此时分屏画面仍会画在系统栏下面（系统栏半透明叠加在上方）。
     */
    @SuppressWarnings("deprecation")
    private void tryHideSystemBars(View v) {
        try {
            v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            if (Build.VERSION.SDK_INT >= 30) {
                v.post(() -> {
                    try {
                        android.view.WindowInsetsController c = v.getWindowInsetsController();
                        if (c != null) {
                            c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                            c.hide(android.view.WindowInsets.Type.systemBars());
                        }
                    } catch (Exception ignored) {}
                });
            }
        } catch (Exception ignored) {}
    }

    private void hideSplit() {
        if (injecting) { injecting = false; injectSeq++; setPassThrough(false); }
        if (rtActive) { rtActive = false; rtSeq++; setPassThrough(false); }
        main.removeCallbacks(hideForCapture);
        main.removeCallbacks(commitCapture);
        hiddenForCapture = false;
        splitOn = false;
        splitOnPublic = false;
        if (splitView != null) {
            try { wm.removeView(splitView); } catch (Exception ignored) {}
            splitView = null;
        }
        stopOrientationListener();
        if (bubble != null) { bubble.setAlpha(1f); bubble.setRotation(0); }
        updateBubbleLook();
        updateNotification();
    }

    // ---------------------------------------------------------------- 悬浮按钮

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showBubble() {
        if (bubble != null || !Settings.canDrawOverlays(this)) return;
        bubble = new TextView(this);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        int size = dp(54);
        bubbleLp = new WindowManager.LayoutParams(size, size, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = prefs.getInt(Prefs.K_BUBBLE_X, dp(8));
        bubbleLp.y = prefs.getInt(Prefs.K_BUBBLE_Y, dp(160));
        bubbleLp.setTitle("ParallelEyeBubble");
        updateBubbleLook();

        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        final long longPressMs = ViewConfiguration.getLongPressTimeout();
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY;
            int startX, startY;
            boolean dragging, longFired;
            final Runnable longPress = () -> {
                longFired = true;
                bubble.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                openSettings();
            };

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX(); downRawY = e.getRawY();
                        startX = bubbleLp.x; startY = bubbleLp.y;
                        dragging = false; longFired = false;
                        main.postDelayed(longPress, longPressMs);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                        if (!dragging && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                            dragging = true;
                            main.removeCallbacks(longPress);
                        }
                        if (dragging && !longFired) {
                            bubbleLp.x = Math.round(startX + dx);
                            bubbleLp.y = Math.round(startY + dy);
                            clampBubble();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        main.removeCallbacks(longPress);
                        if (dragging) {
                            prefs.edit().putInt(Prefs.K_BUBBLE_X, bubbleLp.x).putInt(Prefs.K_BUBBLE_Y, bubbleLp.y).apply();
                        } else if (!longFired) {
                            toggleSplit();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        main.removeCallbacks(longPress);
                        return true;
                }
                return false;
            }
        });
        try {
            wm.addView(bubble, bubbleLp);
        } catch (Exception e) {
            Log.e(TAG, "add bubble failed", e);
            bubble = null;
        }
    }

    private void updateBubbleLook() {
        if (bubble == null) return;
        bubble.setText(splitOn ? "关闭" : "分屏");
        bubble.setBackgroundResource(splitOn ? R.drawable.bubble_bg_on : R.drawable.bubble_bg);
    }

    private void clampBubble() {
        if (bubble == null || bubbleLp == null) return;
        Point sz = realDisplaySize();
        bubbleLp.x = Math.max(0, Math.min(bubbleLp.x, sz.x - bubbleLp.width));
        bubbleLp.y = Math.max(0, Math.min(bubbleLp.y, sz.y - bubbleLp.height));
        try { wm.updateViewLayout(bubble, bubbleLp); } catch (Exception ignored) {}
    }

    private void readdBubble() {
        if (bubble == null) { showBubble(); return; }
        try {
            wm.removeViewImmediate(bubble);
            wm.addView(bubble, bubbleLp);
        } catch (Exception e) {
            Log.w(TAG, "re-add bubble failed", e);
        }
    }

    private void removeBubble() {
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
        }
    }

    private void openSettings() {
        // 分屏层会盖住设置界面，先关闭分屏
        if (splitOn) hideSplit();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try { startActivity(i); } catch (Exception e) { Log.w(TAG, "open settings failed", e); }
    }

    // ---------------------------------------------------------------- 通知

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "平行眼分屏运行状态", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("录屏进行中时显示，可在此停止");
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        int piFlags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, ProjectionService.class).setAction(ACTION_STOP), piFlags);
        PendingIntent toggle = PendingIntent.getService(this, 2,
                new Intent(this, ProjectionService.class).setAction(ACTION_TOGGLE), piFlags);
        PendingIntent open = PendingIntent.getActivity(this, 3,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), piFlags);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("平行眼分屏运行中")
                .setContentText(splitOn ? "分屏已开启（双击画面或点悬浮按钮关闭）" : "点悬浮按钮开启分屏；长按悬浮按钮打开设置")
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, splitOn ? "关闭分屏" : "开启分屏", toggle).build())
                .addAction(new Notification.Action.Builder(null, "停止", stop).build())
                .build();
    }

    private void updateNotification() {
        if (!running) return;
        try {
            getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification());
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- 清理

    private void releaseProjection() {
        final VirtualDisplay vd = virtualDisplay;
        final ImageReader r = reader;
        virtualDisplay = null;
        reader = null;
        gotContentResize = false;
        try { getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener); } catch (Exception ignored) {}
        if (vd != null) vd.release();
        if (r != null) {
            r.setOnImageAvailableListener(null, null);
            captureHandler.post(() -> { closeHwImages(); r.close(); });
        }
        if (projection != null) {
            try {
                projection.unregisterCallback(projectionCallback);
                projection.stop();
            } catch (Exception ignored) {}
            projection = null;
        }
    }

    private void stopEverything() {
        hideSplit();
        removeBubble();
        releaseProjection();
        running = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        hideSplit();
        removeBubble();
        releaseProjection();
        running = false;
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        main.removeCallbacksAndMessages(null);
        captureThread.quitSafely();
        super.onDestroy();
    }

    static Intent toggleIntent(Context c) {
        return new Intent(c, ProjectionService.class).setAction(ACTION_TOGGLE);
    }
}
