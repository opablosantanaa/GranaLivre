package com.opablosantanaa.granalivre.ui;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.repository.BackupMerger;
import com.opablosantanaa.granalivre.repository.BackupRepository;
import com.opablosantanaa.granalivre.repository.PasswordRequiredException;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;

import java.time.format.DateTimeFormatter;

public final class BackupImport {
    public interface OnConfirm {
        void run(@Nullable String password);
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private BackupImport() {}

    public static void confirm(Fragment fragment, BackupRepository backups, Uri document, int title, int message,
                               int confirm, OnConfirm onConfirm) {
        preview(fragment, backups, document, null, title, message, confirm, onConfirm);
    }

    private static void preview(Fragment fragment, BackupRepository backups, Uri document, @Nullable String password,
                                int title, int message, int confirm, OnConfirm onConfirm) {
        backups.preview(document, password, new RepositoryCallback<BackupMerger.Preview>() {
            @Override
            public void onSuccess(BackupMerger.Preview preview) {
                if (!fragment.isAdded()) return;
                Context context = fragment.requireContext();
                new MaterialAlertDialogBuilder(context)
                        .setTitle(title)
                        .setMessage(describe(context, preview, message))
                        .setNegativeButton(R.string.back, null)
                        .setPositiveButton(confirm, (d, w) -> onConfirm.run(password))
                        .show();
            }

            @Override
            public void onError(Exception e) {
                if (!fragment.isAdded()) return;
                if (e instanceof PasswordRequiredException) {
                    PasswordDialog.ask(fragment.requireContext(), password == null ? null : e.getMessage(),
                            typed -> preview(fragment, backups, document, typed, title, message, confirm, onConfirm));
                } else {
                    Toast.makeText(fragment.requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    static String describe(Context context, BackupMerger.Preview preview, int message) {
        String when = preview.createdAt == null ? context.getString(R.string.backup_unknown_date)
                : context.getString(R.string.backup_date_time, Dates.shortDate(preview.createdAt.toLocalDate()),
                        preview.createdAt.format(TIME));
        return context.getString(message, when, preview.records, preview.transactions);
    }
}
