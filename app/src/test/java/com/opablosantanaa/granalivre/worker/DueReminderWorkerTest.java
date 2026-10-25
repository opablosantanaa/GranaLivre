package com.opablosantanaa.granalivre.worker;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestListenableWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDateTime;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class DueReminderWorkerTest {
    private static final String SLOT = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).toString();

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, new Configuration.Builder()
                .setExecutor(new SynchronousExecutor())
                .build());
        context.getSharedPreferences("grana_livre_reminders", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private ListenableWorker.Result run(String slot) {
        DueReminderWorker worker = TestListenableWorkerBuilder.from(context, DueReminderWorker.class)
                .setInputData(new Data.Builder().putString("slot", slot).build())
                .build();
        return worker.doWork();
    }

    private int pending() throws Exception {
        int count = 0;
        List<WorkInfo> infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("due-reminder").get();
        for (WorkInfo info : infos) {
            if (info.getState() == WorkInfo.State.ENQUEUED || info.getState() == WorkInfo.State.BLOCKED) count++;
        }
        return count;
    }

    @Test
    public void normalRunSchedulesTheNextOne() throws Exception {
        assertEquals(ListenableWorker.Result.success(), run(SLOT));
        assertEquals(1, pending());
    }

    @Test
    public void repeatedRunWithNothingQueuedStillSchedulesTheNextOne() throws Exception {
        context.getSharedPreferences("grana_livre_reminders", Context.MODE_PRIVATE)
                .edit().putString("last_slot", SLOT).commit();
        run(SLOT);
        assertEquals("antes a corrente parava aqui", 1, pending());
    }

    @Test
    public void repeatedRunDoesNotDuplicateAQueuedNext() throws Exception {
        run(SLOT);
        run(SLOT);
        assertEquals(1, pending());
    }

    @Test
    public void appStartKeepsTheExistingChain() throws Exception {
        run(SLOT);
        DueReminderWorker.schedule(context);
        assertEquals(1, pending());
    }
}
