# Parallel Eye Split

# 平行眼分屏

Mirrors your phone screen in real time as **two side-by-side copies** with red focus rings, for free-view ("parallel eye") viewing.

把手机屏幕实时复制成**左右两份**并排显示（带红色对焦圈），用“平行眼”方式观看。

- The app underneath (e.g. Douyin) stays portrait; the split view is drawn in landscape — hold the phone sideways.
- 下面的应用（如抖音）保持竖屏，分屏画面横着显示——横拿手机观看。
- Native Android (Java), Android 8.0+ (minSdk 26, targetSdk 34).
- 原生 Android（Java），Android 8.0 及以上（minSdk 26，targetSdk 34）。

**Download:** [Releases](https://github.com/dennisehugh1-hash/parallel-eye-split/releases)

**下载：** [Releases 页面](https://github.com/dennisehugh1-hash/parallel-eye-split/releases)

> ⚠️ Parallel viewing is just a way of watching; there is no reliable evidence it prevents or improves myopia. Stop if your eyes feel strained.
>
> ⚠️ 平行眼只是一种观看方式，没有可靠证据表明能预防或改善近视。眼睛不适请立即停止。

## Features (v2.0)

## 功能（v2.0）

- **Picture:** resolution 100% / 75% / 50%, frame rate follow-screen / 60 / 30, GPU direct drawing (default 75% · 60 fps · GPU on). "Follow screen" also requests the display's highest refresh rate.
- **画面：** 分辨率 100% / 75% / 50%，帧率 跟随屏幕 / 60 / 30，GPU 直接绘制（默认 75% · 60 帧 · GPU 开）。选“跟随屏幕”时同时申请屏幕最高刷新率。
- **Split:** separation (mm), zoom 30–200%, vertical offset, fill / fit, red rings, forced landscape (auto / left / right) or follow system.
- **分屏：** 两点间距（毫米）、缩放 30–200%、垂直偏移、填满 / 完整显示、红圈、强制横屏（自动 / 向左 / 向右）或跟随系统。
- **Quick gestures** (switch): swipe left/right = separation, up/down = offset, pinch = zoom, double-tap = close.
- **快捷手势**（开关）：左右滑调间距、上下滑调偏移、双指缩放、双击关闭。
- **Operable half** — mode: Off / Accessibility (replay after lift) / Shizuku (real-time); left or right half. Combines freely with quick gestures (4 combinations).
- **可操控半屏** —— 操控方式：关闭 / 无障碍（松手后回放）/ Shizuku（实时）；可选左或右半屏。与快捷手势自由组合（4 种情况）。
- **Full-screen capture (experimental)** (one switch): always capture the entire screen and try to exclude the split overlay from the recording; falls back to interval refresh if exclusion fails.
- **整屏捕获（实验）**（一个开关）：始终录整个屏幕，并尝试把分屏层排除出录屏；排除失败时自动改用间歇刷新。
- **Refresh:** live / interval; floating button (tap = toggle, long-press = settings, drag to move); FPS counter for debugging.
- **刷新：** 实时 / 间歇；悬浮按钮（单击开关、长按设置、可拖动）；调试用帧率显示。

## Quick start

## 快速开始

1. Install the APK, grant "Display over other apps".
1. 安装 APK，授予“显示在其他应用上层”。
2. Tap "开始", choose **"A single app"** in the capture dialog (no feedback loop).
2. 点“开始”，录屏弹窗选**“单个应用”**（不会套娃）。
3. Tap the floating "分屏" button, hold the phone sideways, relax your eyes until the two red rings merge.
3. 点悬浮按钮“分屏”，横拿手机，放松双眼让两个红圈重合。

## Shizuku real-time control

## Shizuku 实时操控

1. Install [Shizuku](https://shizuku.rikka.app/) and start it (Wireless debugging on Android 11+, or root).
1. 安装 [Shizuku](https://shizuku.rikka.app/) 并启动（Android 11+ 用无线调试，或 root）。
2. In the app choose 操控方式 → "Shizuku 实时" and tap "Allow" in the Shizuku prompt.
2. 在本应用选 操控方式 →“Shizuku 实时”，在 Shizuku 弹窗中点“允许”。
3. After a reboot, start Shizuku again; until then the app falls back to accessibility replay.
3. 重启手机后需重新启动 Shizuku；未就绪时自动退回无障碍回放。

Accessibility mode: if the switch is greyed out ("Restricted setting"), open App info → ⋮ → "Allow restricted settings".

无障碍模式：开关为灰色（“受限制的设置”）时，进入应用信息 → ⋮ →“允许受限制的设置”。

## Known limitations

## 已知限制

- **Keyboard / notifications:** on Android 14+ the system may hide the keyboard and notifications from screen sharing (screen-share protection). On Android 15 you can try Developer options → "Disable screen share protections".
- **键盘 / 通知：** Android 14+ 系统可能在录屏共享中隐藏键盘和通知（屏幕共享保护）。Android 15 可尝试开发者选项 →“停用屏幕共享保护”（Disable screen share protections）。
- Capturing the entire screen records the overlay itself (feedback loop) unless exclusion works; use interval refresh then.
- 录整个屏幕会把分屏层录进去（套娃），除非排除生效；此时请用间歇刷新。
- Operable half is single-finger only; the view dims slightly while touching (Android touch pass-through rule).
- 可操控半屏仅支持单指；触摸时画面会略变淡（Android 触摸穿透规则）。
- Apps that block screen capture appear black; millimetre conversion depends on the reported screen DPI.
- 禁止录屏的应用显示为黑色；毫米换算依赖厂商上报的屏幕 DPI。

## Build

## 编译

JDK 17 + Android SDK (platform 34, build-tools 34.0.0):

JDK 17 + Android SDK（platform 34、build-tools 34.0.0）：

```bash
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

Dependencies: Shizuku API/provider 13.1.5, HiddenApiBypass 6.1 (only for the experimental overlay exclusion).

依赖：Shizuku API/provider 13.1.5、HiddenApiBypass 6.1（仅用于实验性的分屏层排除）。

## License

## 许可证

[MIT](LICENSE) © 2026 dennisehugh1-hash

本项目以 [MIT](LICENSE) 许可证发布 © 2026 dennisehugh1-hash
