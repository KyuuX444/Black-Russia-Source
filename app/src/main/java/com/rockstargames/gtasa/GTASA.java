package com.rockstargames.gtasa;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import com.wardrumstudios.utils.WarMedia;
public class GTASA extends WarMedia {
    public void onCreate(Bundle bundle) {
        System.out.println("GTASA onCreate");
        super.onCreate(bundle);
    }

    public void onDestroy() {
        System.out.println("GTASA onDestroy");
        super.onDestroy();
    }

    public void onPause() {
        System.out.println("GTASA onPause");
        super.onPause();
    }

    public void onRestart() {
        System.out.println("GTASA onRestart");
        super.onRestart();
    }

    public void onResume() {
        System.out.println("GTASA onResume");
        super.onResume();
    }

    public void onStart() {
        System.out.println("GTASA onStart");
        super.onStart();
    }

    public void onStop() {
        System.out.println("GTASA onStop");
        super.onStop();
    }

    public boolean ServiceAppCommand(String cmd, String args) {
        if ("SetKeepScreenOn".equalsIgnoreCase(cmd)) {
            ScreenSetWakeLock(true);
            return true;
        }
        return false;
    }

    public int ServiceAppCommandValue(String cmd, String args) {
        if ("GetDownloadState".equalsIgnoreCase(cmd)) return 4;
        if ("GetDownloadBytes".equalsIgnoreCase(cmd)) return 0;
        if ("GetNetworkState".equalsIgnoreCase(cmd)) return isNetworkAvailable() ? 1 : 0;
        return 0;
    }

    public boolean ServiceAppCommandInt(String cmd, int arg) {
        if ("SetKeepScreenOn".equalsIgnoreCase(cmd)) {
            ScreenSetWakeLock(arg != 0);
            return true;
        }
        return false;
    }

    public void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
    }

    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
    }

    public boolean onKeyDown(int i, KeyEvent keyEvent) {
        return super.onKeyDown(i, keyEvent);
    }
}