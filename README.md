# Parallel Eye Split
# 平行眼分屏

Mirrors your phone screen in real time as **two side-by-side copies**, each with a red focus ring below it, for free-view ("parallel eye") viewing — just like a parallel-view stereogram.
把手机当前屏幕实时复制成**左右两份**并排显示，每份下方有一个红色对焦圈，用“平行眼”（free-view，自由立体观看）的方式观看——就像看平行法立体图一样。

- The app underneath (e.g. Douyin/TikTok) **stays in portrait**, while the split view is **drawn in landscape**: hold the phone sideways and you see two upright pictures side by side.
- 下面的应用（例如抖音）**保持竖屏不变**，分屏画面**横着显示**：把手机横过来拿，就能看到左右两个竖着的画面。
- Pure native Android (Java, no third-party dependencies), minSdk 26 (Android 8.0), targetSdk 34, following the Android 10–15 screen-capture / foreground-service rules.
- 纯 Android 原生实现（Java，无第三方依赖），minSdk 26（Android 8.0），targetSdk 34，已按 Android 10–15 的录屏/前台服务规则适配。
- Screenshots: none yet.
- 截图：暂无。

**Download:** get the APK from the [Releases page](https://github.com/dennisehugh1-hash/parallel-eye-split/releases).
**下载：** 在 [Releases 页面](https://github.com/dennisehugh1-hash/parallel-eye-split/releases) 下载 APK。

> ⚠️ Note: parallel viewing is just a way of watching / a casual eye-relaxation exercise. **There is currently no reliable evidence that it prevents or improves myopia (nearsightedness).** See an eye doctor for vision problems, and stop immediately if your eyes feel strained or you feel dizzy.
> ⚠️ 说明：平行眼观看只是一种观看方式/眼部放松练习的玩法，**目前没有可靠证据证明它能预防或改善近视**。如有视力问题请咨询眼科医生；观看时如感到眼睛酸胀、头晕，请立即停止。

## Features
## 功能

- **Live mirroring**: MediaProjection + VirtualDisplay/ImageReader, half-resolution capture, about 30 fps.
- **实时镜像**：MediaProjection + VirtualDisplay/ImageReader，半分辨率抓屏，约 30 帧。
- **Split orientation**: Force landscape (default; the app stays portrait, the split view is rotated 90° and shown sideways) / Follow system orientation.
- **分屏方向**：强制横屏（默认，应用保持竖屏，分屏画面整体旋转 90° 横着显示）/ 跟随系统方向。
- **Landscape direction**: Auto (gravity sensor detects whether the phone is turned left or right) / Left / Right.
- **横屏方向**：自动（重力感应判断手机向左还是向右横拿）/ 向左 / 向右。
- **Picture fit**: Fill (crops edges to fill each half, default) / Fit whole picture (keeps aspect ratio, may leave black bars).
- **画面适配**：填满（裁切边缘，铺满半屏，默认）/ 完整显示（等比适配，可能留黑边）。
- **Adjustable settings** (saved automatically): separation between the two centres (millimetres, converted using the screen's physical DPI, default 60 mm), zoom (30%–200%), vertical offset.
- **可调参数**（自动保存）：两点间距（毫米，按屏幕物理 DPI 换算，默认 60 mm）、画面缩放（30%–200%）、垂直偏移。
- **Red focus rings**: semi-transparent red rings over the bottom of both pictures; can be hidden.
- **对焦红圈**：半透明红圈叠加在两个画面底部，可隐藏。
- **Floating button**: tap to toggle the split view, long-press to open settings, drag to move; the notification also has "Toggle split" and "Stop" buttons.
- **悬浮按钮**：单击开关分屏，长按打开设置，可拖动；通知栏也有“开启/关闭分屏”“停止”按钮。
- **Controllable half** (optional): via an accessibility service, taps, long-presses and swipes on the left/right half are mapped to the real screen position and replayed, so you can operate the app underneath while split.
- **可操控半屏**（可选）：通过无障碍服务，把在左/右半屏上的点按、长按、滑动映射到真实屏幕位置代为执行，分屏状态下也能操作下面的应用。
- **Refresh mode**: Live (about 30 fps; choose "A single app" when granting screen capture) / Interval refresh (works with "Entire screen", slight flicker).
- **刷新方式**：实时（约 30 帧，录屏时需选“单个应用”）/ 间歇刷新（整个屏幕也可用，会轻微闪烁）。

## Install & permissions
## 安装与权限

1. Download the APK from the [Releases page](https://github.com/dennisehugh1-hash/parallel-eye-split/releases) and install it (allow "Install unknown apps"). Requires Android 8.0+.
1. 从 [Releases 页面](https://github.com/dennisehugh1-hash/parallel-eye-split/releases) 下载 APK 安装（需允许“安装未知来源应用”）。需要 Android 8.0 及以上。
2. Open "平行眼分屏", tap "授予悬浮窗权限" (grant overlay permission), find the app in the list, turn on "Display over other apps", then go back.
2. 打开“平行眼分屏”，点“授予悬浮窗权限”，在列表中找到本应用，打开“显示在其他应用上层”，返回。
3. Tap "开始（申请录屏）" (Start). On Android 13+ you are first asked for notification permission; allowing it is recommended (for the "Stop" button in the notification) but not required.
3. 点“开始（申请录屏）”。Android 13 及以上会先询问通知权限，建议允许（通知栏的“停止”按钮），不允许也能用。
4. Confirm in the system screen-capture dialog:
4. 在系统录屏弹窗中确认：
   - If there is an "A single app" option, pick it and choose the app to watch → with "Live" mode there is no picture-in-picture feedback loop;
   - 有“单个应用”选项时，选它并选择要看的应用 → 配合“实时”模式，画面里不会套娃；
   - If you pick "Entire screen", switch the refresh mode to "Interval refresh".
   - 选“整个屏幕”时，请把刷新方式改为“间歇刷新”。
   - On Android 14+ you have to confirm again every time you start.
   - Android 14 及以上每次开始都需要重新确认。
5. Tap the floating "分屏" button to turn the split view on, then hold the phone sideways.
5. 点屏幕上的悬浮按钮“分屏”开启，把手机横过来观看。
6. (Optional) Controllable half: on the settings page choose left or right half, tap "开启无障碍服务" (open accessibility settings), find "平行眼分屏·可操控半屏" and turn it on.
6. （可选）可操控半屏：在设置页选择“左半屏可操控 / 右半屏可操控”，点“开启无障碍服务”，找到“平行眼分屏·可操控半屏”并打开。
   - If the switch is greyed out with a "Restricted setting" message (common for sideloaded apps on Android 13+, and on some Chinese ROMs): open "App info", tap the ⋮ menu at the top right → "Allow restricted settings", then go back and turn it on.
   - 若开关是灰色、提示“受限制的设置”（Android 13 起通过安装包安装的应用常见，部分国产系统也有类似限制）：进入“应用信息”，点右上角 ⋮ 菜单 →“允许受限制的设置”，再回去开启。
   - Some Chinese ROMs also require allowing the app under "Auto-start / Battery saver / Background running", otherwise the accessibility service may be switched off automatically.
   - 部分国产系统还需在“自启动 / 省电策略 / 后台运行”中允许本应用，否则无障碍服务可能被自动关闭。

How to view: relax your eyes and look into the distance until the two red rings merge into the middle one of three rings; you then see the fused picture.
观看方法：放松双眼看向远处，让两个红圈重合成三个圈中的中间一个，即可看到融合后的画面。

## Gestures
## 手势

Gestures on the split view (used directly on the picture while split is on; directions are as you see them holding the phone sideways):
分屏画面上的手势（分屏开启时直接在画面上操作；方向均以你横拿手机时看到的画面为准）：

- One-finger swipe left/right: adjust the separation (right = larger, left = smaller)
- 单指左右滑动：调节两点间距（向右变大，向左变小）
- One-finger swipe up/down: move the pictures and red dots up/down (vertical offset)
- 单指上下滑动：整体上下移动画面和红点（垂直偏移）
- Two-finger pinch / spread: zoom out / in (30%–200% on top of "Picture fit"; overflow is clipped within each half)
- 双指捏合 / 张开：缩小 / 放大画面（在“画面适配”基础上 30%～200%，超出部分在各自半屏内裁掉）
- One-finger double-tap: turn the split view off
- 单指双击：关闭分屏
- Floating button: tap to toggle split; long-press to close split and open settings; press and drag to move it
- 悬浮按钮：单击开启 / 关闭分屏；长按关闭分屏并打开本设置页；按住拖动可移动位置
- With "Controllable half" on: single-finger taps, long-presses and swipes on that half go to the app underneath instead of adjusting settings; the adjustment gestures above (including double-tap to close) only work on the other half
- 开启“可操控半屏”后：可操控那一半上的单指点按、长按、滑动会传给下面的应用，不会调节参数；以上调节手势（含双击关闭）只在另一半上有效

## Known limitations
## 已知限制

- While split is on, the overlay covers the whole screen and you cannot touch the app underneath directly (turn split off, or use "Controllable half").
- 分屏开启时画面覆盖整个屏幕，不能直接操作下面的应用（可关闭分屏，或使用“可操控半屏”）。
- When capturing the "Entire screen", the split overlay captures itself: Live mode shows a picture-in-picture loop, so use Interval refresh (the underlying app briefly shows each time a frame is taken). "Single app" sharing needs system support (Android 14 QPR2+/15; missing on some Chinese ROMs).
- 录“整个屏幕”时，分屏层会被自己录进去：实时模式会出现“画中画”套娃，需用间歇刷新（每次取画面会短暂露出底层应用）。“单个应用”共享需要系统支持（Android 14 QPR2+/15，部分国产系统没有）。
- The status bar / navigation bar usually cannot be hidden by an overlay, so the picture is drawn underneath the system bars.
- 状态栏/导航栏通常无法被悬浮窗隐藏，画面会画在系统栏下方。
- Controllable half: replay starts about 0.3 s after lifting the finger (longer drags mean longer delay), no multi-touch; the split view dims briefly during replay and touching the screen then interrupts it; some apps that block obscured touches (banking/payment) may not respond; coordinates may be off when the app is in split-screen or a floating window.
- 可操控半屏：手指抬起约 0.3 秒后才回放（拖动越久延迟越长），不支持多指；回放期间分屏画面会短暂变淡，此时触摸屏幕会中断回放；部分禁止遮挡触摸的应用（银行/支付类）可能不响应；应用处于分屏/小窗时坐标可能不准。
- Apps that block screen capture (banking, some premium video content, etc.) appear black.
- 禁止录屏的应用（银行、部分视频会员内容等）画面为黑色。
- The millimetre conversion relies on the screen DPI reported by the manufacturer and may be inaccurate.
- 毫米换算依赖厂商上报的屏幕 DPI，可能有偏差。
- When the phone lies flat, the auto landscape direction cannot be detected and the last direction is kept; you can fix it manually in settings.
- 手机平放时自动横屏方向无法判断，会沿用上一次的方向，可在设置中手动固定。
- If system auto-rotate is on and the app underneath supports landscape, the system may rotate it and you capture the app's landscape UI; lock screen rotation to keep it portrait.
- 若系统自动旋转开启且下面的应用支持横屏，系统可能自己转成横屏，此时截到的是应用的横屏界面；想保持竖屏可锁定屏幕旋转。

## Build
## 编译

Requires JDK 17 and the Android SDK (platform android-34, build-tools 34.0.0).
需要 JDK 17、Android SDK（platform android-34、build-tools 34.0.0）。

```bash
echo "sdk.dir=/path/to/android/sdk" > local.properties   # or set ANDROID_HOME / 或设置 ANDROID_HOME
./gradlew assembleDebug
# Output / 输出: app/build/outputs/apk/debug/app-debug.apk
```

Toolchain: Android Gradle Plugin 8.5.2, Gradle 8.7 (via the included wrapper), plain framework APIs (no AndroidX).
工具链：Android Gradle Plugin 8.5.2、Gradle 8.7（使用仓库自带的 wrapper）、纯框架 API（无 AndroidX）。

## License
## 许可证

[MIT](LICENSE) © 2026 dennisehugh1-hash
本项目以 [MIT](LICENSE) 许可证发布 © 2026 dennisehugh1-hash

---

## 技术说明 / Design notes

以下为各版本的实现要点（英文）。后面版本的说明会覆盖前面的做法。

v1.0 (versionCode 1)
- Consent (createScreenCaptureIntent, API34: createConfigForUserChoice) is obtained in MainActivity first, then the FGS
  (type mediaProjection) is started and calls startForeground() before getMediaProjection(). New consent every session.
- Callback registered before the single createVirtualDisplay(); rotation handled with VirtualDisplay.resize()+setSurface()
  (API34+: onCapturedContentResize). Capture at half resolution, RGBA_8888 ImageReader, ~30fps cap, double-buffered bitmaps.
- Landscape (superseded in v1.2/v1.3): overlay LayoutParams.screenOrientation = SENSOR_LANDSCAPE; if the view still
  ended up portrait, SplitView rotated its drawing 90°.
- Feedback loop: FLAG_SECURE is NOT used. A secure layer is blacked out (filled black over its bounds) in a non-secure
  MediaProjection display, so a full-screen secure overlay makes the entire capture black. Instead:
  (a) 实时 mode: pick "单个应用" in the Android 14 QPR2+/15 consent dialog -> the overlay isn't part of the captured task.
  (b) 间歇刷新 mode: every N ms the overlay draws fully transparent (window stays visible so the landscape request holds)
      for ~180 ms, frames captured in that window are published, then the overlay is shown again (brief flicker).

v1.1 (versionCode 2)
- Pane size 30–200% (slider + pinch). (v1.1 clipped each pane to a separation-wide strip; v1.2 changed this to
  half-screen windows.)
- 可操控半屏 (off/left/right): single-finger strokes on the chosen pane are recorded, mapped view -> logical (inverse
  rotation) -> pane-normalised -> capture-frame px -> real display px (inverse of VirtualDisplay aspect-fit), batched
  until 250 ms of quiet after the last finger-up, then replayed by GestureService.dispatchGesture. Taps/long-presses/short
  swipes go into one GestureDescription (relative timing kept, so in-app double-taps work); a single drag >= 250 ms is
  replayed as 120 ms segments chained with StrokeDescription.continueStroke (keeps velocity profile, e.g. flings).
  Real touches cancel any in-flight injected gesture (MotionEventInjector), so live streaming is impossible -> replay.
- Self-hit avoidance: during replay the overlay + bubble get FLAG_NOT_TOUCHABLE and window alpha is lowered to
  InputManager.getMaximumObscuringOpacityForTouch() - 0.05 (≈0.75) on Android 12+, because untrusted-touch blocking
  drops touches that pass through an opaque third-party overlay; SAW windows with alpha <= 0.8 are exempt. Bubble alpha 0.
  The windows stay visible, so the landscape orientation request is unaffected. Restored on completion/cancel (+3 s safety).

v1.2 (versionCode 3)
- No forced landscape; overlay follows the current display orientation. Capture VD is resized to the current real
  display size (Display.getRealSize) on DisplayListener.onDisplayChanged / onConfigurationChanged; on API 34+
  onCapturedContentResize wins (single-app capture keeps the app's own aspect).
- Layout: each pane window = exact half of the screen width x full height, content centred on its dot.
  画面适配: 填满 (default; scale = max(2*max(cx, half-cx)/fw, 2*max(cy, H-cy)/fh) so no black holes even with
  separation/vertical offset) or 完整显示 (min(half/fw, H/fh)); zoom slider/pinch multiplies that.
- Dots: semi-transparent ring + tiny centre overlaid 6 mm above the pane's visible bottom; can be hidden.
- Overlay: fitInsetsTypes(0), cutout mode ALWAYS (API30+) / SHORT_EDGES (28-29), tries to hide system bars
  (usually ignored for non-focusable overlays -> content is drawn under the bars).

v1.3 (versionCode 4)
- 分屏方向: 强制横屏 (default) / 跟随系统方向. Forced mode never requests a display rotation; if the overlay view is
  portrait, SplitView draws in a rotated logical landscape frame: +90 (向左, phone turned CCW, top to the left:
  translate(W,0)·rotate(90)) or -90 (向右, CW: translate(0,H)·rotate(-90)). 横屏方向: 自动 (OrientationEventListener,
  60–120° -> 向右, 240–300° -> 向左, dead zones keep last) / 向左 / 向右.
- Capture stays in the real display orientation; panes draw it upright in the logical frame. Geometry, gestures
  (scroll deltas rotated into logical axes), hint text, dots and controllable-pane mapping (view -> logical inverse ->
  pane uv -> frame -> display) all use the logical frame. Floating-button text is rotated to match (setRotation).
