package com.pingxingyan.split;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 100;
    private static final int REQ_NOTIF = 101;

    private SharedPreferences prefs;
    private TextView status, stCapture, stOverlay, stNotif, stShizuku, comboHint, realtimeHelp, lblSep, lblSize, lblOffset, lblInterval;
    private SeekBar seekSep, seekSize, seekOffset, seekInterval;
    private RadioGroup modeGroup, controlGroup, fitGroup, orientGroup, dirGroup;
    private android.widget.CheckBox chkDots;
    private android.widget.Switch swGestures, swControl, swRealtime;
    private Button btnShizuku, btnA11yBtn, btnOverlay, btnStart2;
    private final Runnable shizukuListener = this::refreshStatus;
    private TextView a11yStatus;
    private Button btnNotif;
    private boolean pendingStartAfterNotif = false;
    private boolean updatingUi = false;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> { loadUi(); refreshStatus(); };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = Prefs.get(this);

        status = findViewById(R.id.status);
        stCapture = findViewById(R.id.stCapture);
        stOverlay = findViewById(R.id.stOverlay);
        stNotif = findViewById(R.id.stNotif);
        stShizuku = findViewById(R.id.stShizuku);
        comboHint = findViewById(R.id.comboHint);
        realtimeHelp = findViewById(R.id.realtimeHelp);
        swGestures = findViewById(R.id.swGestures);
        swControl = findViewById(R.id.swControl);
        swRealtime = findViewById(R.id.swRealtime);
        btnShizuku = findViewById(R.id.btnShizuku);
        btnA11yBtn = findViewById(R.id.btnA11y);
        btnOverlay = findViewById(R.id.btnOverlayPerm);
        btnStart2 = findViewById(R.id.btnStart2);
        ((TextView) findViewById(R.id.version)).setText("平行眼分屏 v" + BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）");
        swGestures.setOnCheckedChangeListener((b, c) -> {
            if (updatingUi) return;
            prefs.edit().putBoolean(Prefs.K_GESTURES, c).apply();
        });
        swControl.setOnCheckedChangeListener((b, c) -> {
            if (updatingUi) return;
            prefs.edit().putInt(Prefs.K_CONTROL_PANE, c ? Prefs.controlSide(prefs) : Prefs.CONTROL_OFF).apply();
            if (c && !GestureService.isRunning() && !ShizukuHelper.get(this).isReady()) {
                Toast.makeText(this, "还需开启无障碍服务“平行眼分屏·可操控半屏”（或使用 Shizuku 实时操控）才能生效", Toast.LENGTH_LONG).show();
            }
        });
        swRealtime.setOnCheckedChangeListener((b, c) -> {
            if (updatingUi) return;
            prefs.edit().putBoolean(Prefs.K_REALTIME, c).apply();
            ShizukuHelper h = ShizukuHelper.get(this);
            if (c) {
                if (!h.granted()) {
                    if (!h.requestPermission()) Toast.makeText(this, "Shizuku 未运行：请先安装并启动 Shizuku，未就绪时将使用无障碍回放", Toast.LENGTH_LONG).show();
                } else h.ensureBound();
            } else {
                h.unbind();
            }
            refreshStatus();
        });
        btnShizuku.setOnClickListener(v -> {
            ShizukuHelper h = ShizukuHelper.get(this);
            if (!h.requestPermission()) openShizukuApp();
        });
        findViewById(R.id.btnShizukuApp).setOnClickListener(v -> openShizukuApp());
        findViewById(R.id.btnStart2).setOnClickListener(v -> startFlow());
        lblSep = findViewById(R.id.lblSep);
        lblSize = findViewById(R.id.lblSize);
        lblOffset = findViewById(R.id.lblOffset);
        lblInterval = findViewById(R.id.lblInterval);
        seekSep = findViewById(R.id.seekSep);
        seekSize = findViewById(R.id.seekSize);
        seekOffset = findViewById(R.id.seekOffset);
        seekInterval = findViewById(R.id.seekInterval);
        modeGroup = findViewById(R.id.modeGroup);
        btnNotif = findViewById(R.id.btnNotifPerm);
        controlGroup = findViewById(R.id.controlGroup);
        fitGroup = findViewById(R.id.fitGroup);
        orientGroup = findViewById(R.id.orientGroup);
        dirGroup = findViewById(R.id.dirGroup);
        orientGroup.setOnCheckedChangeListener((g, id) -> {
            if (updatingUi) return;
            prefs.edit().putInt(Prefs.K_SPLIT_ORIENT, id == R.id.orientFollow ? Prefs.ORIENT_FOLLOW : Prefs.ORIENT_FORCE_LANDSCAPE).apply();
        });
        dirGroup.setOnCheckedChangeListener((g, id) -> {
            if (updatingUi) return;
            int v = id == R.id.dirLeft ? Prefs.DIR_LEFT : id == R.id.dirRight ? Prefs.DIR_RIGHT : Prefs.DIR_AUTO;
            prefs.edit().putInt(Prefs.K_LAND_DIR, v).apply();
        });
        chkDots = findViewById(R.id.chkDots);
        fitGroup.setOnCheckedChangeListener((g, id) -> {
            if (updatingUi) return;
            prefs.edit().putInt(Prefs.K_FIT_MODE, id == R.id.fitContain ? Prefs.FIT_CONTAIN : Prefs.FIT_FILL).apply();
        });
        chkDots.setOnCheckedChangeListener((b, c) -> {
            if (updatingUi) return;
            prefs.edit().putBoolean(Prefs.K_SHOW_DOTS, c).apply();
        });
        a11yStatus = findViewById(R.id.a11yStatus);

        // 间距：0.5mm 步进
        seekSep.setMax(Math.round((Prefs.MAX_SEP_MM - Prefs.MIN_SEP_MM) * 2));
        seekSize.setMax(Prefs.MAX_SIZE_PCT - Prefs.MIN_SIZE_PCT);
        seekOffset.setMax(Math.round(Prefs.MAX_OFFSET_MM * 2 * 2));
        seekInterval.setMax((Prefs.MAX_INTERVAL_MS - Prefs.MIN_INTERVAL_MS) / 100);

        seekSep.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override void changed(int v) { prefs.edit().putFloat(Prefs.K_SEP_MM, Prefs.MIN_SEP_MM + v / 2f).apply(); }
        });
        seekSize.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override void changed(int v) { prefs.edit().putInt(Prefs.K_SIZE_PCT, Prefs.MIN_SIZE_PCT + v).apply(); }
        });
        seekOffset.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override void changed(int v) { prefs.edit().putFloat(Prefs.K_OFFSET_MM, v / 2f - Prefs.MAX_OFFSET_MM).apply(); }
        });
        seekInterval.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override void changed(int v) { prefs.edit().putInt(Prefs.K_INTERVAL_MS, Prefs.MIN_INTERVAL_MS + v * 100).apply(); }
        });
        modeGroup.setOnCheckedChangeListener((g, id) -> {
            if (updatingUi) return;
            prefs.edit().putInt(Prefs.K_MODE, id == R.id.modeLive ? Prefs.MODE_LIVE : Prefs.MODE_INTERVAL).apply();
        });

        controlGroup.setOnCheckedChangeListener((g, id) -> {
            if (updatingUi) return;
            int side = id == R.id.controlLeft ? Prefs.CONTROL_LEFT : Prefs.CONTROL_RIGHT;
            SharedPreferences.Editor e = prefs.edit().putInt(Prefs.K_CONTROL_SIDE, side);
            if (Prefs.controlPane(prefs) != Prefs.CONTROL_OFF) e.putInt(Prefs.K_CONTROL_PANE, side);
            e.apply();
        });
        findViewById(R.id.btnA11y).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                Toast.makeText(this, "在“已下载的应用/已安装的服务”中找到“平行眼分屏·可操控半屏”并开启", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btnAppInfo).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开应用信息", Toast.LENGTH_SHORT).show();
            }
        });
        ((TextView) findViewById(R.id.gestureHelp)).setText(GESTURE_HELP);
        ((TextView) findViewById(R.id.controlHelp)).setText(CONTROL_HELP);

        findViewById(R.id.btnOverlayPerm).setOnClickListener(v -> openOverlaySettings());
        btnNotif.setOnClickListener(v -> requestNotif(false));
        findViewById(R.id.btnStart).setOnClickListener(v -> startFlow());
        findViewById(R.id.btnStop).setOnClickListener(v -> {
            if (ProjectionService.running) {
                startService(new Intent(this, ProjectionService.class).setAction(ProjectionService.ACTION_STOP));
            }
            status.postDelayed(this::refreshStatus, 300);
        });
        findViewById(R.id.btnToggle).setOnClickListener(v -> {
            if (!ProjectionService.running) {
                Toast.makeText(this, "请先点“开始”并允许录屏", Toast.LENGTH_SHORT).show();
                return;
            }
            startService(ProjectionService.toggleIntent(this));
            if (!ProjectionService.splitOnPublic) moveTaskToBack(true);
        });
        findViewById(R.id.btnReset).setOnClickListener(v -> Prefs.reset(prefs));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        ((TextView) findViewById(R.id.help)).setText(
                "使用说明\n"
                + "1. 先授予“显示在其他应用上层”（悬浮窗）权限；Android 13 及以上建议允许通知（用于显示“停止”按钮）。\n"
                + "2. 点“开始”，在系统录屏弹窗中确认。Android 14 及以上每次开始都需要重新确认。\n"
                + "   · 选“单个应用”：画面里不会出现本应用的分屏层，可用“实时”模式（约30帧）。\n"
                + "   · 选“整个屏幕”：分屏层会被自己录进去形成套娃，请用“间歇刷新”模式。\n"
                + "3. 点屏幕上的悬浮按钮“分屏”开启；再点“关闭”（或在开启快捷手势时双击分屏画面）关闭。长按悬浮按钮打开本设置页。悬浮按钮可拖动。\n"
                + "4. 默认“强制横屏”：下面的应用（如抖音）保持竖屏不变，分屏画面横着显示——把手机横过来拿，就能看到左右两个竖着的画面。"
                + "横拿方向默认按重力感应自动判断，也可在“横屏方向”里固定为向左或向右。选“跟随系统方向”则分屏画面与系统方向一致。\n"
                + "5. 观看方法（平行眼）：放松双眼看向远处，让两个红点重合成三个点中的中间一个，即可看到融合后的画面。\n\n"
                + "注意事项\n"
                + "· 分屏开启时画面覆盖整个屏幕，无法直接操作下面的应用；可先关闭分屏，或使用“交互操控”里的“可操控半屏”。\n"
                + "· 部分应用（银行、视频会员等）禁止录屏，画面会是黑色。\n"
                + "· 间歇刷新模式下每次取画面时会短暂露出底层应用（轻微闪烁）。\n"
                + String.format(java.util.Locale.ROOT, "· 本机屏幕物理密度约 %.0f×%.0f dpi，毫米换算据此计算，若厂商数据不准，实际距离可能有偏差。", dm.xdpi, dm.ydpi));
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        ShizukuHelper.get(this).addListener(shizukuListener);
        loadUi();
        refreshStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        ShizukuHelper.get(this).removeListener(shizukuListener);
    }

    private void loadUi() {
        updatingUi = true;
        float sep = Prefs.sepMm(prefs);
        int size = Prefs.sizePct(prefs);
        float off = Prefs.offsetMm(prefs);
        int iv = Prefs.intervalMs(prefs);
        seekSep.setProgress(Math.round((sep - Prefs.MIN_SEP_MM) * 2));
        seekSize.setProgress(size - Prefs.MIN_SIZE_PCT);
        seekOffset.setProgress(Math.round((off + Prefs.MAX_OFFSET_MM) * 2));
        seekInterval.setProgress((iv - Prefs.MIN_INTERVAL_MS) / 100);
        modeGroup.check(Prefs.mode(prefs) == Prefs.MODE_LIVE ? R.id.modeLive : R.id.modeInterval);
        orientGroup.check(Prefs.splitOrient(prefs) == Prefs.ORIENT_FOLLOW ? R.id.orientFollow : R.id.orientForce);
        int ld = Prefs.landDir(prefs);
        dirGroup.check(ld == Prefs.DIR_LEFT ? R.id.dirLeft : ld == Prefs.DIR_RIGHT ? R.id.dirRight : R.id.dirAuto);
        for (int i = 0; i < dirGroup.getChildCount(); i++) {
            dirGroup.getChildAt(i).setEnabled(Prefs.splitOrient(prefs) == Prefs.ORIENT_FORCE_LANDSCAPE);
        }
        fitGroup.check(Prefs.fitMode(prefs) == Prefs.FIT_CONTAIN ? R.id.fitContain : R.id.fitFill);
        chkDots.setChecked(Prefs.showDots(prefs));
        int cp = Prefs.controlPane(prefs);
        boolean gOn = Prefs.gestures(prefs), cOn = cp != Prefs.CONTROL_OFF;
        swGestures.setChecked(gOn);
        swControl.setChecked(cOn);
        swRealtime.setChecked(Prefs.realtime(prefs));
        controlGroup.check(Prefs.controlSide(prefs) == Prefs.CONTROL_LEFT ? R.id.controlLeft : R.id.controlRight);
        for (int i = 0; i < controlGroup.getChildCount(); i++) controlGroup.getChildAt(i).setEnabled(cOn);
        String side = Prefs.controlSide(prefs) == Prefs.CONTROL_LEFT ? "左" : "右";
        String other = Prefs.controlSide(prefs) == Prefs.CONTROL_LEFT ? "右" : "左";
        comboHint.setText("当前模式：" + (!gOn && !cOn ? "分屏画面不响应触摸，只能用悬浮按钮开关分屏"
                : gOn && !cOn ? "整个分屏画面都可用快捷手势"
                : gOn ? side + "半屏操控下面的应用，" + other + "半屏用快捷手势"
                : "只有" + side + "半屏可操控下面的应用，" + other + "半屏不响应触摸；用悬浮按钮关闭分屏"));
        float pxmm = Prefs.pxPerMm(getResources().getDisplayMetrics());
        lblSep.setText(String.format(java.util.Locale.ROOT, "两点间距（两个画面中心距离）：%.1f 毫米（约 %d 像素）", sep, Math.round(sep * pxmm)));
        lblSize.setText("画面缩放（在“画面适配”基础上额外放大/缩小）：" + size + "%");
        lblOffset.setText(String.format(java.util.Locale.ROOT, "垂直偏移：%+.1f 毫米（正数向下）", off));
        lblInterval.setText(String.format(java.util.Locale.ROOT, "间歇刷新间隔：%.1f 秒", iv / 1000f));
        seekInterval.setEnabled(Prefs.mode(prefs) == Prefs.MODE_INTERVAL);
        updatingUi = false;
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        status.setText(ProjectionService.running
                ? (ProjectionService.splitOnPublic ? "状态：运行中，分屏已开启" : "状态：运行中（点悬浮按钮开启分屏）")
                : "状态：未运行");
        setSt(stCapture, ProjectionService.running ? 2 : 1, ProjectionService.running ? "录屏：已授权（运行中）" : "录屏：每次点“开始”时确认");
        btnStart2.setVisibility(ProjectionService.running ? View.GONE : View.VISIBLE);
        setSt(stOverlay, overlay ? 2 : 0, overlay ? "悬浮窗：已授予" : "悬浮窗：未授予（必需）");
        btnOverlay.setVisibility(overlay ? View.GONE : View.VISIBLE);
        ((View) stNotif.getParent()).setVisibility(Build.VERSION.SDK_INT >= 33 ? View.VISIBLE : View.GONE);
        setSt(stNotif, notif ? 2 : 1, notif ? "通知：已允许" : "通知：未允许（可选）");
        ShizukuHelper sh = ShizukuHelper.get(this);
        int ss = sh.state();
        boolean rt = Prefs.realtime(prefs);
        String sText;
        int sLevel;
        switch (ss) {
            case ShizukuHelper.ST_READY: sText = rt ? "Shizuku：已就绪（实时操控）" : "Shizuku：已授权"; sLevel = 2; break;
            case ShizukuHelper.ST_BINDING: sText = rt ? "Shizuku：已授权，正在连接…" : "Shizuku：已授权"; sLevel = rt ? 1 : 2; break;
            case ShizukuHelper.ST_NO_PERMISSION: sText = "Shizuku：运行中，未授权"; sLevel = rt ? 0 : 1; break;
            case ShizukuHelper.ST_OLD: sText = "Shizuku：版本过旧，请更新"; sLevel = 0; break;
            default: sText = "Shizuku：未运行（仅实时操控需要）"; sLevel = rt ? 0 : 1; break;
        }
        setSt(stShizuku, sLevel, sText);
        btnShizuku.setText(ss == ShizukuHelper.ST_NOT_RUNNING || ss == ShizukuHelper.ST_OLD ? "打开" : "授权");
        btnShizuku.setVisibility(ss == ShizukuHelper.ST_READY || ss == ShizukuHelper.ST_BINDING ? View.GONE : View.VISIBLE);
        if (rt && ss == ShizukuHelper.ST_BINDING) sh.ensureBound();
        realtimeHelp.setText(rt
                ? (ss == ShizukuHelper.ST_READY ? "实时操控已生效：手指在可操控半屏上移动时，下面的应用同步响应。"
                    : "Shizuku 未就绪，暂时使用无障碍回放（松手约 0.3 秒后执行）。")
                : "关闭时使用无障碍回放（松手后执行，有延迟）。开启后通过 Shizuku 实时注入触摸，手指移动即时生效。");
        boolean a11yOn = GestureService.isRunning();
        boolean a11ySet = GestureService.isEnabledInSettings(this);
        setSt(a11yStatus, a11yOn ? 2 : a11ySet ? 1 : (Prefs.controlPane(prefs) != Prefs.CONTROL_OFF ? 0 : 1),
                a11yOn ? "无障碍：已开启" : a11ySet ? "无障碍：已打开但未运行（请关闭后重开）" : "无障碍：未开启（可操控半屏回放需要）");
        btnA11yBtn.setVisibility(a11yOn ? View.GONE : View.VISIBLE);
        btnNotif.setVisibility(Build.VERSION.SDK_INT >= 33 && !notif ? View.VISIBLE : View.GONE);
    }

    /** level: 0 未满足（红）1 可选/提示（橙）2 正常（绿）。 */
    private void setSt(TextView t, int level, String text) {
        t.setText((level == 2 ? "● " : level == 1 ? "○ " : "✘ ") + text);
        t.setTextColor(getColor(level == 2 ? R.color.ok : level == 1 ? R.color.warn : R.color.bad));
    }

    private void openShizukuApp() {
        Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        try {
            if (i != null) startActivity(i);
            else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases")));
        } catch (Exception e) {
            Toast.makeText(this, "请到 https://shizuku.rikka.app 下载 Shizuku", Toast.LENGTH_LONG).show();
        }
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void requestNotif(boolean thenStart) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingStartAfterNotif = thenStart;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else if (thenStart) {
            requestCapture();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            refreshStatus();
            if (pendingStartAfterNotif) {
                pendingStartAfterNotif = false;
                requestCapture(); // 通知权限不是必需的，无论结果都继续
            }
        }
    }

    private void startFlow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许“显示在其他应用上层”，然后返回再点开始", Toast.LENGTH_LONG).show();
            openOverlaySettings();
            return;
        }
        requestNotif(true);
    }

    private void requestCapture() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent i;
        if (Build.VERSION.SDK_INT >= 34) {
            // 让用户可在“单个应用 / 整个屏幕”之间选择（设备支持时）
            i = mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice());
        } else {
            i = mpm.createScreenCaptureIntent();
        }
        try {
            startActivityForResult(i, REQ_CAPTURE);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开录屏授权：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "已取消录屏授权", Toast.LENGTH_SHORT).show();
            return;
        }
        // 先拿到授权，再启动 mediaProjection 类型的前台服务（Android 14 要求）
        Intent svc = new Intent(this, ProjectionService.class)
                .setAction(ProjectionService.ACTION_START)
                .putExtra(ProjectionService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(ProjectionService.EXTRA_DATA, data);
        startForegroundService(svc);
        Toast.makeText(this, "已开始：点悬浮按钮“分屏”开启平行眼画面", Toast.LENGTH_LONG).show();
        moveTaskToBack(true);
    }

    static final String GESTURE_HELP =
            "分屏画面上的手势（分屏开启时直接在画面上操作；方向均以你横拿手机时看到的画面为准）：\n"
            + "· 单指左右滑动：调节两点间距（向右变大，向左变小）\n"
            + "· 单指上下滑动：整体上下移动画面和红点（垂直偏移）\n"
            + "· 双指捏合 / 张开：缩小 / 放大画面（在“画面适配”基础上 30%～200%，超出部分在各自半屏内裁掉）\n"
            + "· 单指双击：关闭分屏（关闭快捷手势后只能用悬浮按钮关闭）\n"
            + "· 悬浮按钮：单击开启 / 关闭分屏；长按关闭分屏并打开本设置页；按住拖动可移动位置\n"
            + "· 开启“可操控半屏”后：可操控那一半上的单指点按、长按、滑动会传给下面的应用，不会调节参数；"
            + "以上调节手势（含双击关闭）只在另一半上有效";

    static final String CONTROL_HELP =
            "无障碍回放说明：在选定的半屏画面上用单指点按、长按或滑动，手指抬起约 0.3 秒后，会在真实屏幕的对应位置代为执行一遍"
            + "（有延迟，拖动越久延迟越长）。执行期间分屏画面会变淡一下，请不要触摸屏幕，否则会中断。不支持双指操作。\n"
            + "开启方法：点“开启无障碍服务”，在列表中找到“平行眼分屏·可操控半屏”并打开。"
            + "如果开关是灰色、提示“受限制的设置”（Android 13 起安装包安装的应用常见），"
            + "请点“应用信息”，点右上角 ⋮ 菜单，选“允许受限制的设置”，再回到无障碍设置开启。"
            + "部分国产系统还需在“权限管理 / 自启动 / 省电策略”中允许本应用，否则无障碍服务可能被自动关闭。\n\n"
            + "Shizuku 实时操控：安装并启动 Shizuku（无线调试或 root 方式），打开上面的“实时操控”开关并在弹窗中点“允许”。"
            + "就绪后手指按下即实时传给下面的应用（无需无障碍服务）；Shizuku 停止（如重启手机后）会自动退回无障碍回放。"
            + "按住期间分屏画面会稍微变淡，这是系统允许触摸穿透的要求。";

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        abstract void changed(int v);
        @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { if (fromUser) changed(p); }
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
