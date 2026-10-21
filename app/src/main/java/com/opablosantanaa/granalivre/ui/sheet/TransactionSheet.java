package com.opablosantanaa.granalivre.ui.sheet;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.Rows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class TransactionSheet extends BaseSheet {
    private static final String ARG_ID = "id";

    private FinancialTransaction transaction;
    private LocalDate date;
    private long pendingCategory;

    public TransactionSheet() {
        super(R.layout.sheet_transaction);
    }

    public static TransactionSheet edit(FinancialTransaction transaction) {
        TransactionSheet sheet = new TransactionSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_ID, transaction.getTransaction_id());
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        header(view, "");
        vm().transactions.getById(requireArguments().getLong(ARG_ID)).observe(getViewLifecycleOwner(), t -> {
            if (transaction != null) return;
            if (t == null) {
                fail(view.findViewById(R.id.error), null, getString(R.string.transaction_not_found));
                view.findViewById(R.id.save).setEnabled(false);
                view.findViewById(R.id.delete).setEnabled(false);
                return;
            }
            transaction = t;
            bind(view);
        });
        getChildFragmentManager().setFragmentResultListener(CategorySheet.RESULT, getViewLifecycleOwner(),
                (key, result) -> {
                    pendingCategory = result.getLong(CategorySheet.RESULT_ID);
                    if (transaction != null) renderCategories(view);
                });
        vm().categoryList.observe(getViewLifecycleOwner(), list -> {
            if (transaction != null) renderCategories(view);
        });
    }

    private boolean income() {
        return transaction.getTransaction_type() == TransactionType.RECEITA;
    }

    private void renderCategories(View view) {
        ChipGroup group = view.findViewById(R.id.categories);
        long keep = pendingCategory != 0 ? pendingCategory : checkedTag(group);
        if (keep == 0 && transaction.getCategory_id() != null) keep = transaction.getCategory_id();
        group.removeAllViews();
        TransactionType type = transaction.getTransaction_type();
        List<Category> list = vm().categoryList.getValue();
        if (list != null) {
            for (Category c : list) {
                if (c.getCategory_type() != type || c.getCategory_parent_id() != null) continue;
                Chip chip = chip(group, c.getCategory_name(), c.getCategory_id());
                if (c.getCategory_id() == keep) {
                    chip.setChecked(true);
                    pendingCategory = 0;
                }
            }
        }
        Chip create = (Chip) getLayoutInflater().inflate(R.layout.item_chip, group, false);
        create.setText(R.string.category_new_chip);
        create.setCheckable(false);
        create.setTextColor(color(R.color.gl_primary));
        create.setOnClickListener(v -> CategorySheet.create(type).show(getChildFragmentManager(), "category"));
        group.addView(create);
    }

    private void bind(View view) {
        boolean income = income();
        Rows.Lookup lookup = Rows.lookup(vm().categoryList.getValue(), vm().accountList.getValue(), vm().cardList.getValue());
        header(view, getString(income ? R.string.transaction_edit_income : R.string.transaction_edit_expense));

        EditText amount = view.findViewById(R.id.amount);
        amount.setText(new BigDecimal(transaction.getTransaction_amount_cents()).movePointLeft(2)
                .toPlainString().replace('.', ','));
        ((TextInputEditText) view.findViewById(R.id.description)).setText(transaction.getTransaction_description());

        String source = transaction.getCard_id() != null ? lookup.cards.get(transaction.getCard_id())
                : lookup.accounts.get(transaction.getAccount_id());
        ((TextView) view.findViewById(R.id.details)).setText(source == null ? ""
                : getString(income ? R.string.transaction_received_in : R.string.transaction_paid_with, source));
        renderCategories(view);

        if (transaction.getRecurrence_id() != null) {
            String name = transaction.getTransaction_description();
            List<Recurrence> recurrences = vm().recurrenceList.getValue();
            if (recurrences != null) {
                for (Recurrence r : recurrences) {
                    if (r.getRecurrence_id() == transaction.getRecurrence_id()) name = r.getRecurrence_description();
                }
            }
            TextView hint = view.findViewById(R.id.hint);
            hint.setText(getString(R.string.transaction_recurring_hint, name));
            hint.setVisibility(View.VISIBLE);
        }

        date = transaction.getTransaction_date();
        MaterialButton dateButton = view.findViewById(R.id.date);
        dateButton.setText(Dates.shortDate(date));
        dateButton.setOnClickListener(v -> new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
            date = LocalDate.of(y, m + 1, d);
            dateButton.setText(Dates.shortDate(date));
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show());

        MaterialSwitch paid = view.findViewById(R.id.paid);
        paid.setText(income ? R.string.transaction_paid_income : R.string.transaction_paid_expense);
        paid.setChecked(transaction.isTransaction_paid());

        TextView error = view.findViewById(R.id.error);
        View save = view.findViewById(R.id.save);
        save.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            long cents = Money.parse(amount.getText().toString());
            if (cents <= 0) {
                fail(error, null, getString(R.string.error_amount));
                return;
            }
            long category = checkedTag(view.findViewById(R.id.categories));
            transaction.setTransaction_amount_cents(cents);
            transaction.setTransaction_description(text(view.findViewById(R.id.description)));
            transaction.setCategory_id(category > 0 ? category : transaction.getCategory_id());
            transaction.setTransaction_date(date);
            transaction.setTransaction_paid(paid.isChecked());
            save.setEnabled(false);
            vm().transactions.update(transaction, callback(error, save, R.string.transaction_saved));
        });

        View delete = view.findViewById(R.id.delete);
        delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.transaction_delete)
                .setMessage(getString(R.string.transaction_delete_confirm,
                        Money.plain(transaction.getTransaction_amount_cents())))
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(R.string.transaction_delete, (d, w) -> {
                    delete.setEnabled(false);
                    vm().transactions.delete(transaction, callback(error, delete, R.string.transaction_deleted));
                })
                .show());
    }

    private RepositoryCallback<Void> callback(TextView error, View button, int message) {
        return new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                done(message);
            }

            @Override
            public void onError(Exception e) {
                fail(error, button, e.getMessage());
            }
        };
    }
}
