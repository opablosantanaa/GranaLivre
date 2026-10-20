package com.opablosantanaa.granalivre.ui.screen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.opablosantanaa.granalivre.App;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.entity.Transfer;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.CategoryFilters;
import com.opablosantanaa.granalivre.ui.MainViewModel;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.Rows;
import com.opablosantanaa.granalivre.ui.sheet.TransactionSheet;
import com.opablosantanaa.granalivre.ui.widget.SegmentedControl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MovesScreen extends Screen {
    private static final int ALL = 0;
    private static final int IN = 1;
    private static final int OUT = 2;
    private static final int TRANSFERS = 3;

    private int filter = ALL;
    private long categoryFilter;
    private MovesAdapter adapter;

    public MovesScreen() {
        super(R.layout.screen_moves);
    }

    @Override
    public int tab() {
        return App.TAB_MOVES;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        padTop(view.findViewById(R.id.header), 16);
        SegmentedControl segmented = view.findViewById(R.id.filter);
        segmented.setOptions(getString(R.string.moves_all), getString(R.string.moves_in),
                getString(R.string.moves_out), getString(R.string.moves_transfers));
        segmented.setPalette(color(R.color.gl_line), color(R.color.gl_text_secondary),
                color(R.color.gl_text), color(R.color.gl_bg));
        segmented.setOnChange(index -> {
            filter = index;
            renderLater(view);
        });

        MainViewModel vm = vm();
        RecyclerView list = view.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setHasFixedSize(true);
        adapter = new MovesAdapter();
        list.setAdapter(adapter);
        padBottomForTabs(list, 8);
        view.findViewById(R.id.prev).setOnClickListener(v -> vm.shiftMonth(-1));
        view.findViewById(R.id.next).setOnClickListener(v -> vm.shiftMonth(1));
        vm.getSelectedMonth().observe(getViewLifecycleOwner(), month ->
                ((TextView) view.findViewById(R.id.month)).setText(Dates.monthYear(month)));
        vm.monthTransactions.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.monthTransfers.observe(getViewLifecycleOwner(), v -> renderLater(view));
        ChipGroup categoryChips = view.findViewById(R.id.category_filter);
        vm.categoryList.observe(getViewLifecycleOwner(), categories -> {
            CategoryFilters.populate(requireContext(), categoryChips, categories, categoryFilter, id -> {
                categoryFilter = id;
                renderLater(view);
            });
            renderLater(view);
        });
        vm.accountList.observe(getViewLifecycleOwner(), v -> renderLater(view));
        vm.cardList.observe(getViewLifecycleOwner(), v -> renderLater(view));
    }

    private static final class Entry {
        final LocalDate date;
        final FinancialTransaction transaction;
        final Transfer transfer;

        Entry(FinancialTransaction t) {
            date = t.getTransaction_date();
            transaction = t;
            transfer = null;
        }

        Entry(Transfer t) {
            date = t.getTransfer_date();
            transaction = null;
            transfer = t;
        }
    }

    private void renderLater(View view) {
        later("render", () -> render(view));
    }

    private void render(View view) {
        MainViewModel vm = vm();
        List<FinancialTransaction> transactions = vm.monthTransactions.getValue();
        List<Transfer> transfers = vm.monthTransfers.getValue();
        if (transactions == null) transactions = new ArrayList<>();
        if (transfers == null) transfers = new ArrayList<>();

        long in = 0;
        long out = 0;
        for (FinancialTransaction t : transactions) {
            if (t.getTransaction_type() == TransactionType.RECEITA) in += t.getTransaction_amount_cents();
            else out += t.getTransaction_amount_cents();
        }
        int muted = color(R.color.gl_on_silk_secondary);
        ((TextView) view.findViewById(R.id.total_in)).setText(Money.styled(in, muted));
        ((TextView) view.findViewById(R.id.total_out)).setText(Money.styled(-out, muted));
        ((TextView) view.findViewById(R.id.total_result)).setText(Money.styled(in - out, muted, true));

        List<Entry> entries = new ArrayList<>();
        if (filter != TRANSFERS) {
            for (FinancialTransaction t : transactions) {
                boolean income = t.getTransaction_type() == TransactionType.RECEITA;
                if (filter == IN && !income) continue;
                if (filter == OUT && income) continue;
                if (categoryFilter != 0 && (income || !matchesCategory(t.getCategory_id(), categoryFilter))) continue;
                entries.add(new Entry(t));
            }
        }
        if (categoryFilter == 0 && (filter == ALL || filter == TRANSFERS)) {
            for (Transfer t : transfers) entries.add(new Entry(t));
        }
        entries.sort((a, b) -> b.date.compareTo(a.date));
        Rows.Lookup lookup = Rows.lookup(vm.categoryList.getValue(), vm.accountList.getValue(), vm.cardList.getValue());
        List<RowItem> items = new ArrayList<>(entries.size() + 20);
        int index = 0;
        while (index < entries.size()) {
            LocalDate date = entries.get(index).date;
            int end = index;
            long dayNet = 0;
            while (end < entries.size() && entries.get(end).date.equals(date)) {
                FinancialTransaction transaction = entries.get(end).transaction;
                if (transaction != null) {
                    dayNet += transaction.getTransaction_type() == TransactionType.RECEITA
                            ? transaction.getTransaction_amount_cents() : -transaction.getTransaction_amount_cents();
                }
                end++;
            }
            items.add(RowItem.header(date, dayNet));
            for (int i = index; i < end; i++) items.add(RowItem.entry(entries.get(i)));
            index = end;
        }
        if (entries.isEmpty()) items.add(RowItem.empty());
        adapter.submit(items, lookup);
    }

    private static boolean matchesCategory(Long transactionCategory, long selectedCategory) {
        return selectedCategory == -1L ? transactionCategory == null
                : transactionCategory != null && transactionCategory == selectedCategory;
    }

    private static final class RowItem {
        static final int HEADER = 0;
        static final int TRANSACTION = 1;
        static final int TRANSFER = 2;
        static final int EMPTY = 3;
        final int type;
        final LocalDate date;
        final long dayNet;
        final FinancialTransaction transaction;
        final Transfer transfer;

        private RowItem(int type, LocalDate date, long dayNet, FinancialTransaction transaction, Transfer transfer) {
            this.type = type;
            this.date = date;
            this.dayNet = dayNet;
            this.transaction = transaction;
            this.transfer = transfer;
        }

        static RowItem header(LocalDate date, long dayNet) {
            return new RowItem(HEADER, date, dayNet, null, null);
        }

        static RowItem entry(Entry entry) {
            return new RowItem(entry.transaction == null ? TRANSFER : TRANSACTION,
                    entry.date, 0, entry.transaction, entry.transfer);
        }

        static RowItem empty() {
            return new RowItem(EMPTY, null, 0, null, null);
        }
    }

    private final class MovesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<RowItem> items = new ArrayList<>();
        private Rows.Lookup lookup = new Rows.Lookup();

        void submit(List<RowItem> next, Rows.Lookup rowsLookup) {
            items.clear();
            items.addAll(next);
            lookup = rowsLookup;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == RowItem.HEADER ? R.layout.item_day_header
                    : viewType == RowItem.EMPTY ? R.layout.item_moves_empty : R.layout.item_transaction;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(layout, parent, false)) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RowItem item = items.get(position);
            if (item.type == RowItem.HEADER) {
                ((TextView) holder.itemView.findViewById(R.id.day)).setText(Dates.relativeDay(item.date));
                TextView total = holder.itemView.findViewById(R.id.total);
                total.setText(item.dayNet == 0 ? "" : Money.styled(item.dayNet,
                        color(R.color.gl_text_secondary), true));
            } else if (item.type == RowItem.TRANSACTION) {
                Rows.bindTransaction(holder.itemView, item.transaction, lookup);
                holder.itemView.setOnClickListener(v -> TransactionSheet.edit(item.transaction)
                        .show(getChildFragmentManager(), "transaction"));
            } else if (item.type == RowItem.TRANSFER) {
                Rows.bindTransfer(holder.itemView, item.transfer, lookup);
                holder.itemView.setOnClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
