package com.opablosantanaa.granalivre.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.domain.BackupCipher;

public final class PasswordDialog {
    public interface OnPassword {
        void accept(String password);
    }

    private PasswordDialog() {}

    private static TextInputLayout field(Context context, int hint) {
        TextInputLayout layout = new TextInputLayout(context);
        layout.setHint(context.getString(hint));
        layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        Typeface typeface = input.getTypeface();
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTypeface(typeface);
        layout.addView(input);
        return layout;
    }

    private static String text(TextInputLayout layout) {
        return layout.getEditText() == null || layout.getEditText().getText() == null ? ""
                : layout.getEditText().getText().toString();
    }

    private static LinearLayout container(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int unit = Math.round(context.getResources().getDisplayMetrics().density);
        box.setPadding(24 * unit, 8 * unit, 24 * unit, 0);
        return box;
    }

    public static void ask(Context context, @Nullable String error, OnPassword onPassword) {
        LinearLayout box = container(context);
        TextInputLayout password = field(context, R.string.backup_password_hint);
        password.setError(error);
        box.addView(password);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.backup_password)
                .setMessage(R.string.backup_password_ask)
                .setView(box)
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(R.string.backup_password_open, (d, w) -> onPassword.accept(text(password)))
                .show();
    }

    public static void create(Context context, OnPassword onPassword) {
        LinearLayout box = container(context);
        TextInputLayout password = field(context, R.string.backup_password_hint);
        TextInputLayout repeat = field(context, R.string.backup_password_confirm_hint);
        box.addView(password);
        box.addView(repeat);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.backup_password)
                .setMessage(R.string.backup_password_create_message)
                .setView(box)
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(R.string.backup_password_save, null)
                .show();
        Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setOnClickListener(v -> {
            String typed = text(password);
            password.setError(null);
            repeat.setError(null);
            if (typed.length() < BackupCipher.MIN_PASSWORD_LENGTH) {
                password.setError(context.getString(R.string.backup_password_too_short,
                        BackupCipher.MIN_PASSWORD_LENGTH));
            } else if (!typed.equals(text(repeat))) {
                repeat.setError(context.getString(R.string.backup_password_mismatch));
            } else {
                dialog.dismiss();
                onPassword.accept(typed);
            }
        });
    }
}
