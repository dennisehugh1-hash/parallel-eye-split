package com.pingxingyan.split;

interface IInjector {
    /** 返回 true 表示注入成功。action 为 MotionEvent.ACTION_*。 */
    boolean injectTouch(int action, float x, float y, long downTime, long eventTime) = 1;
    /** Shizuku 约定的销毁方法号。 */
    void destroy() = 16777114;
}
