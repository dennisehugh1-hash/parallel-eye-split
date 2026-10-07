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
    private TextView status, stCapture, stOverlay, stNotif, stShizuku, comboHint, methodHelp, methodStatus, statusDetail, qualityHint, fitHint, orientHint, modeHint, lblSep, lblSize, lblOffset, lblInterval;
    private SeekBar seekSep, seekSize, seekOffset, seekInterval;
    private RadioGroup modeGroup, controlGroup, fitGroup, orientGroup, dirGroup;
    private android.widget.CompoundButton chkDots, swFps;
    private android.widget.Switch swGestures;
    private RadioGroup methodGroup, resGroup, fpsGroup;
    private TextView gpuHint, fullHint;
    private android.widget.CompoundButton swFull;
    private android.widget.CompoundButton swGpu;
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
        methodHelp = findViewById(R.id.methodHelp);
        methodStatus = findViewById(R.id.methodStatus);
        statusDetail = findViewById(R.id.statusDetail);
        qualityHint = findViewById(R.id.qualityHint);
        fitHint = findViewById(R.id.fitHint);
        orientHint = findViewById(R.id.orientHint);
        modeHint = findViewById(R.id.modeHint);
        methodGroup = findViewById(R.id.methodGroup);
        resGroup = findViewById(R.id.resGroup);
        fpsGroup = findViewById(R.id.fpsGroup);
        swGpu = findViewById(R.id.swGpu);
        gpuHint = findViewById(R.id.gpuHint);
        swFull = findViewById(R.id.swFull);
        fullHint = findViewById(R.id.fullHint);
        swFull.setOnCheckedChangeListener((b, c) -> {
            if (updatingUi) return;
            prefs.edit().putBoolean(Prefs.K_FULL, c).apply();
            if (ProjectionService.running) Toast.makeText(this, "下次点“开始”时生效", Toast.LENGTH_SHORT).show();
        });
        ((TextView) findViewById(R.id.protectTip)).setText(Build.VERSION.SDK_INT >= 34
                ? "提示：Android 14+ 录屏共享可能被系统保护，隐藏键盘、通知等内容。Android 15 可在开发者选项中尝试打开“停用屏幕共享保护”（Disable screen share protections）。"
                : "提示：部分应用禁止录屏，分屏中会显示黑色。");
        resGroup.setOnCheckedChangeListener((g, id) -> {
            if (updatingUi) return;
            prefs.edit().putInt(Prefs.K_RES, id == R.id.res50 ? 50 : id == R.id.res75 ? 75 : 100).apply();
        });
        fpsGroup.setOnCheckedChangeListener((g, id) -> {
            if (updatingUi) return;
            prefs.edit().putInt(Prefs.K_FPS, id == R.id.fps30 ? 30 : id == R.id.fps60 ? 60 : 0).apply();
        });
        swGpu.setOnCheckedChangeListener((b, c) -> {
            if (updatingUi) return;
            prefs.edit().putBoolean(Prefs.K_GPU, c).apply();
        });
        swFps = findViewById(R.id.swFps);
        swGestures = findViewById(R.id.swGestures);
        btnShizuku = findViewById(R.id.btnShizuku);
        btnA11yBtn = findViewById(R.id.btnA11y);
        btnOverlay = findViewById(R.id.btnOverlayPerm);
        btnStart2 = findViewById(R.id.btnStart2);
        ((TextView) findViewById(R.id.version)).setText("v" + BuildConfig.VERSION_NAME + " · 平行眼（裸眼 3D）观看工具");
        swGestures.setOnCheckedChangeListener((b, c) -> {
            if (updatingUi) return;
            prefs.edit().putBoolean(Prefs.K_GESTURES, c).apply();
        });
        swFps.setOnCheckedChangeListener((b, c) -> {
            if (updatingUi) return;
            prefs.edit().putBoolean(Prefs.K_SHOW_FPS, c).apply();
        });
        methodGroup.setOnCheckedChangeListener((g, id) -> {
            if (updatingUi) return;
            ShizukuHelper h = ShizukuHelper.get(this);
            if (id == R.id.methodOff) {
                prefs.edit().putInt(Prefs.K_CONTROL_PANE, Prefs.CONTROL_OFF).putBoolean(Prefs.K_REALTIME, false).apply();
                h.unbind();
            } else if (id == R.id.methodA11y) {
                prefs.edit().putInt(Prefs.K_CONTROL_PANE, Prefs.controlSide(prefs)).putBoolean(Prefs.K_REALTIME, false).apply();
                h.unbind();
                if (!GestureService.isRunning()) Toast.makeText(this, "还需开启无障碍服务“平行眼分屏·可操控半屏”", Toast.LENGTH_LONG).show();
            } else {
                prefs.edit().putInt(Prefs.K_CONTROL_PANE, Prefs.controlSide(prefs)).putBoolean(Prefs.K_REALTIME, true).apply();
                if (!h.granted()) {
                    if (!h.requestPermission()) Toast.makeText(this, "Shizuku 未运行：请先安装并启动 Shizuku。未就绪期间会临时改用无障碍回放", Toast.LENGTH_LONG).show();
                } else h.ensureBound();
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
                "1. 授予悬浮窗权限，点“开始”，录屏弹窗推荐选“单个应用”。\n"
                + "2. 点悬浮按钮“分屏”开关；长按打开设置；可拖动。\n"
                + "3. 横拿手机，放松双眼看远处，让两个红点重合成中间一个。\n"
                + "4. 部分应用禁止录屏，画面会是黑色。\n"
                + String.format(java.util.Locale.ROOT, "本机屏幕约 %.0f×%.0f dpi（毫米换算依据）。", dm.xdpi, dm.ydpi));
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
        boolean rtOn = Prefs.realtime(prefs);
        methodGroup.check(!cOn ? R.id.methodOff : rtOn ? R.id.methodShizuku : R.id.methodA11y);
        int rp = Prefs.resPct(prefs), fc = Prefs.fpsCap(prefs);
        resGroup.check(rp == 50 ? R.id.res50 : rp == 75 ? R.id.res75 : R.id.res100);
        fpsGroup.check(fc == 30 ? R.id.fps30 : fc == 60 ? R.id.fps60 : R.id.fpsFollow);
        qualityHint.setText(fc == 0 ? "跟随屏幕：按屏幕最高刷新率（90/120Hz）显示，更耗电。" : "分辨率越高越清晰，帧率越高越流畅，也越耗电。");
        swGpu.setChecked(Prefs.gpu(prefs));
        swGpu.setEnabled(Build.VERSION.SDK_INT >= 29);
        gpuHint.setText(Build.VERSION.SDK_INT < 29 ? "需要 Android 10 及以上。" : "推荐开启：GPU 直接绘制，更流畅省电；画面异常时再关闭。");
        swFps.setChecked(Prefs.showFps(prefs));
        boolean full = Prefs.fullCapture(prefs);
        swFull.setChecked(full);
        int sk = ProjectionService.skipStatus;
        fullHint.setText(!full ? "关：每次开始时由系统询问，推荐选“单个应用”（不会套娃）。"
                : !SkipCapture.supported() ? "开：始终录整个屏幕；本机低于 Android 12，无法排除分屏层，会自动用间歇刷新。"
                : sk == SkipCapture.R_FAIL ? "开：分屏层排除失败，已自动用间歇刷新。"
                : "开：始终录整个屏幕并尝试把分屏层排除出画面；若出现套娃请改用间歇刷新。");
        fitHint.setText(Prefs.fitMode(prefs) == Prefs.FIT_CONTAIN ? "等比显示，可能留黑边。" : "裁切边缘，铺满半屏。");
        orientHint.setText(Prefs.splitOrient(prefs) == Prefs.ORIENT_FOLLOW ? "与系统方向一致。" : "应用保持竖屏，横拿手机观看。");
        modeHint.setText(Prefs.mode(prefs) == Prefs.MODE_LIVE ? "持续刷新，最流畅。" : "定时刷新，会轻微闪烁；整屏捕获套娃时使用。");
        controlGroup.check(Prefs.controlSide(prefs) == Prefs.CONTROL_LEFT ? R.id.controlLeft : R.id.controlRight);
        for (int i = 0; i < controlGroup.getChildCount(); i++) controlGroup.getChildAt(i).setEnabled(cOn);
        String side = Prefs.controlSide(prefs) == Prefs.CONTROL_LEFT ? "左" : "右";
        String other = Prefs.controlSide(prefs) == Prefs.CONTROL_LEFT ? "右" : "左";
        comboHint.setText("当前：" + (!gOn && !cOn ? "画面不响应触摸，用悬浮按钮开关"
                : gOn && !cOn ? "全屏快捷手势"
                : gOn ? side + "半屏操控应用，" + other + "半屏快捷手势"
                : "仅" + side + "半屏操控应用，" + other + "半屏不响应"));
        float pxmm = Prefs.pxPerMm(getResources().getDisplayMetrics());
        lblSep.setText(String.format(java.util.Locale.ROOT, "两点间距：%.1f 毫米（约 %d 像素）", sep, Math.round(sep * pxmm)));
        lblSize.setText("画面缩放：" + size + "%");
        lblOffset.setText(String.format(java.util.Locale.ROOT, "垂直偏移：%+.1f 毫米", off));
        lblInterval.setText(String.format(java.util.Locale.ROOT, "刷新间隔：%.1f 秒", iv / 1000f));
        seekInterval.setEnabled(Prefs.mode(prefs) == Prefs.MODE_INTERVAL);
        updatingUi = false;
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        status.setText(ProjectionService.running ? (ProjectionService.splitOnPublic ? "● 分屏中" : "● 运行中") : "○ 未运行");
        statusDetail.setText(!overlay ? "先授予悬浮窗权限，再点“开始”。"
                : ProjectionService.running ? (ProjectionService.splitOnPublic ? "分屏已开启，点悬浮按钮关闭。" : "录屏中，点悬浮按钮“分屏”开启。")
                : "点“开始”并允许录屏。");
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
        boolean a11yOn = GestureService.isRunning();
        boolean a11ySet = GestureService.isEnabledInSettings(this);
        setSt(a11yStatus, a11yOn ? 2 : a11ySet ? 1 : (Prefs.controlPane(prefs) != Prefs.CONTROL_OFF ? 0 : 1),
                a11yOn ? "无障碍：已开启" : a11ySet ? "无障碍：已打开但未运行（请关闭后重开）" : "无障碍：未开启（可操控半屏回放需要）");
        btnA11yBtn.setVisibility(a11yOn ? View.GONE : View.VISIBLE);
        boolean cOn = Prefs.controlPane(prefs) != Prefs.CONTROL_OFF;
        if (!cOn) {
            setSt(methodStatus, 1, "可操控半屏已关闭");
            methodHelp.setText("触摸不会传给下面的应用。");
        } else if (!rt) {
            setSt(methodStatus, a11yOn ? 2 : 0, a11yOn ? "无障碍已开启，可用" : "无障碍未开启，点权限栏“去开启”");
            methodHelp.setText("松手约 0.3 秒后代为执行，单指操作。");
        } else if (ss == ShizukuHelper.ST_READY) {
            setSt(methodStatus, 2, "Shizuku 已就绪，实时操控");
            methodHelp.setText("触摸实时传给应用，按住时画面略变淡，单指操作。");
        } else {
            String why = ss == ShizukuHelper.ST_NOT_RUNNING ? "Shizuku 未运行" : ss == ShizukuHelper.ST_NO_PERMISSION ? "Shizuku 未授权"
                    : ss == ShizukuHelper.ST_OLD ? "Shizuku 版本过旧" : "正在连接 Shizuku";
            setSt(methodStatus, 0, "⚠ " + why + "，暂用无障碍回放" + (a11yOn ? "" : "（无障碍也未开启）"));
            methodHelp.setText("启动 Shizuku 并在权限栏点“授权”后自动切换。");
        }
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
            // 整个屏幕：强制捕获默认显示器（输入法、通知等系统窗口都会被录进来）；单个应用：让用户选择应用
            i = mpm.createScreenCaptureIntent(Prefs.scope(prefs) == Prefs.SCOPE_DISPLAY
                    ? MediaProjectionConfig.createConfigForDefaultDisplay()
                    : MediaProjectionConfig.createConfigForUserChoice());
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
            "左右滑调间距 · 上下滑调偏移 · 双指缩放 · 双击关闭";

    static final String CONTROL_HELP =
            "无障碍开关灰色时：应用信息 → 右上角 ⋮ → 允许受限制的设置。Shizuku 需先在其应用内启动（无线调试或 root）。";

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        abstract void changed(int v);
        @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { if (fromUser) changed(p); }
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
