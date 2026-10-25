package com.opablosantanaa.granalivre.worker;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.DueReminder;
import com.opablosantanaa.granalivre.repository.DueReminderFinder;
import com.opablosantanaa.granalivre.repository.RecurrenceGenerator;
import com.opablosantanaa.granalivre.ui.Session;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class DueReminderWorker extends Worker {
    private static final String UNIQUE_WORK_NAME = "due-reminder";
    private static final String KEY_SLOT = "slot";
    private static final String PREFS = "grana_livre_reminders";
    private static final String KEY_LAST_SLOT = "last_slot";

    public DueReminderWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String slot = getInputData().getString(KEY_SLOT);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean repeated = slot != null && slot.equals(prefs.getString(KEY_LAST_SLOT, null));
        if (!repeated) notifyDue(context);
        prefs.edit().putString(KEY_LAST_SLOT, slot).apply();
        ensureNext(context, slot == null ? null : LocalDateTime.parse(slot));
        return Result.success();
    }

    private static void notifyDue(Context context) {
        try {
            long userId = Session.userId(context);
            if (userId > 0) {
                LocalDate today = LocalDate.now();
                AppDatabase database = AppDatabase.getInstance(context);
                new RecurrenceGenerator(database).generateUntil(today);
                new DueNotifier(context).show(new DueReminderFinder(database).find(userId, today), today);
            }
        } catch (RuntimeException ignored) {
        }
    }

    public static void schedule(Context context) {
        enqueue(context, DueReminder.nextSlot(LocalDateTime.now()), ExistingWorkPolicy.KEEP);
    }

    private static void ensureNext(Context context, LocalDateTime planned) {
        if (hasPending(context)) return;
        LocalDateTime now = LocalDateTime.now();
        enqueue(context, DueReminder.nextAfter(planned, now), ExistingWorkPolicy.APPEND_OR_REPLACE);
    }

    private static boolean hasPending(Context context) {
        try {
            for (WorkInfo info : WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()) {
                if (info.getState() == WorkInfo.State.ENQUEUED || info.getState() == WorkInfo.State.BLOCKED) {
                    return true;
                }
            }
            return false;
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void enqueue(Context context, LocalDateTime next, ExistingWorkPolicy policy) {
        LocalDateTime now = LocalDateTime.now();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DueReminderWorker.class)
                .setInitialDelay(Math.max(0, Duration.between(now, next).toMillis()), TimeUnit.MILLISECONDS)
                .setInputData(new Data.Builder().putString(KEY_SLOT, next.toString()).build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request);
    }
}
