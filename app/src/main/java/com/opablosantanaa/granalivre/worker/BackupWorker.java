package com.opablosantanaa.granalivre.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.opablosantanaa.granalivre.repository.BackupRepository;
import com.opablosantanaa.granalivre.ui.Session;

import java.util.concurrent.TimeUnit;

public class BackupWorker extends Worker {
    private static final String UNIQUE_WORK_NAME = "automatic-backup";
    private static final long REPEAT_INTERVAL_DAYS = 1;
    private static final int MAX_ATTEMPTS = 3;

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        long userId = Session.userId(context);
        if (userId <= 0) return Result.success();
        try {
            new BackupRepository(context).writeAutomaticNow(userId);
            return Result.success();
        } catch (Exception e) {
            return getRunAttemptCount() < MAX_ATTEMPTS ? Result.retry() : Result.success();
        }
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BackupWorker.class, REPEAT_INTERVAL_DAYS, TimeUnit.DAYS).build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void runNow(Context context) {
        WorkManager.getInstance(context).enqueue(new OneTimeWorkRequest.Builder(BackupWorker.class).build());
    }
}
