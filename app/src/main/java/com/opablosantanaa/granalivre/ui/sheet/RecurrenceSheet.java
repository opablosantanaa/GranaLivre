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
import com.google.android.material.textfield.TextInputEditText;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.RecurrenceFrequency;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.widget.SegmentedControl;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class RecurrenceSheet extends BaseSheet {
    private static final String ARG_TYPE = "type";
    private static final String ARG_ID = "id";
    private static final RecurrenceFrequency[] FREQUENCIES =
            {RecurrenceFrequency.SEMANAL, RecurrenceFrequency.MENSAL, RecurrenceFrequency.ANUAL};

    private TransactionType type = TransactionType.DESPESA;
    private Recurrence editing;
    private LocalDate start = LocalDate.now();
    private int frequency = 1;
    private long pendingCategory;

    public RecurrenceSheet() {
        super(R.layout.sheet_recurrence);
    }

    public static RecurrenceSheet create(TransactionType type) {
        RecurrenceSheet sheet = new RecurrenceSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type.name());
        sheet.setArguments(args);
        return sheet;
    }

    public static RecurrenceSheet edit(Recurrence recurrence) {
        RecurrenceSheet sheet = create(recurrence.getRecurrence_type());
        sheet.requireArguments().putLong(ARG_ID, recurrence.getRecurrence_id());
        return sheet;
    }

    private Recurrence find(long id) {
        List<Recurrence> list = vm().recurrenceList.getValue();
        if (list == null) return null;
        for (Recurrence r : list) if (r.getRecurrence_id() == id) return r;
        return null;
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        type = TransactionType.valueOf(requireArguments().getString(ARG_TYPE, TransactionType.DESPESA.name()));
        long id = requireArguments().getLong(ARG_ID, 0);
        editing = id == 0 ? null : find(id);
        boolean income = type == TransactionType.RECEITA;
        header(view, income ? R.string.recurrence_title_income : R.string.recurrence_title_expense);
        ((TextView) view.findViewById(R.id.source_label)).setText(income ? R.string.entry_receive_in : R.string.entry_pay_with);

        if (editing != null) {
            long cents = editing.getRecurrence_amount_cents();
            ((EditText) view.findViewById(R.id.amount)).setText(
                    String.format(Locale.ROOT, "%d,%02d", cents / 100, cents % 100));
            ((TextInputEditText) view.findViewById(R.id.description)).setText(editing.getRecurrence_description());
            start = editing.getRecurrence_start_date();
            for (int i = 0; i < FREQUENCIES.length; i++) {
                if (FREQUENCIES[i] == editing.getRecurrence_frequency()) frequency = i;
            }
        }

        SegmentedControl segmented = view.findViewById(R.id.frequency);
        segmented.setOptions(getString(R.string.freq_weekly), getString(R.string.freq_monthly), getString(R.string.freq_yearly));
        segmented.setPalette(color(R.color.gl_line), color(R.color.gl_text_secondary),
                color(R.color.gl_text), color(R.color.gl_bg));
        segmented.select(frequency, false);
        segmented.setOnChange(index -> frequency = index);

        MaterialButton startButton = view.findViewById(R.id.start);
        startButton.setText(Dates.shortDate(start));
        startButton.setOnClickListener(v -> new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
            start = LocalDate.of(y, m + 1, d);
            startButton.setText(Dates.shortDate(start));
        }, start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth()).show());

        ChipGroup sources = view.findViewById(R.id.sources);
        List<AccountWithBalance> accounts = vm().accountList.getValue();
        if (accounts != null) {
            for (AccountWithBalance a : accounts) {
                Chip chip = chip(sources, a.account.getAccount_bank_name(), a.account.getAccount_id());
                Long current = editing == null ? null : editing.getAccount_id();
                if (current != null && current == a.account.getAccount_id()) chip.setChecked(true);
            }
        }
        List<Card> cards = vm().cardList.getValue();
        if (cards != null && !income) {
            for (Card card : cards) {
                Chip chip = chip(sources, card.getCard_nickname(), -card.getCard_id());
                Long current = editing == null ? null : editing.getCard_id();
                if (current != null && current == card.getCard_id()) chip.setChecked(true);
            }
        }
        if (sources.getCheckedChipId() == View.NO_ID && sources.getChildCount() > 0) {
            ((Chip) sources.getChildAt(0)).setChecked(true);
        }

        pendingCategory = editing != null && editing.getCategory_id() != null ? editing.getCategory_id() : 0;
        getChildFragmentManager().setFragmentResultListener(CategorySheet.RESULT, getViewLifecycleOwner(),
                (key, result) -> {
                    pendingCategory = result.getLong(CategorySheet.RESULT_ID);
                    renderCategories(view);
                });
        vm().categoryList.observe(getViewLifecycleOwner(), list -> renderCategories(view));

        TextView error = view.findViewById(R.id.error);
        View save = view.findViewById(R.id.save);
        save.setOnClickListener(v -> save(view, error, save));

        if (editing == null) return;
        view.findViewById(R.id.edit_actions).setVisibility(View.VISIBLE);
        MaterialButton pause = view.findViewById(R.id.pause);
        boolean active = editing.isRecurrence_active();
        pause.setText(active ? R.string.recurrence_pause : R.string.recurrence_resume);
        pause.setOnClickListener(v -> {
            editing.setRecurrence_active(!active);
            pause.setEnabled(false);
            vm().recurrences.update(editing, callback(error, pause,
                    active ? R.string.recurrence_paused_toast : R.string.recurrence_resumed_toast));
        });
        View delete = view.findViewById(R.id.delete);
        delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.recurrence_delete)
                .setMessage(getString(R.string.recurrence_delete_confirm, editing.getRecurrence_description()))
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(R.string.recurrence_delete, (d, w) ->
                        vm().recurrences.delete(editing, callback(error, delete, R.string.recurrence_deleted)))
                .show());
    }

    private void renderCategories(View view) {
        ChipGroup group = view.findViewById(R.id.categories);
        long keep = pendingCategory != 0 ? pendingCategory : checkedTag(group);
        group.removeAllViews();
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

    private void save(View view, TextView error, View button) {
        error.setVisibility(View.GONE);
        long cents = Money.parse(((EditText) view.findViewById(R.id.amount)).getText().toString());
        if (cents <= 0) {
            fail(error, null, getString(R.string.error_amount));
            return;
        }
        long source = checkedTag(view.findViewById(R.id.sources));
        if (source == 0) {
            fail(error, null, getString(R.string.error_need_account));
            return;
        }
        long categoryValue = checkedTag(view.findViewById(R.id.categories));
        Recurrence r = editing != null ? editing : new Recurrence();
        r.setUser_id(userId());
        r.setRecurrence_type(type);
        r.setRecurrence_amount_cents(cents);
        r.setRecurrence_description(text(view.findViewById(R.id.description)));
        r.setRecurrence_frequency(FREQUENCIES[frequency]);
        r.setRecurrence_start_date(start);
        r.setAccount_id(source > 0 ? source : null);
        r.setCard_id(source < 0 ? -source : null);
        r.setCategory_id(categoryValue > 0 ? categoryValue : null);
        button.setEnabled(false);
        if (editing == null) {
            vm().recurrences.insert(r, callback(error, button, R.string.recurrence_saved));
        } else {
            vm().recurrences.update(r, callback(error, button, R.string.recurrence_saved));
        }
    }
}
