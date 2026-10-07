package com.pingxingyan.split;

import android.view.SurfaceControl;

interface IInjector {
    /** 返回 true 表示注入成功。action 为 MotionEvent.ACTION_*。 */
    boolean injectTouch(int action, float x, float y, long downTime, long eventTime) = 1;
    /** 以 shell 身份给图层设置/清除 SKIP_SCREENSHOT（不出现在录屏/镜像中）。 */
    boolean setSkipScreenshot(in SurfaceControl sc, boolean skip) = 2;

    /** 多指触摸：action 为 ACTION_*（不含下标），index 为 POINTER_DOWN/UP 的手指下标。 */
    boolean injectMulti(int action, int index, in int[] ids, in float[] xs, in float[] ys, long downTime) = 3;

    /** Shizuku 约定的销毁方法号。 */
    void destroy() = 16777114;
}
