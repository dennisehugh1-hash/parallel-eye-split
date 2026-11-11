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

## Features (v2.4)

## 功能（v2.4）

- **Picture:** resolution 100% / 75% / 50%, frame rate follow-screen / 60 / 30, GPU direct drawing (default 75% · 60 fps · GPU on). "Follow screen" also requests the display's highest refresh rate.
- **画面：** 分辨率 100% / 75% / 50%，帧率 跟随屏幕 / 60 / 30，GPU 直接绘制（默认 75% · 60 帧 · GPU 开）。选“跟随屏幕”时同时申请屏幕最高刷新率。
- **Split:** separation (mm), zoom 30–200%, vertical offset, fill / fit, red rings, forced landscape (auto / left / right) or follow system.
- **分屏：** 两点间距（毫米）、缩放 30–200%、垂直偏移、填满 / 完整显示、红圈、强制横屏（自动 / 向左 / 向右）或跟随系统。
- **Quick gestures** (switch): swipe left/right = separation, up/down = offset, pinch = zoom, double-tap = close.
- **快捷手势**（开关）：左右滑调间距、上下滑调偏移、双指缩放、双击关闭。
- **Operable half** — mode: Off / Accessibility (replay after lift) / Shizuku (real-time); left or right half. Combines freely with quick gestures (4 combinations).
- **可操控半屏** —— 操控方式：关闭 / 无障碍（松手后回放）/ Shizuku（实时）；可选左或右半屏。与快捷手势自由组合（4 种情况）。
- **Keyboard:** "显示输入法键盘" is on by default. The IME is a system window, so single-app capture leaves a black band. The app now captures the whole display and tries to keep the split overlay out of the recording. On Android 15, if the keys are still black and Shizuku is authorized, screen-share protection is turned off only for the session and restored when you stop.
- **输入法：** “显示输入法键盘”默认开启。输入法是系统窗口，选“单个应用”时键盘区域会是黑的。现在默认录整个屏幕，并尽量把分屏层排除出录屏。Android 15 上若键盘仍是黑的、且已授权 Shizuku，只在本次分屏期间临时关闭屏幕共享保护，停止后自动恢复。
- **Full-screen capture (experimental)** (one switch): always capture the entire screen and try to exclude the split overlay from the recording; falls back to interval refresh if exclusion fails.
- **整屏捕获（实验）**（一个开关）：始终录整个屏幕，并尝试把分屏层排除出录屏；排除失败时自动改用间歇刷新。
- **Refresh:** live / interval; floating button (tap = toggle, long-press = settings, drag to move); FPS counter for debugging.
- **刷新：** 实时 / 间歇；悬浮按钮（单击开关、长按设置、可拖动）；调试用帧率显示。

## What's new in v2.4

## v2.4 更新

- Keyboard is included in both copies. Default capture is the whole screen (not a single app), because the IME is not part of the app window.
- 键盘会出现在左右两幅画面里。默认改为录整个屏幕，不再默认“单个应用”——输入法不在应用窗口里。
- If Shizuku is already allowed, Android 14+ screen-share protection is disabled only while a session runs, then restored (also after a crash, the next time you open the app).
- 若已经允许 Shizuku，Android 14+ 的屏幕共享保护只在分屏会话期间临时关闭，停止后恢复（异常退出后，下次打开应用也会恢复）。
- v2.3 is kept on branch `archive/v2.3` and as the [v2.3 APK](https://github.com/dennisehugh1-hash/parallel-eye-split/releases/download/v2.3/ParallelEyeSplit-v2.3.apk).
- v2.3 留在分支 `archive/v2.3`，安装包仍是 [v2.3 APK](https://github.com/dennisehugh1-hash/parallel-eye-split/releases/download/v2.3/ParallelEyeSplit-v2.3.apk)。

## What's new in v2.3

## v2.3 更新

- Settings: 权限 card moved to the top (first module).
- 设置页：权限卡片移到最上方（首个模块）。

## What's new in v2.2

## v2.2 更新

- Fixed a crash when drawing GPU frames on a software canvas (`Software rendering doesn't support hardware bitmaps`).
- 修复 GPU 帧在软件 Canvas 上绘制时崩溃（`Software rendering doesn't support hardware bitmaps`）。
- Settings layout: 权限 → 交互操控 → 画面与布局 → 采集与刷新 → 关于与调试.
- 设置页顺序：权限 → 交互操控 → 画面与布局 → 采集与刷新 → 关于与调试.
- Hardened handling when the system ends screen capture (floating button stays; one-tap re-authorize).
- 系统结束录屏时更稳妥（悬浮按钮保留，一点即可重新授权）。

## Quick start

## 快速开始

1. Install the APK, grant "Display over other apps".
1. 安装 APK，授予“显示在其他应用上层”。
2. Tap "开始" and allow **"Entire screen"** (not a single app — a single app does not include the keyboard).
2. 点“开始”，允许**“整个屏幕”**（不要选单个应用，否则键盘是黑的）。
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

- **Keyboard / notifications:** single-app sharing never includes the IME, so the keyboard area is black until you capture the whole screen. On Android 15 the system can still black out the keyboard unless screen-share protections are off. This app turns that developer setting off only while splitting, via Shizuku, and turns it back on when you stop. Without Shizuku, open Developer options → "Disable screen share protections" yourself.
- **键盘 / 通知：** 单个应用共享不含输入法，键盘区域会是黑的，必须录整个屏幕。Android 15 仍可能把键盘涂黑，除非关掉屏幕共享保护。本应用在已授权 Shizuku 时只在分屏期间临时关闭该设置，停止后恢复。没有 Shizuku 时，请自己打开开发者选项 →“停用屏幕共享保护”。
- Capturing the entire screen records the overlay itself (feedback loop) unless exclusion works; use interval refresh then.
- 录整个屏幕会把分屏层录进去（套娃），除非排除生效；此时请用间歇刷新。
- Operable half supports multi-touch (up to 5 fingers); the view dims slightly while touching (Android touch pass-through rule).
- 可操控半屏支持多指（最多 5 指）；触摸时画面会略变淡（Android 触摸穿透规则）。
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
