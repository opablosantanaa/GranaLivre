package com.opablosantanaa.granalivre.worker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.opablosantanaa.granalivre.App;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.domain.DueReminder;
import com.opablosantanaa.granalivre.ui.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class DueNotifier {
    private static final String CHANNEL_ID = "due_reminders";
    private static final String GROUP = "com.opablosantanaa.granalivre.DUE";
    private static final int SUMMARY_ID = 1;

    private final Context context;
    private final NotificationManagerCompat manager;

    public DueNotifier(Context context) {
        this.context = context.getApplicationContext();
        manager = NotificationManagerCompat.from(this.context);
    }

    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    @SuppressWarnings("MissingPermission")
    public void show(List<DueReminder> reminders, LocalDate today) {
        manager.cancelAll();
        if (reminders.isEmpty() || !canNotify(context)) return;
        createChannel();

        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, App.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        for (DueReminder reminder : reminders) {
            manager.notify(notificationId(reminder), base(open)
                    .setContentTitle(title(reminder))
                    .setContentText(text(reminder, today))
                    .setWhen(System.currentTimeMillis())
                    .build());
        }
        if (reminders.size() > 1) {
            String summary = context.getResources().getQuantityString(R.plurals.reminder_summary,
                    reminders.size(), reminders.size());
            manager.notify(SUMMARY_ID, base(open)
                    .setContentTitle(summary)
                    .setGroupSummary(true)
                    .build());
        }
    }

    private NotificationCompat.Builder base(PendingIntent open) {
        int color = ContextCompat.getColor(context, R.color.gl_primary);
        NotificationCompat.Builder publicVersion = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(color)
                .setContentTitle(context.getString(R.string.reminder_public_title));
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(color)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion.build())
                .setGroup(GROUP)
                .setContentIntent(open)
                .setAutoCancel(true);
    }

    private String title(DueReminder reminder) {
        if (reminder.kind == DueReminder.Kind.INVOICE) {
            return context.getString(R.string.reminder_invoice_title, reminder.title).trim();
        }
        return reminder.title.isEmpty() ? context.getString(R.string.reminder_bill_title) : reminder.title;
    }

    private String text(DueReminder reminder, LocalDate today) {
        String amount = Money.plain(reminder.amountCents);
        int days = reminder.daysUntilDue(today);
        if (days >= 2) return context.getString(R.string.reminder_due_in_two_days, amount);
        if (days == 1) return context.getString(R.string.reminder_due_tomorrow, amount);
        if (days == 0) return context.getString(R.string.reminder_due_today, amount);
        return context.getString(R.string.reminder_overdue_yesterday, amount);
    }

    private static int notificationId(DueReminder reminder) {
        int id = Objects.hash(reminder.kind, reminder.id, reminder.dueDate);
        return id == SUMMARY_ID ? id + 1 : id;
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.reminder_channel_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
