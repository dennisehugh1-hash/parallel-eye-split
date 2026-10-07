package com.pingxingyan.split;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 全屏分屏视图：黑底，左右各画一份截屏画面（各自裁剪在自己的半屏内），红色对焦圈叠加在每个画面底部。
 * 分屏方向可“强制横屏”：系统仍是竖屏时整体旋转 ±90° 绘制，手机横拿即为左右并排；每个画面占半个（逻辑）屏幕宽、全高；“填满”时裁切边缘铺满半屏，“完整显示”时等比适配。
 * 可选“可操控半屏”：该半屏上的单指操作被记录并映射回真实屏幕坐标，交给无障碍服务回放。
 */
@SuppressLint("ViewConstructor")
public class SplitView extends View {

    public interface Listener {
        void onDoubleTap();
        /** 可操控半屏上的一批单指操作（真实屏幕坐标），在手指全部抬起且静止一小段时间后回调。 */
        void onInjectStrokes(List<Stroke> strokes);
        /** 可操控半屏上的触摸开始时回调，用于检查无障碍服务是否可用。 */
        void onControlTouchStart();
        /** 画面整体旋转角度变化（0 / 90 / -90），用于同步旋转悬浮按钮文字。 */
        void onRotationChanged(int deg);
        /** 是否使用 Shizuku 实时注入（否则松手后用无障碍回放）。 */
        boolean realtimeAvailable();
        /** 实时注入一个单指事件（真实屏幕坐标）。action: DOWN/MOVE/UP/CANCEL。 */
        void onRealtimeEvent(int action, float x, float y);
    }

    /** 一次单指操作：真实屏幕坐标 + 时间戳（uptime 毫秒）。 */
    public static final class Stroke {
        public final ArrayList<double[]> pts = new ArrayList<>(); // {x, y, t}
        public boolean isTap = true;                                // 没有超过触摸阈值的移动
        public long downTime() { return (long) pts.get(0)[2]; }
        public long upTime() { return (long) pts.get(pts.size() - 1)[2]; }
    }

    /** 当前布局几何（用户看到的逻辑坐标系）。 */
    private static final class Geo {
        int rot;               // 0，或 90（手机向左转/逆时针）、-90（向右转/顺时针）
        float W, H, half, pxmm; // W/H 为用户看到的逻辑宽高
        float sep, cxL, cxR, cy, pw, ph, dotY, dotR;
    }

    private static final long BATCH_QUIET_MS = 250;

    private final SharedPreferences prefs;
    private final Listener listener;
    private final WindowManager wm;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final Paint fpsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int newFrames, drawCount;
    private long fpsT0;
    private float fpsCap, fpsDraw;
    private final RectF dst = new RectF();
    private final int touchSlop;

    private Bitmap frame;
    private int frameW, frameH;
    private boolean transparent;   // 间歇刷新抓帧期间：画成全透明，窗口保持可见
    private String hint;
    private long hintUntil;

    private final GestureDetector gestures;
    private final ScaleGestureDetector scaler;
    private int axisLock; // 0 未定 1 水平 2 垂直

    // 触摸分流
    private static final int T_NONE = 0, T_ADJUST = 1, T_CONTROL = 2, T_IGNORE = 3;
    private int touchMode = T_NONE;
    private Stroke curStroke;
    private boolean curCancelled;
    private float downX, downY;
    private final ArrayList<Stroke> batch = new ArrayList<>();
    private final Runnable flushBatch = this::flushBatch;

