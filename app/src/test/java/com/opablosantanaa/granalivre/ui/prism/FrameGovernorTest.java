package com.opablosantanaa.granalivre.ui.prism;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FrameGovernorTest {
    private static int window(FrameGovernor governor, int misses, int current) {
        for (int i = 0; i < FrameGovernor.WINDOW - misses; i++) governor.onRequested();
        for (int i = 0; i < misses; i++) governor.onMissed();
        return governor.decide(current, 2);
    }

    @Test
    public void keepsQualityWhileWindowIsIncomplete() {
        FrameGovernor governor = new FrameGovernor();
        for (int i = 0; i < 30; i++) governor.onMissed();
        assertEquals(0, governor.decide(0, 2));
    }

    @Test
    public void lowersResolutionWhenManyFramesAreMissed() {
        FrameGovernor governor = new FrameGovernor();
        assertEquals(1, window(governor, FrameGovernor.DOWNGRADE_MISSES, 0));
        assertEquals(2, window(governor, FrameGovernor.DOWNGRADE_MISSES, 1));
        assertEquals(2, window(governor, FrameGovernor.DOWNGRADE_MISSES, 2));
    }

    @Test
    public void ignoresOccasionalMisses() {
        FrameGovernor governor = new FrameGovernor();
        assertEquals(0, window(governor, FrameGovernor.DOWNGRADE_MISSES - 1, 0));
    }

    @Test
    public void raisesResolutionOnlyAfterSustainedSmoothFrames() {
        FrameGovernor governor = new FrameGovernor();
        int step = window(governor, FrameGovernor.DOWNGRADE_MISSES, 0);
        for (int i = 1; i < FrameGovernor.UPGRADE_CLEAN_WINDOWS; i++) step = window(governor, 0, step);
        assertEquals(1, step);
        assertEquals(0, window(governor, 0, step));
    }

    @Test
    public void mapsBatteryAndHeatToLevels() {
        assertEquals(DeviceLoad.NORMAL, DeviceLoad.levelFor(false, 0));
        assertEquals(DeviceLoad.REDUCED, DeviceLoad.levelFor(true, 0));
        assertEquals(DeviceLoad.REDUCED, DeviceLoad.levelFor(false, 2));
        assertEquals(DeviceLoad.STOPPED, DeviceLoad.levelFor(false, 3));
    }
}
