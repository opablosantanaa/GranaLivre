package com.opablosantanaa.granalivre.ui.screen;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.opablosantanaa.granalivre.App;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.RecurrenceFrequency;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.ui.CategoryStyle;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.MainViewModel;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.RecurrenceMath;
import com.opablosantanaa.granalivre.ui.RecurrenceStatus;
import com.opablosantanaa.granalivre.ui.Rows;
import com.opablosantanaa.granalivre.ui.sheet.AccountSheet;
import com.opablosantanaa.granalivre.ui.sheet.RecurrenceSheet;
import com.opablosantanaa.granalivre.ui.widget.SegmentedControl;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;

public class RecurringScreen extends Screen {
    private static final String STATE_TYPE = "type";
    /** Tipo pedido antes de a aba existir (ex.: aviso da tela inicial). */
    public static TransactionType pendingType;

    private TransactionType type = TransactionType.DESPESA;

    public RecurringScreen() {
        super(R.layout.screen_recurring);
    }

    private TransactionType type() {
        return type;
    }

    private boolean income() {
        return type == TransactionType.RECEITA;
    }

    public void setType(TransactionType next) {
        if (next == type) return;
        type = next;
        View view = getView();
        if (view == null) return;
        ((SegmentedControl) view.findViewById(R.id.type)).select(income() ? 1 : 0, false);
        bindType(view);
        renderLater(view);
    }

