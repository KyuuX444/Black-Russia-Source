package com.ryzn.game.core;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;

import com.ryzn.game.ui.GameUI;
import com.ryzn.launcher.other.RyznGameDownloader;

import java.io.File;

public class GTASA extends com.rockstargames.gtasa.GTASA {
    private static final String TAG = "RyznGTASA";

    public GameUI ui;

    private native void initializeSAMP(GameUI ui, String gameDir, boolean isOffline);

    private native boolean multiTouchEvent4Ex(int action, int count, int x0, int y0, int x1, int y1, int x2, int y2, int x3, int y3);

    private void loadLibraries() {
        String abi = Build.CPU_ABI;
        boolean is64 = "arm64-v8a".equals(abi);
        Log.i(TAG, "Loading GTA SA 2.00 libraries (ABI=" + abi + ")");

        if (!is64) {
            System.loadLibrary("ImmEmulatorJ");
        }
        System.loadLibrary("GTASA");
        System.loadLibrary("ryzn_samp");
    }

    private File resolveGameDirectory() {
        File primaryDir = RyznGameDownloader.getGameDir();
        boolean modMode = RyznGameDownloader.isModifiedDataEnabled(this);
        if (new File(primaryDir, "texdb").exists()
                || (modMode && (new File(primaryDir, "mods").exists() || new File(primaryDir, "data").exists() || new File(primaryDir, "SAMP").exists()))) {
            return primaryDir;
        }
        File extFiles = getExternalFilesDir(null);
        String[] candidates = new String[]{
                primaryDir.getAbsolutePath(),
                extFiles != null ? extFiles.getAbsolutePath() : "",
                "/storage/emulated/0/Android/data/com.ryzn.samp/files",
                "/storage/emulated/0/Android/data/com.rockstargames.gtasa/files"
        };
        for (String path : candidates) {
            if (path == null || path.isEmpty()) continue;
            File dir = new File(path);
            if (new File(dir, "texdb").exists()
                    || (modMode && (new File(dir, "mods").exists() || new File(dir, "data").exists() || new File(dir, "SAMP").exists()))) {
                return dir;
            }
        }
        return primaryDir;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "GTASA onCreate");
        loadLibraries();

        super.onCreate(savedInstanceState);
        Toast.makeText(this, "Ryzn SA-MP Memulai Game...", Toast.LENGTH_SHORT).show();

        File targetDir = resolveGameDirectory();
        File minfo = new File(targetDir, "MINFO.BIN");
        File cinfo = new File(targetDir, "CINFO.BIN");
        if (minfo.exists()) minfo.delete();
        if (cinfo.exists()) cinfo.delete();

        File sampDir = new File(targetDir, "SAMP");
        if (!sampDir.exists()) {
            sampDir.mkdirs();
        }
        RyznGameDownloader.ensureClientConfigAndFonts(this, "");
        RyznGameDownloader.syncSettingsJson(this);

        ui = new GameUI(this);
        ui.initializeUI();

        String gameDir = targetDir.getAbsolutePath();
        if (!gameDir.endsWith("/")) {
            gameDir = gameDir + "/";
        }
        Log.i(TAG, "Game directory: " + gameDir);

        try {
            getSharedPreferences("samp_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("offline_mode", false)
                    .apply();
            initializeSAMP(ui, gameDir, false);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "initializeSAMP failed: " + e.getMessage());
        }

        if (getSharedPreferences("samp_settings", Context.MODE_PRIVATE).getBoolean("fullscreen", false)) {
            if (Build.VERSION.SDK_INT >= 30) {
                getWindow().getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            } else if (Build.VERSION.SDK_INT >= 28) {
                getWindow().getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (ui != null) {
            ui.onBackPressed();
        }
        super.onBackPressed();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (ui != null) {
            ui.onTouchEvent(event);
        }

        int x1 = 0, y1 = 0, x2 = 0, y2 = 0, x3 = 0, y3 = 0, x4 = 0, y4 = 0;
        int numEvents = event.getPointerCount();
        for (int i = 0; i < numEvents; i++) {
            int pointerId = event.getPointerId(i);
            if (pointerId == 0) {
                x1 = (int) event.getX(i);
                y1 = (int) event.getY(i);
            } else if (pointerId == 1) {
                x2 = (int) event.getX(i);
                y2 = (int) event.getY(i);
            } else if (pointerId == 2) {
                x3 = (int) event.getX(i);
                y3 = (int) event.getY(i);
            } else if (pointerId == 3) {
                x4 = (int) event.getX(i);
                y4 = (int) event.getY(i);
            }
        }

        int pointerId = event.getPointerId(event.getActionIndex());
        int action = event.getActionMasked();
        try {
            multiTouchEvent4Ex(action, pointerId, x1, y1, x2, y2, x3, y3, x4, y4);
        } catch (UnsatisfiedLinkError ignored) {
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ui != null) {
            ui.onResumeEvent();
        }
    }
}
