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
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.domain.InvoiceCalculator;
import com.opablosantanaa.granalivre.domain.InstallmentSplitter;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Installment;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.RecurrenceFrequency;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.entity.Transfer;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.widget.SegmentedControl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class EntrySheet extends BaseSheet {
    public static final int TYPE_EXPENSE = 0;
    public static final int TYPE_INCOME = 1;
    public static final int TYPE_TRANSFER = 2;
    private static final String ARG_TYPE = "type";
    private static final int MAX_INSTALLMENTS = 24;

    private int type;
    private LocalDate date = LocalDate.now();
    private int installments = 1;
    private long pendingCategory;

    public EntrySheet() {
        super(R.layout.sheet_entry);
    }

    public static EntrySheet newInstance(int type) {
        EntrySheet sheet = new EntrySheet();
        Bundle args = new Bundle();
        args.putInt(ARG_TYPE, type);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        type = requireArguments().getInt(ARG_TYPE, TYPE_EXPENSE);
        SegmentedControl segmented = view.findViewById(R.id.type);
        segmented.setOptions(getString(R.string.entry_type_expense), getString(R.string.entry_type_income),
                getString(R.string.entry_type_transfer));
        segmented.setPalette(color(R.color.gl_line), color(R.color.gl_text_secondary),
                color(R.color.gl_text), color(R.color.gl_bg));
        segmented.select(type, false);
        segmented.setOnChange(index -> {
            type = index;
            installments = 1;
            render(view);
        });

        MaterialButton dateButton = view.findViewById(R.id.date);
        dateButton.setOnClickListener(v -> new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
            date = LocalDate.of(y, m + 1, d);
            dateButton.setText(Dates.relativeDay(date));
            updateDue(view);
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show());
        dateButton.setText(Dates.relativeDay(date));

        EditText amount = view.findViewById(R.id.amount);
        amount.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) { updateInstallments(view); }
        });
        view.findViewById(R.id.less).setOnClickListener(v -> {
            installments = Math.max(1, installments - 1);
            updateInstallments(view);
        });
        view.findViewById(R.id.more).setOnClickListener(v -> {
            installments = Math.min(MAX_INSTALLMENTS, installments + 1);
            updateInstallments(view);
        });
        // conta e cartão ficam em linhas separadas, mas só uma origem pode estar marcada
        ChipGroup sourcesGroup = view.findViewById(R.id.sources);
        ChipGroup cardsGroup = view.findViewById(R.id.cards);
        sourcesGroup.setOnCheckedStateChangeListener((g, ids) -> {
            if (!ids.isEmpty() && cardsGroup.getCheckedChipId() != View.NO_ID) cardsGroup.clearCheck();
            if (!sourceIsCard(view)) installments = 1;
            updateInstallments(view);
        });
        cardsGroup.setOnCheckedStateChangeListener((g, ids) -> {
            if (!ids.isEmpty() && sourcesGroup.getCheckedChipId() != View.NO_ID) sourcesGroup.clearCheck();
            if (!sourceIsCard(view)) installments = 1;
            updateInstallments(view);
        });
        view.findViewById(R.id.save).setOnClickListener(v -> save(view));
        render(view);
        getChildFragmentManager().setFragmentResultListener(CategorySheet.RESULT, getViewLifecycleOwner(),
                (key, result) -> {
                    pendingCategory = result.getLong(CategorySheet.RESULT_ID);
                    if (renderCategories(view, pendingCategory)) pendingCategory = 0;
                });
        vm().categoryList.observe(getViewLifecycleOwner(), list -> {
            if (renderCategories(view, pendingCategory)) pendingCategory = 0;
        });
    }

    private boolean sourceIsCard(View view) {
        return checkedTag(view.findViewById(R.id.cards)) > 0;
    }

    /** Origem escolhida: id da conta (positivo) ou id do cartão com sinal negativo; 0 se nenhuma. */
    private long checkedSource(View view) {
        long card = checkedTag(view.findViewById(R.id.cards));
        return card > 0 ? -card : checkedTag(view.findViewById(R.id.sources));
    }

    private Card checkedCard(View view) {
        long id = checkedTag(view.findViewById(R.id.cards));
        List<Card> cards = vm().cardList.getValue();
        if (id <= 0 || cards == null) return null;
        for (Card card : cards) if (card.getCard_id() == id) return card;
        return null;
    }

    /** Compra no cartão: a data de pagamento é sempre o vencimento da fatura em que a compra cai. */
    private void updateDue(View view) {
        Card card = type == TYPE_EXPENSE ? checkedCard(view) : null;
        TextView due = view.findViewById(R.id.due_info);
        ((TextView) view.findViewById(R.id.date_label)).setText(
                card == null ? R.string.entry_date : R.string.entry_purchase_date);
        if (card == null) {
            due.setVisibility(View.GONE);
            return;
        }
        YearMonth month = InvoiceCalculator.referenceMonth(date, card.getCard_closing_day());
        LocalDate dueDate = InvoiceCalculator.dueDate(month, card.getCard_closing_day(), card.getCard_due_day());
        due.setText(getString(R.string.entry_card_due, Dates.shortDate(dueDate), Dates.month(month)));
        due.setVisibility(View.VISIBLE);
    }

    private TransactionType categoryType() {
        return type == TYPE_INCOME ? TransactionType.RECEITA : TransactionType.DESPESA;
    }

    private boolean renderCategories(View view, long select) {
        ChipGroup categories = view.findViewById(R.id.categories);
        long keep = select != 0 ? select : checkedTag(categories);
        categories.removeAllViews();
        if (type == TYPE_TRANSFER) return false;
        boolean found = false;
        TransactionType wanted = categoryType();
        List<Category> list = vm().categoryList.getValue();
        if (list != null) {
            for (Category c : list) {
                if (c.getCategory_type() == wanted && c.getCategory_parent_id() == null) {
                    Chip chip = chip(categories, c.getCategory_name(), c.getCategory_id());
                    if (c.getCategory_id() == keep) {
                        chip.setChecked(true);
                        found = c.getCategory_id() == select;
                    }
                }
            }
        }
        Chip create = (Chip) getLayoutInflater().inflate(R.layout.item_chip, categories, false);
        create.setText(R.string.category_new_chip);
        create.setCheckable(false);
        create.setTextColor(color(R.color.gl_primary));
        create.setOnClickListener(v ->
                CategorySheet.create(categoryType()).show(getChildFragmentManager(), "category"));
        categories.addView(create);
        return found;
    }

    private void render(View view) {
        boolean transfer = type == TYPE_TRANSFER;
        header(view, type == TYPE_EXPENSE ? R.string.entry_expense_title
                : type == TYPE_INCOME ? R.string.entry_income_title : R.string.entry_transfer_title);
        ((MaterialButton) view.findViewById(R.id.save)).setText(type == TYPE_EXPENSE ? R.string.entry_save_expense
                : type == TYPE_INCOME ? R.string.entry_save_income : R.string.entry_save_transfer);
        view.findViewById(R.id.error).setVisibility(View.GONE);

        ChipGroup categories = view.findViewById(R.id.categories);
        renderCategories(view, 0);
        categories.setVisibility(transfer ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.category_label).setVisibility(transfer ? View.GONE : View.VISIBLE);

        ((TextView) view.findViewById(R.id.source_label)).setText(type == TYPE_EXPENSE ? R.string.entry_pay_with
                : type == TYPE_INCOME ? R.string.entry_receive_in : R.string.entry_from);
        ChipGroup sources = view.findViewById(R.id.sources);
        ChipGroup targets = view.findViewById(R.id.targets);
        sources.removeAllViews();
        targets.removeAllViews();
        List<AccountWithBalance> accounts = vm().accountList.getValue();
        if (accounts != null) {
            for (AccountWithBalance a : accounts) {
                chip(sources, a.account.getAccount_bank_name(), a.account.getAccount_id());
                if (transfer) chip(targets, a.account.getAccount_bank_name(), a.account.getAccount_id());
            }
        }
        ChipGroup cardChips = view.findViewById(R.id.cards);
        cardChips.removeAllViews();
        List<Card> cards = vm().cardList.getValue();
        if (cards != null && type == TYPE_EXPENSE) {
            for (Card card : cards) chip(cardChips, card.getCard_nickname(), card.getCard_id());
        }
        boolean showCards = cardChips.getChildCount() > 0;
        cardChips.setVisibility(showCards ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.card_label).setVisibility(showCards ? View.VISIBLE : View.GONE);
        if (sources.getChildCount() > 0) ((Chip) sources.getChildAt(0)).setChecked(true);
        if (targets.getChildCount() > 1) ((Chip) targets.getChildAt(1)).setChecked(true);
        targets.setVisibility(transfer ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.target_label).setVisibility(transfer ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.repeat).setVisibility(transfer ? View.GONE : View.VISIBLE);
        updateInstallments(view);
    }

    private void updateInstallments(View view) {
        updateDue(view);
        boolean visible = type == TYPE_EXPENSE && sourceIsCard(view);
        view.findViewById(R.id.installments_row).setVisibility(visible ? View.VISIBLE : View.GONE);
        MaterialSwitch repeat = view.findViewById(R.id.repeat);
        repeat.setEnabled(installments == 1);
        if (installments > 1) repeat.setChecked(false);
        ((TextView) view.findViewById(R.id.installments)).setText(installments + "x");
        TextView preview = view.findViewById(R.id.installments_preview);
        long total = Money.parse(((EditText) view.findViewById(R.id.amount)).getText().toString());
        if (installments < 2 || total < installments) {
            preview.setText("");
            preview.setVisibility(View.GONE);
            return;
        }
        List<Long> parts = InstallmentSplitter.split(total, installments);
        preview.setVisibility(View.VISIBLE);
        if (parts.get(0).equals(parts.get(1))) {
            preview.setText(getString(R.string.entry_installments_preview, installments, Money.plain(parts.get(0))));
        } else {
            preview.setText(getString(R.string.entry_installments_preview_first, Money.plain(parts.get(0)),
                    installments - 1, Money.plain(parts.get(1))));
        }
    }

    private void save(View view) {
        TextView error = view.findViewById(R.id.error);
        View button = view.findViewById(R.id.save);
        error.setVisibility(View.GONE);
        long cents = Money.parse(((EditText) view.findViewById(R.id.amount)).getText().toString());
        if (cents <= 0) {
            fail(error, null, getString(R.string.error_amount));
            return;
        }
        long source = checkedSource(view);
        if (source == 0) {
            fail(error, null, getString(vm().accountList.getValue() == null
                    || vm().accountList.getValue().isEmpty() ? R.string.error_need_account : R.string.error_source));
            return;
        }
        String description = text(view.findViewById(R.id.description));
        button.setEnabled(false);

        if (type == TYPE_TRANSFER) {
            long target = checkedTag(view.findViewById(R.id.targets));
            if (target == 0 || target == source) {
                fail(error, button, getString(R.string.error_same_account));
                return;
            }
            Transfer transfer = new Transfer();
            transfer.setUser_id(userId());
            transfer.setTransfer_from_account_id(source);
            transfer.setTransfer_to_account_id(target);
            transfer.setTransfer_amount_cents(cents);
            transfer.setTransfer_date(date);
            transfer.setTransfer_description(description);
            vm().transfers.insert(transfer, callback(error, button, R.string.entry_saved_transfer));
            return;
        }

        TransactionType kind = type == TYPE_INCOME ? TransactionType.RECEITA : TransactionType.DESPESA;
        Long accountId = source > 0 ? source : null;
        Long cardId = source < 0 ? -source : null;
        long categoryValue = checkedTag(view.findViewById(R.id.categories));
        Long categoryId = categoryValue > 0 ? categoryValue : null;
        int message = type == TYPE_INCOME ? R.string.entry_saved_income : R.string.entry_saved_expense;

        if (installments > 1) {
            if (description.isEmpty()) {
                fail(error, button, getString(R.string.error_description));
                return;
            }
            Installment installment = new Installment();
            installment.setUser_id(userId());
            installment.setInstallment_description(description);
            installment.setInstallment_total_cents(cents);
            installment.setInstallment_count(installments);
            installment.setInstallment_first_date(date);
            vm().installments.create(installment, accountId, cardId, categoryId, callback(error, button, message));
            return;
        }

        FinancialTransaction t = new FinancialTransaction();
        t.setUser_id(userId());
        t.setTransaction_type(kind);
        t.setTransaction_amount_cents(cents);
        t.setTransaction_description(description);
        t.setTransaction_date(date);
        t.setTransaction_paid(!date.isAfter(LocalDate.now()));
        t.setAccount_id(accountId);
        t.setCard_id(cardId);
        t.setCategory_id(categoryId);
        boolean repeat = ((MaterialSwitch) view.findViewById(R.id.repeat)).isChecked();
        vm().transactions.insert(t, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                if (!repeat) {
                    done(message);
                    return;
                }
                Recurrence r = new Recurrence();
                r.setUser_id(userId());
                r.setRecurrence_type(kind);
                r.setRecurrence_amount_cents(cents);
                r.setRecurrence_description(description);
                r.setRecurrence_frequency(RecurrenceFrequency.MENSAL);
                r.setRecurrence_start_date(date.plusMonths(1));
                r.setAccount_id(accountId);
                r.setCard_id(cardId);
                r.setCategory_id(categoryId);
                vm().recurrences.insert(r, callback(error, button, message));
            }

            @Override
            public void onError(Exception e) {
                fail(error, button, e.getMessage());
            }
        });
    }

    private <T> RepositoryCallback<T> callback(TextView error, View button, int message) {
        return new RepositoryCallback<T>() {
            @Override
            public void onSuccess(T result) {
                done(message);
            }

            @Override
            public void onError(Exception e) {
                fail(error, button, e.getMessage());
            }
        };
    }
}
