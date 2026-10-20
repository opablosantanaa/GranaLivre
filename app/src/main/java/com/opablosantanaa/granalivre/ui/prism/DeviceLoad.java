package com.opablosantanaa.granalivre.ui.prism;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;

final class DeviceLoad {
    static final int NORMAL = 0;
    static final int REDUCED = 1;
    static final int STOPPED = 2;

    interface Listener {
        void onLevel(int level);
    }

    private final Context context;
    private final Listener listener;
    private final PowerManager power;
    private int thermal;
    private BroadcastReceiver saverReceiver;
    private PowerManager.OnThermalStatusChangedListener thermalListener;

    DeviceLoad(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.power = (PowerManager) this.context.getSystemService(Context.POWER_SERVICE);
    }

    static int levelFor(boolean powerSave, int thermalStatus) {
        if (thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) return STOPPED;
        if (powerSave || thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) return REDUCED;
        return NORMAL;
    }

    int current() {
        boolean saver = power != null && power.isPowerSaveMode();
        return levelFor(saver, thermal);
    }

    int start() {
        if (power == null) return NORMAL;
        saverReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                listener.onLevel(current());
            }
        };
        context.registerReceiver(saverReceiver, new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermal = power.getCurrentThermalStatus();
            thermalListener = status -> {
                thermal = status;
                listener.onLevel(current());
            };
            power.addThermalStatusListener(context.getMainExecutor(), thermalListener);
        }
        return current();
    }

    void stop() {
        if (saverReceiver != null) {
            context.unregisterReceiver(saverReceiver);
            saverReceiver = null;
        }
        if (thermalListener != null && power != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            power.removeThermalStatusListener(thermalListener);
            thermalListener = null;
        }
    }
}
