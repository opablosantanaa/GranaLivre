package com.opablosantanaa.granalivre.ui.sheet;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.domain.ofx.OfxStatement;
import com.opablosantanaa.granalivre.domain.DefaultCategories;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.repository.StatementImportRepository;
import com.opablosantanaa.granalivre.repository.StatementImportRepository.Preview;
import com.opablosantanaa.granalivre.repository.StatementImportRepository.Row;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.MainViewModel;
import com.opablosantanaa.granalivre.ui.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ImportSheet extends BaseSheet {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    private OfxStatement statement;
    private Preview preview;
    private long accountId;
    private long cardId;
    private View root;
    private View header;
    private final RowsAdapter adapter = new RowsAdapter();

    public ImportSheet() {
        super(R.layout.sheet_import);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (!(dialog instanceof BottomSheetDialog)) return;
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        sheet.setLayoutParams(params);
        BottomSheetBehavior<?> behavior = ((BottomSheetDialog) dialog).getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        statement = vm().pendingImport;
        if (statement == null) {
            dismiss();
            return;
        }
        root = view;
        header(view, statement.creditCard ? R.string.import_title_invoice : R.string.import_title);
        RecyclerView rows = view.findViewById(R.id.rows);
        rows.setLayoutManager(new LinearLayoutManager(requireContext()));
        header = getLayoutInflater().inflate(R.layout.item_import_header, rows, false);
        rows.setAdapter(adapter);

        boolean named = statement.bankId != null && statement.bankId.matches(".*\\p{L}.*");
        String source = statement.bankId != null && statement.accountId != null
                ? getString(named ? R.string.import_bank_named : R.string.import_bank, statement.bankId, statement.accountId)
                : statement.bankId != null && named ? statement.bankId
                : statement.accountId != null ? statement.accountId : getString(R.string.app_name);
        int count = statement.transactions.size();
        ((TextView) header.findViewById(R.id.source)).setText(count == 1
                ? getString(R.string.import_source_one, source) : getString(R.string.import_source, source, count));
        TextView period = header.findViewById(R.id.period);
        if (statement.start != null && statement.end != null) {
            period.setText(getString(R.string.import_period, Dates.shortDate(statement.start), Dates.shortDate(statement.end)));
        } else {
            period.setVisibility(View.GONE);
        }
        ChipGroup accounts = header.findViewById(R.id.accounts);
        List<AccountWithBalance> list = vm().accountList.getValue();
        List<Card> cards = vm().cardList.getValue();
        if (!statement.creditCard && list != null) {
            for (AccountWithBalance a : list) {
                chip(accounts, a.account.getAccount_bank_name(), a.account.getAccount_id());
            }
        }
        if (statement.creditCard && cards != null) {
            for (Card c : cards) chip(accounts, c.getCard_nickname(), -c.getCard_id());
        }
        accounts.setOnCheckedStateChangeListener((group, ids) -> load(checkedTag(accounts)));
        if (accounts.getChildCount() > 0) ((Chip) accounts.getChildAt(0)).setChecked(true);

        ((MaterialSwitch) header.findViewById(R.id.adjust)).setOnCheckedChangeListener((b, checked) -> updateButton());
        view.findViewById(R.id.save).setOnClickListener(v -> save());
        updateButton();
    }

    private void load(long target) {
        if (target == 0) return;
        accountId = Math.max(target, 0);
        cardId = Math.max(-target, 0);
        preview = null;
        adapter.notifyDataSetChanged();
        root.findViewById(R.id.save).setEnabled(false);
        RepositoryCallback<Preview> callback = new RepositoryCallback<Preview>() {
            @Override
            public void onSuccess(Preview result) {
                if (!isAdded() || accountId != Math.max(target, 0) || cardId != Math.max(-target, 0)) return;
                preview = result;
                int fresh = 0;
                for (Row row : preview.rows) if (!row.duplicate) fresh++;
                int matched = preview.matchedCount();
                ((TextView) header.findViewById(R.id.counts)).setText(cardId > 0
                        ? getString(R.string.import_counts_card, fresh, preview.rows.size() - fresh)
                        : matched == 0
                        ? getString(R.string.import_counts, fresh, preview.rows.size() - fresh)
                        : getString(R.string.import_counts_matched, fresh - matched,
                                preview.rows.size() - fresh, matched));
                adapter.notifyDataSetChanged();
                renderReconcile();
            }

            @Override
            public void onError(Exception e) {
                fail(root.findViewById(R.id.error), null, e.getMessage());
            }
        };
        if (cardId > 0) vm().imports.previewCard(vm().getUserId(), cardId, statement, callback);
        else vm().imports.preview(vm().getUserId(), accountId, statement, callback);
    }

    private int blockLabel(StatementImportRepository.Block block) {
        switch (block) {
            case CHARGE_DUPLICATE: return R.string.import_charge_duplicate;
            case CREDIT: return R.string.import_card_credit;
            case PAID_INVOICE: return R.string.import_paid_invoice;
            default: return R.string.import_duplicate;
        }
    }

    private String categoryName(Row row) {
        Category category = MainViewModel.find(vm().categoryList.getValue(), row.categoryId);
        return category == null ? DefaultCategories.fallbackName(row.type()) : category.getCategory_name();
    }

    private void bindRow(View item, Row row) {
        ((TextView) item.findViewById(R.id.title)).setText(row.source.description.isEmpty()
                ? getString(row.source.isIncome() ? R.string.entry_type_income : R.string.entry_type_expense)
                : row.source.description);
        ((TextView) item.findViewById(R.id.date)).setText(Dates.shortDate(row.source.date));
        TextView amount = item.findViewById(R.id.amount);
        amount.setText(Money.styled(row.source.amountCents, 0, true));
        amount.setTextColor(color(row.source.isIncome() ? R.color.gl_income : R.color.gl_text));

        MaterialCheckBox check = item.findViewById(R.id.check);
        check.setOnCheckedChangeListener(null);
        check.setChecked(row.selected);
        check.setEnabled(!row.duplicate);
        check.setContentDescription(row.source.description + ", " + Money.plain(row.source.amountCents));
        check.setOnCheckedChangeListener((b, checked) -> {
            row.selected = checked;
            renderReconcile();
            if (row.match != null) bindRow(item, row);
        });

        TextView category = item.findViewById(R.id.category);
        if (row.duplicate) {
            category.setText(blockLabel(row.block));
            category.setTextColor(color(R.color.gl_text_secondary));
            category.setOnClickListener(null);
            category.setClickable(false);
            item.setAlpha(0.55f);
        } else if (row.links()) {
            category.setText(getString(row.match.getTransaction_id() == 0 ? R.string.import_matched_upcoming
                    : R.string.import_matched, row.match.getTransaction_description()));
            category.setContentDescription(category.getText());
            category.setTextColor(color(R.color.gl_text_secondary));
            category.setOnClickListener(null);
            category.setClickable(false);
            item.setAlpha(0.55f);
        } else {
            category.setText(categoryName(row));
            category.setTextColor(color(R.color.gl_primary));
            category.setContentDescription(getString(R.string.import_choose_category, row.source.description));
            category.setOnClickListener(v -> pickCategory(category, row));
            item.setAlpha(1f);
        }
    }

    private void pickCategory(TextView anchor, Row row) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        List<Category> options = new ArrayList<>();
        List<Category> all = vm().categoryList.getValue();
        if (all != null) {
            for (Category c : all) {
                if (c.getCategory_type() != row.type() || c.getCategory_parent_id() != null) continue;
                menu.getMenu().add(Menu.NONE, options.size(), options.size(), c.getCategory_name());
                options.add(c);
            }
        }
        menu.setOnMenuItemClickListener(item -> {
            row.categoryId = options.get(item.getItemId()).getCategory_id();
            anchor.setText(categoryName(row));
            return true;
        });
        menu.show();
    }

    private void renderReconcile() {
        if (preview == null) return;
        TextView text = header.findViewById(R.id.reconcile_text);
        MaterialSwitch adjust = header.findViewById(R.id.adjust);
        header.findViewById(R.id.reconcile).setVisibility(cardId > 0 ? View.GONE : View.VISIBLE);
        if (cardId > 0) {
            adjust.setChecked(false);
            updateButton();
            return;
        }
        if (statement.ledgerBalanceCents == null || preview.balanceAtLedgerCents == null) {
            text.setText(R.string.import_no_ledger);
            adjust.setVisibility(View.GONE);
            adjust.setChecked(false);
            updateButton();
            return;
        }
        String date = Dates.shortDate(statement.ledgerBalanceDate == null ? LocalDate.now() : statement.ledgerBalanceDate);
        long selectedNet = preview.selectedNet(null);
        long projected = preview.balanceNowCents + selectedNet;
        long adjustmentCents = -selectedNet;
        String body = getString(R.string.import_ledger, date, Money.plain(statement.ledgerBalanceCents)) + "\n"
                + getString(R.string.import_current_balance, Money.plain(preview.balanceNowCents)) + "\n"
                + getString(R.string.import_projected_balance, Money.plain(projected));
        text.setText(body);
        adjust.setVisibility(adjustmentCents == 0 ? View.GONE : View.VISIBLE);
        adjust.setText(getString(R.string.import_adjust, Money.plain(adjustmentCents)));
        if (adjustmentCents == 0) adjust.setChecked(false);
        updateButton();
    }

    private int selectedCount() {
        int count = 0;
        if (preview == null) return 0;
        for (Row row : preview.rows) if (row.selected && !row.duplicate) count++;
        return count;
    }

    private void updateButton() {
        if (root == null) return;
        MaterialButton save = root.findViewById(R.id.save);
        int count = selectedCount();
        int links = preview == null ? 0 : preview.linkCount();
        boolean adjust = ((MaterialSwitch) header.findViewById(R.id.adjust)).isChecked();
        if (count == 0 && adjust) save.setText(R.string.import_save_adjust_only);
        else if (count == 0 && links > 0) save.setText(R.string.import_save_link_only);
        else if (count == 0) save.setText(R.string.import_nothing);
        else if (count == 1) save.setText(R.string.import_save_one);
        else save.setText(getString(R.string.import_save, count));
        boolean enabled = preview != null && (count > 0 || links > 0 || adjust);
        save.setEnabled(enabled);
        save.setAlpha(enabled ? 1f : 0.45f);
    }

    private void save() {
        TextView error = root.findViewById(R.id.error);
        View save = root.findViewById(R.id.save);
        error.setVisibility(View.GONE);
        boolean adjust = ((MaterialSwitch) header.findViewById(R.id.adjust)).isChecked();
        save.setEnabled(false);
        RepositoryCallback<StatementImportRepository.Result> callback =
                new RepositoryCallback<StatementImportRepository.Result>() {
                    @Override
                    public void onSuccess(StatementImportRepository.Result result) {
                        if (!isAdded()) return;
                        vm().pendingImport = null;
                        String message = result.adjustmentCents == 0
                                ? getString(R.string.import_done, result.imported)
                                : getString(R.string.import_done_adjust, result.imported, Money.plain(result.adjustmentCents));
                        if (result.linked > 0) message = getString(R.string.import_done_linked, message, result.linked);
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        dismiss();
                    }

                    @Override
                    public void onError(Exception e) {
                        fail(error, save, e.getMessage());
                    }
                };
        if (cardId > 0) {
            vm().imports.commitCard(vm().getUserId(), cardId, preview.rows, callback);
        } else {
            vm().imports.commit(vm().getUserId(), accountId, preview.rows, statement.ledgerBalanceCents,
                    statement.ledgerBalanceDate, adjust, callback);
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(View view) {
            super(view);
        }
    }

    private final class RowsAdapter extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_HEADER : TYPE_ROW;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                if (header.getParent() instanceof ViewGroup) ((ViewGroup) header.getParent()).removeView(header);
                return new Holder(header);
            }
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_import_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            if (position > 0) bindRow(holder.itemView, preview.rows.get(position - 1));
        }

        @Override
        public int getItemCount() {
            return 1 + (preview == null ? 0 : preview.rows.size());
        }
    }
}
