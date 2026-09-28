package com.pingxingyan.split;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 无障碍服务：只用于 dispatchGesture 回放“可操控半屏”上的操作。不读取任何窗口内容。
 */
public class GestureService extends AccessibilityService {
    private static final String TAG = "ParallelEyeA11y";
    /** 超过这个时长的单条拖动用 continueStroke 分段回放，保留原始速度变化（例如快速甩动）。 */
    private static final long CHAIN_MIN_MS = 250;
    private static final long SEGMENT_MS = 120;
    private static final int MAX_PATH_POINTS = 40;

    public static volatile GestureService instance;

    public static boolean isEnabledInSettings(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        ComponentName cn = new ComponentName(c, GestureService.class);
        return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString());
    }

    public static boolean isRunning() { return instance != null; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    /** 返回预计总时长（毫秒），-1 表示无法回放。done 在主线程回调一次。 */
    public long play(List<SplitView.Stroke> strokes, Runnable done) {
        if (strokes == null || strokes.isEmpty()) return -1;
        try {
            if (strokes.size() == 1 && !strokes.get(0).isTap
                    && strokes.get(0).upTime() - strokes.get(0).downTime() >= CHAIN_MIN_MS) {
                return playChained(strokes.get(0), done);
            }
            return playBatch(strokes, done);
        } catch (Exception e) {
            Log.w(TAG, "play failed", e);
            return -1;
        }
    }

    /** 多条操作（点按/长按/短滑动）合成一个手势，保留相对时间（例如应用内的双击）。 */
    private long playBatch(List<SplitView.Stroke> strokes, Runnable done) {
        GestureDescription.Builder b = new GestureDescription.Builder();
        int maxStrokes = GestureDescription.getMaxStrokeCount();
        long maxDur = GestureDescription.getMaxGestureDuration();
        long t0 = strokes.get(0).downTime();
        long total = 0;
        int n = 0;
        for (SplitView.Stroke s : strokes) {
            if (n >= maxStrokes) break;
            long start = s.downTime() - t0;
            long dur = Math.max(s.isTap ? 30 : 10, s.upTime() - s.downTime());
            if (start + dur > maxDur) break;
            b.addStroke(new GestureDescription.StrokeDescription(pathOf(s.pts, 0, s.pts.size(), s.isTap), start, dur));
            total = Math.max(total, start + dur);
            n++;
        }
        if (n == 0) return -1;
        boolean ok = dispatchGesture(b.build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { done.run(); }
            @Override public void onCancelled(GestureDescription g) { done.run(); }
        }, null);
        return ok ? total : -1;
    }

    /** 单条较长拖动：按时间切成若干段，用 continueStroke 串起来（手指在段之间不抬起）。 */
    private long playChained(SplitView.Stroke s, Runnable done) {
        ArrayList<double[]> pts = s.pts;
        // 按时间分段，每段的起点 = 上一段的终点
        ArrayList<int[]> segs = new ArrayList<>();
        int segStart = 0;
        for (int i = 1; i < pts.size(); i++) {
            boolean last = i == pts.size() - 1;
            if (last || pts.get(i)[2] - pts.get(segStart)[2] >= SEGMENT_MS) {
                segs.add(new int[]{segStart, i});
                segStart = i;
            }
        }
        if (segs.isEmpty()) return playBatch(java.util.Collections.singletonList(s), done);
        long total = s.upTime() - s.downTime();
        if (total > GestureDescription.getMaxGestureDuration()) return -1;
        dispatchSegment(pts, segs, 0, null, done);
        return total;
    }

    private void dispatchSegment(ArrayList<double[]> pts, ArrayList<int[]> segs, int idx,
                                 GestureDescription.StrokeDescription prev, Runnable done) {
        int[] seg = segs.get(idx);
        boolean willContinue = idx < segs.size() - 1;
        long dur = Math.max(1, (long) (pts.get(seg[1])[2] - pts.get(seg[0])[2]));
        Path path = pathOf(pts, seg[0], seg[1] + 1, false);
        GestureDescription.StrokeDescription sd = prev == null
                ? new GestureDescription.StrokeDescription(path, 0, dur, willContinue)
                : prev.continueStroke(path, 0, dur, willContinue);
        GestureDescription g = new GestureDescription.Builder().addStroke(sd).build();
        boolean ok = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription d) {
                if (willContinue) {
                    try {
                        dispatchSegment(pts, segs, idx + 1, sd, done);
                    } catch (Exception e) {
                        Log.w(TAG, "segment failed", e);
                        done.run();
                    }
                } else {
                    done.run();
                }
            }
            @Override public void onCancelled(GestureDescription d) { done.run(); }
        }, null);
        if (!ok) done.run();
    }

    /** 从点列 [from, to) 生成路径；点多时均匀抽样。 */
    private static Path pathOf(List<double[]> pts, int from, int to, boolean tap) {
        Path p = new Path();
        double[] first = pts.get(from);
        p.moveTo((float) Math.max(0, first[0]), (float) Math.max(0, first[1]));
        if (tap) return p;
        int count = to - from;
        int step = Math.max(1, (int) Math.ceil(count / (double) MAX_PATH_POINTS));
        for (int i = from + step; i < to; i += step) {
            double[] q = pts.get(i);
            p.lineTo((float) Math.max(0, q[0]), (float) Math.max(0, q[1]));
        }
        double[] last = pts.get(to - 1);
        if (to - 1 > from && (to - 1 - from) % step != 0) p.lineTo((float) Math.max(0, last[0]), (float) Math.max(0, last[1]));
        return p;
    }
}