    public SplitView(Context ctx, SharedPreferences prefs, Listener listener) {
        super(ctx);
        this.prefs = prefs;
        this.listener = listener;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        dotPaint.setColor(Color.RED);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(16 * ctx.getResources().getDisplayMetrics().scaledDensity);
        setBackground(null);

        gestures = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { axisLock = 0; return true; }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (SplitView.this.listener != null) SplitView.this.listener.onDoubleTap();
                return true;
            }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (scaler.isInProgress() || e2.getPointerCount() > 1) return true;
                // 把手指位移换算到用户看到的（可能旋转过的）横屏坐标系
                int rot = rotationDeg();
                float ldx, ldy;
                if (rot == 90) { ldx = -dy; ldy = dx; }
                else if (rot == -90) { ldx = dy; ldy = -dx; }
                else { ldx = -dx; ldy = -dy; }
                if (axisLock == 0) {
                    if (Math.abs(ldx) > Math.abs(ldy) * 1.5f) axisLock = 1;
                    else if (Math.abs(ldy) > Math.abs(ldx) * 1.5f) axisLock = 2;
                    else return true;
                }
                float pxmm = pxPerMm();
                if (axisLock == 1) {
                    // 水平拖动每移动 1mm，间距变 0.5mm（更细腻）
                    float sep = clamp(Prefs.sepMm(prefs) + ldx / pxmm * 0.5f, Prefs.MIN_SEP_MM, Prefs.MAX_SEP_MM);
                    prefs.edit().putFloat(Prefs.K_SEP_MM, sep).apply();
                    showHint(String.format(Locale.ROOT, "两点间距 %.1f 毫米", sep));
                } else {
                    float off = clamp(Prefs.offsetMm(prefs) + ldy / pxmm, -Prefs.MAX_OFFSET_MM, Prefs.MAX_OFFSET_MM);
                    prefs.edit().putFloat(Prefs.K_OFFSET_MM, off).apply();
                    showHint(String.format(Locale.ROOT, "垂直偏移 %.1f 毫米", off));
                }
                return true;
            }
        });
        gestures.setIsLongpressEnabled(false);
        scaler = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            float acc = -1;
            @Override public boolean onScaleBegin(ScaleGestureDetector d) { acc = Prefs.sizePct(prefs); return true; }
            @Override public boolean onScale(ScaleGestureDetector d) {
                if (acc < 0) acc = Prefs.sizePct(prefs);
                acc = clamp(acc * d.getScaleFactor(), Prefs.MIN_SIZE_PCT, Prefs.MAX_SIZE_PCT);
                int n = Math.round(acc);
                if (n != Prefs.sizePct(prefs)) prefs.edit().putInt(Prefs.K_SIZE_PCT, n).apply();
                showHint("画面大小 " + n + "%");
                return true;
            }
        });
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private float pxPerMm() { return Prefs.pxPerMm(getResources().getDisplayMetrics()); }

    public void showHint(String s) {
        hint = s;
        hintUntil = SystemClock.uptimeMillis() + 1500;
        invalidate();
        postInvalidateDelayed(1600);
    }

    /** 仅在主线程调用。 */
    public void setFrame(Bitmap bmp, int w, int h) {
        newFrames++;
        frame = bmp;
        frameW = w;
        frameH = h;
        invalidate();
    }

    public void setTransparent(boolean t) {
        if (transparent != t) {
            transparent = t;
            invalidate();
        }
    }

    // ------------------------------------------------------------ 几何

    private Geo geo() {
        Geo g = new Geo();
        g.rot = rotationDeg();
        g.W = g.rot == 0 ? getWidth() : getHeight();
        g.H = g.rot == 0 ? getHeight() : getWidth();
        g.half = g.W / 2f;
        float pxmm = g.pxmm = pxPerMm();
        g.dotR = 1.6f * pxmm;

        // 两个画面中心（= 两个红点）之间的距离，限制在屏幕宽度内
        g.sep = clamp(Prefs.sepMm(prefs) * pxmm, 1f, g.W);
        g.cxL = g.half - g.sep / 2f;
        g.cxR = g.half + g.sep / 2f;
        float zoom = Prefs.sizePct(prefs) / 100f;
        float offset = Prefs.offsetMm(prefs) * pxmm;
        g.cy = g.H / 2f + offset;

        float fw = frameW > 0 ? frameW : g.W;
        float fh = frameH > 0 ? frameH : g.H;
        float k;
        if (Prefs.fitMode(prefs) == Prefs.FIT_FILL) {
            // 填满：画面以红点为中心，要保证盖住整个半屏（含间距偏移和垂直偏移造成的错位），不留黑洞
            float needW = 2f * Math.max(g.cxL, g.half - g.cxL);
            float needH = 2f * Math.max(g.cy, g.H - g.cy);
            k = Math.max(needW / fw, needH / fh);
        } else {
            // 完整显示：等比放进半屏
            k = Math.min(g.half / fw, g.H / fh);
        }
        k *= zoom;
        g.pw = fw * k;
        g.ph = fh * k;
        // 红点叠加在画面底部（画面底边与屏幕底边取较高者，再往上一点）
        float bottom = Math.min(g.cy + g.ph / 2f, g.H);
        g.dotY = clamp(bottom - 6f * pxmm, g.dotR * 2, g.H - g.dotR * 2);
        return g;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (transparent) return; // 窗口格式为 TRANSLUCENT，不画即为全透明
        canvas.drawColor(Color.BLACK);
        if (getWidth() == 0 || getHeight() == 0) return;
        Geo g = geo();
        if (g.rot != lastRot) {
            lastRot = g.rot;
            final int r = g.rot;
            post(() -> { if (listener != null) listener.onRotationChanged(r); });
        }
        canvas.save();
        if (g.rot == 90) {
            canvas.translate(getWidth(), 0);
            canvas.rotate(90);
        } else if (g.rot == -90) {
            canvas.translate(0, getHeight());
            canvas.rotate(-90);
        }

        for (int i = 0; i < 2; i++) {
            float cx = i == 0 ? g.cxL : g.cxR;
            canvas.save();
            // 每个画面只显示在自己的半屏内
            if (i == 0) canvas.clipRect(0, 0, g.half, g.H);
            else canvas.clipRect(g.half, 0, g.W, g.H);
            if (frame != null && !frame.isRecycled()) {
                src.set(0, 0, frameW, frameH);
                dst.set(cx - g.pw / 2f, g.cy - g.ph / 2f, cx + g.pw / 2f, g.cy + g.ph / 2f);
                canvas.drawBitmap(frame, src, dst, bmpPaint);
            } else {
                canvas.drawText("正在获取画面…", cx, g.cy, textPaint);
            }
            canvas.restore();
        }
        if (Prefs.showDots(prefs)) {
            dotPaint.setStrokeWidth(Math.max(2f, 0.5f * g.pxmm));
            for (int i = 0; i < 2; i++) {
                float cx = i == 0 ? g.cxL : g.cxR;
                dotPaint.setStyle(Paint.Style.STROKE);
                dotPaint.setColor(0xB0FF2020);
                canvas.drawCircle(cx, g.dotY, g.dotR, dotPaint);
                dotPaint.setStyle(Paint.Style.FILL);
                dotPaint.setColor(0xC0FF2020);
                canvas.drawCircle(cx, g.dotY, Math.max(1.5f, 0.35f * g.pxmm), dotPaint);
            }
        }

        if (Prefs.showFps(prefs)) {
            long now = SystemClock.uptimeMillis();
            drawCount++;
            if (fpsT0 == 0) fpsT0 = now;
            if (now - fpsT0 >= 1000) {
                fpsCap = newFrames * 1000f / (now - fpsT0);
                fpsDraw = drawCount * 1000f / (now - fpsT0);
                newFrames = 0; drawCount = 0; fpsT0 = now;
            }
            String t = String.format(Locale.ROOT, "画面 %.0f fps · 绘制 %.0f fps · %dx%d%s", fpsCap, fpsDraw, frameW, frameH,
                    frame != null && frame.getConfig() == Bitmap.Config.HARDWARE ? " · GPU" : " · CPU");
            fpsPaint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
            float tw = fpsPaint.measureText(t);
            fpsPaint.setColor(0x99000000);
            canvas.drawRect(8, 8, 24 + tw, 16 + fpsPaint.getTextSize() * 1.4f, fpsPaint);
            fpsPaint.setColor(0xFF7CFC00);
            canvas.drawText(t, 16, 12 + fpsPaint.getTextSize() * 1.1f, fpsPaint);
            postInvalidateDelayed(500);
        }
        if (hint != null && SystemClock.uptimeMillis() < hintUntil) {
            float ty = textPaint.getTextSize() * 2.5f;
            canvas.drawText(hint, g.W / 2f, ty, textPaint);
        }
        canvas.restore();
    }

    // ------------------------------------------------------------ 方向

    private int sensorDir = Prefs.DIR_LEFT;
    private int lastRot = Integer.MIN_VALUE;

    /** 由方向传感器更新（自动模式下使用）。 */
    public void setSensorDir(int dir) {
        if (dir != sensorDir) {
            sensorDir = dir;
            invalidate();
        }
    }

    /** 强制横屏且窗口是竖的（系统没有转屏）时，整个分屏画面旋转 ±90° 绘制。 */
    public int rotationDeg() {
        if (Prefs.splitOrient(prefs) != Prefs.ORIENT_FORCE_LANDSCAPE) return 0;
        if (getWidth() >= getHeight()) return 0;
        int d = Prefs.landDir(prefs);
        if (d == Prefs.DIR_AUTO) d = sensorDir;
        return d == Prefs.DIR_RIGHT ? -90 : 90;
    }

    /** 视图坐标 -> 用户看到的逻辑坐标（绘制变换的逆变换）。 */
    private float[] toLogical(int rot, float vx, float vy) {
        if (rot == 90) return new float[]{vy, getWidth() - vx};
        if (rot == -90) return new float[]{getHeight() - vy, vx};
        return new float[]{vx, vy};
    }

    // ------------------------------------------------------------ 触摸

    /** 该点是否在可操控的那一半屏幕内。 */
    private boolean inControlHalf(Geo g, float x, float y) {
        int pane = Prefs.controlPane(prefs);
        if (pane == Prefs.CONTROL_OFF) return false;
        return pane == Prefs.CONTROL_LEFT ? x < g.half : x >= g.half;
    }

    private Point displaySize() { return Prefs.realDisplaySize(getContext()); }

    /**
     * 视图坐标 -> 真实屏幕坐标：先换算到画面内的归一化位置，再换算到截屏帧像素，
     * 最后按虚拟显示的等比缩放（可能有黑边）还原到真实屏幕。clampToPane=false 时，落在画面外返回 null。
     */
    private float[] toScreen(Geo g, float lx, float ly, boolean clampToPane) {
        if (g.pw <= 0 || g.ph <= 0) return null;
        if (frame == null || frameW <= 0 || frameH <= 0) return null;
        int pane = Prefs.controlPane(prefs);
        float cx = pane == Prefs.CONTROL_LEFT ? g.cxL : g.cxR;
        float u = (lx - (cx - g.pw / 2f)) / g.pw;
        float v = (ly - (g.cy - g.ph / 2f)) / g.ph;
        // 同时要求在该半屏的可见区域内（被裁掉的部分看不见，不可点）
        float clipL = pane == Prefs.CONTROL_LEFT ? 0 : g.half;
        float clipR = pane == Prefs.CONTROL_LEFT ? g.half : g.W;
        float uMin = Math.max(0f, (clipL - (cx - g.pw / 2f)) / g.pw);
        float uMax = Math.min(1f, (clipR - (cx - g.pw / 2f)) / g.pw);
        float vMin = Math.max(0f, (0 - (g.cy - g.ph / 2f)) / g.ph);
        float vMax = Math.min(1f, (g.H - (g.cy - g.ph / 2f)) / g.ph);
        if (!clampToPane && (u < uMin || u > uMax || v < vMin || v > vMax)) return null;
        u = clamp(u, uMin, uMax);
        v = clamp(v, vMin, vMax);
        float fx = u * frameW, fy = v * frameH;
        Point d = displaySize();
        float s = Math.min((float) frameW / d.x, (float) frameH / d.y);
        float ox = (frameW - d.x * s) / 2f, oy = (frameH - d.y * s) / 2f;
        float sx = clamp((fx - ox) / s, 0, d.x - 1), sy = clamp((fy - oy) / s, 0, d.y - 1);
        return new float[]{sx, sy};
    }

    private boolean rtActive;  // 当前这次触摸走实时注入
    private boolean rtDown;    // 已注入 DOWN

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            Geo g = geo();
            float[] l = toLogical(g.rot, ev.getX(), ev.getY());
            removeCallbacks(flushBatch);
            if (inControlHalf(g, l[0], l[1])) {
                touchMode = T_CONTROL;
                curCancelled = false;
                downX = ev.getX(); downY = ev.getY();
                curStroke = null;
                rtActive = false;
                rtDown = false;
                float[] p = toScreen(g, l[0], l[1], false);
                if (p != null) {
                    if (listener != null && listener.realtimeAvailable()) {
                        rtActive = true;
                        rtDown = true;
                        listener.onRealtimeEvent(MotionEvent.ACTION_DOWN, p[0], p[1]);
                    } else {
                        curStroke = new Stroke();
                        curStroke.pts.add(new double[]{p[0], p[1], ev.getEventTime()});
                        if (listener != null) listener.onControlTouchStart();
                    }
                }
                return true;
            }
            // 快捷手势关闭：非可操控区域的触摸一律忽略（吞掉，不调节参数、不关闭分屏）
            touchMode = Prefs.gestures(prefs) ? T_ADJUST : T_IGNORE;
        }

        if (touchMode == T_CONTROL) {
            if (rtActive) handleRealtime(ev, action);
            else handleControl(ev, action);
            return true;
        }
        if (touchMode == T_IGNORE) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touchMode = T_NONE;
                if (!batch.isEmpty()) postDelayed(flushBatch, BATCH_QUIET_MS);
            }
            return true;
        }

        scaler.onTouchEvent(ev);
        gestures.onTouchEvent(ev);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchMode = T_NONE;
            if (!batch.isEmpty()) postDelayed(flushBatch, BATCH_QUIET_MS);
        }
        return true;
    }

    private void handleRealtime(MotionEvent ev, int action) {
        Geo g;
        float[] l, p;
        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
                if (rtDown) {
                    rtDown = false;
                    l = toLogical(geo().rot, ev.getX(0), ev.getY(0));
                    p = toScreen(geo(), l[0], l[1], true);
                    if (p != null) listener.onRealtimeEvent(MotionEvent.ACTION_CANCEL, p[0], p[1]);
                    showHint("可操控半屏暂不支持多指操作");
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!rtDown) break;
                g = geo();
                for (int h = 0; h < ev.getHistorySize(); h++) {
                    l = toLogical(g.rot, ev.getHistoricalX(0, h), ev.getHistoricalY(0, h));
                    p = toScreen(g, l[0], l[1], true);
                    if (p != null) listener.onRealtimeEvent(MotionEvent.ACTION_MOVE, p[0], p[1]);
                }
                l = toLogical(g.rot, ev.getX(0), ev.getY(0));
                p = toScreen(g, l[0], l[1], true);
                if (p != null) listener.onRealtimeEvent(MotionEvent.ACTION_MOVE, p[0], p[1]);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (rtDown) {
                    g = geo();
                    l = toLogical(g.rot, ev.getX(0), ev.getY(0));
                    p = toScreen(g, l[0], l[1], true);
                    float x = p != null ? p[0] : 0, y = p != null ? p[1] : 0;
                    listener.onRealtimeEvent(action == MotionEvent.ACTION_UP ? MotionEvent.ACTION_UP : MotionEvent.ACTION_CANCEL, x, y);
                }
                rtDown = false;
                rtActive = false;
                touchMode = T_NONE;
                break;
            default:
                break;
        }
    }

    private void handleControl(MotionEvent ev, int action) {
        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!curCancelled) {
                    curCancelled = true;
                    showHint("可操控半屏暂不支持多指操作");
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (curStroke != null && !curCancelled) {
                    if (curStroke.isTap && (Math.abs(ev.getX() - downX) > touchSlop || Math.abs(ev.getY() - downY) > touchSlop)) {
                        curStroke.isTap = false;
                    }
                    if (!curStroke.isTap) {
                        Geo g = geo();
                        for (int h = 0; h < ev.getHistorySize(); h++) {
                            addPoint(g, ev.getHistoricalX(0, h), ev.getHistoricalY(0, h), ev.getHistoricalEventTime(h));
                        }
                        addPoint(g, ev.getX(0), ev.getY(0), ev.getEventTime());
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (curStroke != null && !curCancelled) {
                    Geo g = geo();
                    if (curStroke.isTap) {
                        double[] p0 = curStroke.pts.get(0);
                        curStroke.pts.add(new double[]{p0[0], p0[1], ev.getEventTime()});
                    } else {
                        addPoint(g, ev.getX(), ev.getY(), ev.getEventTime());
                    }
                    batch.add(curStroke);
                }
                curStroke = null;
                touchMode = T_NONE;
                if (!batch.isEmpty()) postDelayed(flushBatch, BATCH_QUIET_MS);
                break;
            case MotionEvent.ACTION_CANCEL:
                curStroke = null;
                touchMode = T_NONE;
                if (!batch.isEmpty()) postDelayed(flushBatch, BATCH_QUIET_MS);
                break;
            default:
                break;
        }
    }

    private void addPoint(Geo g, float vx, float vy, long t) {
        float[] l = toLogical(g.rot, vx, vy);
        float[] p = toScreen(g, l[0], l[1], true);
        if (p != null) curStroke.pts.add(new double[]{p[0], p[1], t});
    }

    private void flushBatch() {
        if (touchMode != T_NONE) {
            // 手指还在屏幕上，稍后再回放
            postDelayed(flushBatch, 100);
            return;
        }
        if (batch.isEmpty()) return;
        List<Stroke> out = new ArrayList<>(batch);
        batch.clear();
        if (listener != null) listener.onInjectStrokes(out);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(flushBatch);
        batch.clear();
        super.onDetachedFromWindow();
    }
}
