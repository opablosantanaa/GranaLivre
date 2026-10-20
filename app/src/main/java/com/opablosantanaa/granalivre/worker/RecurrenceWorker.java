package com.opablosantanaa.granalivre.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.repository.RecurrenceGenerator;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

public class RecurrenceWorker extends Worker {
    private static final String UNIQUE_WORK_NAME = "recurrence-generator";
    private static final long REPEAT_INTERVAL_DAYS = 1;

    public RecurrenceWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            new RecurrenceGenerator(AppDatabase.getInstance(getApplicationContext())).generateUntil(LocalDate.now());
            return Result.success();
        } catch (RuntimeException e) {
            return Result.retry();
        }
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                RecurrenceWorker.class, REPEAT_INTERVAL_DAYS, TimeUnit.DAYS).build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