    @Override
    public int tab() {
        return App.TAB_RECURRING;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TYPE, type.name());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            type = TransactionType.valueOf(savedInstanceState.getString(STATE_TYPE, type.name()));
        }
        if (pendingType != null) {
            type = pendingType;
            pendingType = null;
        }
        padTop(view.findViewById(R.id.header), 16);
        padBottomForTabs(view.findViewById(R.id.sheet), 8);
        SegmentedControl typeControl = view.findViewById(R.id.type);
        typeControl.setOptions(getString(R.string.analytics_type_expense), getString(R.string.analytics_type_income));
        typeControl.setPalette(color(R.color.gl_on_silk_line), color(R.color.gl_on_silk_secondary),
                color(R.color.gl_on_silk), color(R.color.gl_silk_backdrop));
        typeControl.setContentDescription(getString(R.string.recurring_type_choice));
        typeControl.select(income() ? 1 : 0, false);
        typeControl.setOnChange(index -> {
            TransactionType next = index == 1 ? TransactionType.RECEITA : TransactionType.DESPESA;
            if (next == type) return;
            type = next;
            bindType(view);
            renderLater(view);
        });
        bindType(view);
        MaterialButton create = view.findViewById(R.id.create);
        create.setOnClickListener(v -> {
            List<?> accounts = vm().accountList.getValue();
            if (accounts == null || accounts.isEmpty()) {
                new AccountSheet().show(getChildFragmentManager(), "account");
                return;
            }
            RecurrenceSheet.create(type()).show(getChildFragmentManager(), "recurrence");
        });

        MainViewModel vm = vm();
        vm.recurrenceList.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.categoryList.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.accountList.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.cardList.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.pendingList.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.history.observe(getViewLifecycleOwner(), v -> renderLater(view));
    }

    private void bindType(View view) {
        ((MaterialButton) view.findViewById(R.id.create)).setText(
                income() ? R.string.recurring_new_income : R.string.recurring_new_expense);
    }

    private String rule(Recurrence r) {
        LocalDate start = r.getRecurrence_start_date();
        switch (r.getRecurrence_frequency()) {
            case SEMANAL:
                return getString(R.string.recurring_every_week,
                        start.getDayOfWeek().getDisplayName(TextStyle.FULL, Money.BR));
            case ANUAL:
                return getString(R.string.recurring_every_year, Dates.dayMonth(start));
            case MENSAL:
            default:
                return getString(R.string.recurring_every_month, start.getDayOfMonth());
        }
    }

    private void section(LinearLayout sheet, int title) {
        View header = getLayoutInflater().inflate(R.layout.item_section, sheet, false);
        ((TextView) header.findViewById(R.id.section_title)).setText(title);
        sheet.addView(header);
    }

    private void row(LinearLayout sheet, Recurrence r, Rows.Lookup lookup) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_recurring, sheet, false);
        Category category = r.getCategory_id() == null ? null : lookup.categories.get(r.getCategory_id());
        int tint = r.isRecurrence_active() ? CategoryStyle.color(category) : color(R.color.gl_text_secondary);
        ImageView icon = row.findViewById(R.id.icon);
        icon.setImageResource(CategoryStyle.icon(category, r.getRecurrence_type()));
        icon.setImageTintList(ColorStateList.valueOf(tint));
        row.findViewById(R.id.icon_bg).setBackgroundTintList(ColorStateList.valueOf(CategoryStyle.tint(tint, 0x29)));
        ((TextView) row.findViewById(R.id.title)).setText(r.getRecurrence_description());

        String source = r.getCard_id() != null ? lookup.cards.get(r.getCard_id()) : lookup.accounts.get(r.getAccount_id());
        StringBuilder subtitle = new StringBuilder(rule(r));
        if (source != null) subtitle.append(", ").append(source);
        if (category != null) subtitle.append(", ").append(category.getCategory_name());
        ((TextView) row.findViewById(R.id.subtitle)).setText(subtitle);

        long cents = income() ? r.getRecurrence_amount_cents() : -r.getRecurrence_amount_cents();
        TextView amount = row.findViewById(R.id.amount);
        amount.setText(Money.styled(cents, color(R.color.gl_text_secondary), true));
        amount.setTextColor(color(!r.isRecurrence_active() ? R.color.gl_text_secondary
                : income() ? R.color.gl_income : R.color.gl_text));

        RecurrenceStatus status = RecurrenceStatus.of(r, vm().pendingList.getValue(), vm().history.getValue(), LocalDate.now());
        TextView next = row.findViewById(R.id.next);
        next.setText(status.partial(r) ? getString(R.string.recurring_open_left, Money.plain(status.openCents))
                : frequencyName(r.getRecurrence_frequency()));
        TextView statusView = row.findViewById(R.id.status);
        int statusColor = statusColor(status);
        statusView.setText(statusText(status));
        statusView.setTextColor(statusColor);
        TextViewCompat.setCompoundDrawableTintList(statusView, ColorStateList.valueOf(statusColor));
        MaterialButton settle = row.findViewById(R.id.settle);
        settle.setVisibility(status.canSettle() ? View.VISIBLE : View.GONE);
        settle.setText(income() ? R.string.settle_receive : R.string.settle_pay);
        settle.setContentDescription(settle.getText() + ", " + r.getRecurrence_description());
        settle.setOnClickListener(v -> confirmSettle(r, status, settle));

        row.findViewById(R.id.row_main).setContentDescription(r.getRecurrence_description() + ", " + subtitle + ", "
                + Money.plain(cents) + ", " + statusView.getText());
        row.findViewById(R.id.row_main).setOnClickListener(v ->
                RecurrenceSheet.edit(r).show(getChildFragmentManager(), "recurrence"));
        sheet.addView(row);
    }

    private String frequencyName(RecurrenceFrequency frequency) {
        switch (frequency) {
            case SEMANAL: return getString(R.string.freq_weekly);
            case ANUAL: return getString(R.string.freq_yearly);
            case MENSAL:
            default: return getString(R.string.freq_monthly);
        }
    }

    private int statusColor(RecurrenceStatus status) {
        switch (status.kind) {
            case OVERDUE: return color(R.color.gl_expense);
            case DUE_TODAY:
            case SOON: return color(R.color.gl_accent);
            case PAID: return color(R.color.gl_income);
            default: return color(R.color.gl_text_secondary);
        }
    }

    private String statusText(RecurrenceStatus status) {
        boolean in = income();
        String date = status.date == null ? "" : Dates.dayMonth(status.date);
        switch (status.kind) {
            case OVERDUE:
                if (status.overdueCount > 1) return getString(R.string.status_overdue_many, status.overdueCount, date);
                return getString(in ? R.string.status_overdue_income : R.string.status_overdue_expense, date);
            case DUE_TODAY:
                return getString(in ? R.string.status_due_today_income : R.string.status_due_today);
            case SOON:
                if (status.daysUntil == 1) {
                    return getString(in ? R.string.status_soon_income_one : R.string.status_soon_one, date);
                }
                return getString(in ? R.string.status_soon_income : R.string.status_soon, (int) status.daysUntil, date);
            case PAID:
                return getString(in ? R.string.status_received : R.string.status_paid, date);
            case PAUSED:
                return getString(R.string.status_paused);
            case ENDED:
                return getString(R.string.status_ended);
            case UPCOMING:
            default:
                return getString(R.string.status_upcoming, date);
        }
    }

    private void confirmSettle(Recurrence r, RecurrenceStatus status, View button) {
        LocalDate today = LocalDate.now();
        long open = status.openCents > 0 ? status.openCents : r.getRecurrence_amount_cents();
        String amount = Money.plain(open);
        String message = status.date == null || status.date.equals(today)
                ? getString(R.string.settle_message_same, amount, r.getRecurrence_description(), Dates.shortDate(today))
                : getString(R.string.settle_message, amount, r.getRecurrence_description(), Dates.shortDate(today),
                Dates.shortDate(status.date));
        int title = income() ? R.string.settle_receive_title : R.string.settle_pay_title;

        View content = getLayoutInflater().inflate(R.layout.dialog_settle, null, false);
        ((TextView) content.findViewById(R.id.message)).setText(message);
        ((TextView) content.findViewById(R.id.mode_full)).setText(getString(R.string.settle_full, amount));
        RadioGroup mode = content.findViewById(R.id.mode);
        View partialRow = content.findViewById(R.id.partial_row);
        View partialHint = content.findViewById(R.id.partial_hint);
        EditText partialAmount = content.findViewById(R.id.amount);
        TextView error = content.findViewById(R.id.error);
        mode.setOnCheckedChangeListener((group, checked) -> {
            boolean partial = checked == R.id.mode_partial;
            partialRow.setVisibility(partial ? View.VISIBLE : View.GONE);
            partialHint.setVisibility(partial ? View.VISIBLE : View.GONE);
            content.findViewById(R.id.message).setVisibility(partial ? View.GONE : View.VISIBLE);
            error.setVisibility(View.GONE);
            if (partial) partialAmount.requestFocus();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(content)
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(title, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (mode.getCheckedRadioButtonId() != R.id.mode_partial) {
                dialog.dismiss();
                button.setEnabled(false);
                vm().recurrences.settle(r, today, settleCallback(button, false));
                return;
            }
            long cents = Money.parse(partialAmount.getText().toString());
            if (cents <= 0) {
                error.setText(R.string.error_amount);
                error.setVisibility(View.VISIBLE);
                return;
            }
            if (cents > open) {
                error.setText(getString(R.string.settle_partial_too_much, amount));
                error.setVisibility(View.VISIBLE);
                return;
            }
            dialog.dismiss();
            button.setEnabled(false);
            vm().recurrences.settlePartial(r, cents, today, new RepositoryCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean settled) {
                    settleCallback(button, !settled).onSuccess(null);
                }

                @Override
                public void onError(Exception e) {
                    settleCallback(button, true).onError(e);
                }
            });
        }));
        dialog.show();
    }

    private RepositoryCallback<Void> settleCallback(View button, boolean partial) {
        return new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), partial ? R.string.settle_partial_toast
                        : income() ? R.string.settle_received_toast : R.string.settle_paid_toast,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                button.setEnabled(true);
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
    }

    private void renderLater(View view) {
        later("render", () -> render(view));
    }

    private void render(View view) {
        MainViewModel vm = vm();
        LinearLayout sheet = view.findViewById(R.id.sheet);
        sheet.removeAllViews();
        List<Recurrence> all = vm.recurrenceList.getValue();
        List<Recurrence> mine = new ArrayList<>();
        if (all != null) {
            for (Recurrence r : all) if (r.getRecurrence_type() == type()) mine.add(r);
        }
        long total = 0;
        int active = 0;
        for (Recurrence r : mine) {
            if (!r.isRecurrence_active()) continue;
            active++;
            total += RecurrenceMath.monthlyCents(r);
        }
        ((TextView) view.findViewById(R.id.total)).setText(
                Money.styled(income() ? total : -total, color(R.color.gl_on_silk_secondary)));
        ((TextView) view.findViewById(R.id.summary)).setText(
                getString(R.string.recurring_summary, active, mine.size() - active));

        if (mine.isEmpty()) {
            TextView empty = new TextView(requireContext(), null, 0, R.style.Text_Body);
            empty.setText(income() ? R.string.recurring_empty_income : R.string.recurring_empty_expense);
            empty.setTextColor(color(R.color.gl_text_secondary));
            empty.setPadding(0, dp(16), 0, dp(12));
            sheet.addView(empty);
            return;
        }
        Rows.Lookup lookup = Rows.lookup(vm.categoryList.getValue(), vm.accountList.getValue(), vm.cardList.getValue());
        LocalDate today = LocalDate.now();
        List<Recurrence> attention = new ArrayList<>();
        for (Recurrence r : mine) {
            if (RecurrenceStatus.of(r, vm.pendingList.getValue(), vm.history.getValue(), today).needsAttention()) {
                attention.add(r);
            }
        }
        if (!attention.isEmpty()) {
            section(sheet, R.string.recurring_attention);
            for (Recurrence r : attention) row(sheet, r, lookup);
        }
        RecurrenceFrequency[] order = {RecurrenceFrequency.MENSAL, RecurrenceFrequency.SEMANAL, RecurrenceFrequency.ANUAL};
        int[] titles = {R.string.recurring_monthly, R.string.recurring_weekly, R.string.recurring_yearly};
        for (int i = 0; i < order.length; i++) {
            boolean header = false;
            for (Recurrence r : mine) {
                if (!r.isRecurrence_active() || r.getRecurrence_frequency() != order[i] || attention.contains(r)) continue;
                if (!header) {
                    section(sheet, titles[i]);
                    header = true;
                }
                row(sheet, r, lookup);
            }
        }
        boolean header = false;
        for (Recurrence r : mine) {
            if (r.isRecurrence_active()) continue;
            if (!header) {
                section(sheet, R.string.recurring_paused);
                header = true;
            }
            row(sheet, r, lookup);
        }
    }
}
