package com.opablosantanaa.granalivre.ui.sheet;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.Rows;

import java.time.LocalDate;
import java.util.List;

public class TransactionSheet extends BaseSheet {
    private static final String ARG_ID = "id";

    private FinancialTransaction transaction;
    private LocalDate date;

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
                return;
            }
            transaction = t;
            bind(view);
        });
    }

    private void bind(View view) {
        boolean income = transaction.getTransaction_type() == TransactionType.RECEITA;
        Rows.Lookup lookup = Rows.lookup(vm().categoryList.getValue(), vm().accountList.getValue(), vm().cardList.getValue());
        Category category = transaction.getCategory_id() == null ? null : lookup.categories.get(transaction.getCategory_id());
        String title = transaction.getTransaction_description();
        if (title == null || title.trim().isEmpty()) {
            title = category != null ? category.getCategory_name()
                    : getString(income ? R.string.entry_type_income : R.string.entry_type_expense);
        }
        header(view, title);

        TextView amount = view.findViewById(R.id.amount);
        long cents = income ? transaction.getTransaction_amount_cents() : -transaction.getTransaction_amount_cents();
        amount.setText(Money.styled(cents, 0, true));
        amount.setTextColor(color(income ? R.color.gl_income : R.color.gl_text));

        String source = transaction.getCard_id() != null ? lookup.cards.get(transaction.getCard_id())
                : lookup.accounts.get(transaction.getAccount_id());
        StringBuilder details = new StringBuilder();
        if (category != null) details.append(category.getCategory_name());
        if (source != null) {
            if (details.length() > 0) details.append(", ");
            details.append(source);
        }
        ((TextView) view.findViewById(R.id.details)).setText(details);

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
            transaction.setTransaction_date(date);
            transaction.setTransaction_paid(paid.isChecked());
            save.setEnabled(false);
            vm().transactions.update(transaction, new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    done(R.string.transaction_saved);
                }

                @Override
                public void onError(Exception e) {
                    fail(error, save, e.getMessage());
                }
            });
        });
    }
}
