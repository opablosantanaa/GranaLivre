package com.opablosantanaa.granalivre.ui.prism;

final class FrameGovernor {
    static final int WINDOW = 60;
    static final int DOWNGRADE_MISSES = 12;
    static final int UPGRADE_CLEAN_WINDOWS = 5;

    private int slots;
    private int misses;
    private int cleanWindows;
    private boolean downgraded;

    void onRequested() {
        slots++;
    }

    void onMissed() {
        slots++;
        misses++;
    }

    int decide(int current, int lowest) {
        if (slots < WINDOW) return current;
        int result = current;
        if (misses >= DOWNGRADE_MISSES) {
            cleanWindows = 0;
            if (current < lowest) {
                result = current + 1;
                downgraded = true;
            }
        } else if (misses == 0) {
            cleanWindows++;
            if (downgraded && current > 0 && cleanWindows >= UPGRADE_CLEAN_WINDOWS) {
                result = current - 1;
                cleanWindows = 0;
            }
        } else {
            cleanWindows = 0;
        }
        slots = 0;
        misses = 0;
        return result;
    }
}
