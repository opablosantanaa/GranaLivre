package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.DefaultCategories;
import com.opablosantanaa.granalivre.domain.ofx.CategorySuggester;
import com.opablosantanaa.granalivre.domain.ofx.OfxStatement;
import com.opablosantanaa.granalivre.domain.ofx.OfxTransaction;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public class StatementImportRepository extends BaseRepository {
    public static final String ADJUSTMENT_DESCRIPTION = "Ajuste de saldo (extrato)";

    static final int MATCH_WINDOW_DAYS = 5;

    public static final class Row {
        public final OfxTransaction source;
        public final boolean duplicate;
        public Long categoryId;
        public boolean selected;
        public FinancialTransaction match;

        Row(OfxTransaction source, boolean duplicate, Long categoryId) {
            this.source = source;
            this.duplicate = duplicate;
            this.categoryId = categoryId;
            this.selected = !duplicate;
        }

        public TransactionType type() {
            return source.isIncome() ? TransactionType.RECEITA : TransactionType.DESPESA;
        }

        public boolean links() {
            return match != null && !selected && !duplicate;
        }
    }

    public static final class Preview {
        public final List<Row> rows;
        public final long balanceNowCents;
        public final Long balanceAtLedgerCents;

        Preview(List<Row> rows, long balanceNowCents, Long balanceAtLedgerCents) {
            this.rows = rows;
            this.balanceNowCents = balanceNowCents;
            this.balanceAtLedgerCents = balanceAtLedgerCents;
        }

        public long selectedNet(LocalDate until) {
            long net = 0;
            for (Row row : rows) {
                if (row.duplicate) continue;
                if (until != null && row.source.date.isAfter(until)) continue;
                boolean settlesPending = row.links() && !row.match.isTransaction_paid();
                if (!row.selected && !settlesPending) continue;
                net += row.source.amountCents;
            }
            return net;
        }

        public int matchedCount() {
            int count = 0;
            for (Row row : rows) if (row.match != null && !row.duplicate) count++;
            return count;
        }

        public int linkCount() {
            int count = 0;
            for (Row row : rows) if (row.links()) count++;
            return count;
        }
    }

    public static final class Result {
        public final int imported;
        public final int linked;
        public final int skipped;
        public final long adjustmentCents;

        Result(int imported, int linked, int skipped, long adjustmentCents) {
            this.imported = imported;
            this.linked = linked;
            this.skipped = skipped;
            this.adjustmentCents = adjustmentCents;
        }
    }

    private final TransactionRules rules;

    public StatementImportRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public StatementImportRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        rules = new TransactionRules(database);
    }

    private long balanceAt(long accountId, LocalDate date) {
        long now = database.accountDao().findBalance(accountId);
        if (date == null) return now;
        return now - database.financialTransactionDao().findNetAfter(accountId, date)
                - database.transferDao().findNetAfter(accountId, date)
                + database.invoiceDao().findPaidAfter(accountId, date)
                + database.goalDao().findReservedAfter(accountId, date);
    }

    private Map<String, Long> categoryIds(long userId, TransactionType type) {
        Map<String, Long> ids = new HashMap<>();
        for (Category c : database.categoryDao().findByUser(userId)) {
            if (c.getCategory_type() == type && c.getCategory_parent_id() == null) {
                ids.put(c.getCategory_name(), c.getCategory_id());
            }
        }
        return ids;
    }

    public void preview(long userId, long accountId, OfxStatement statement, RepositoryCallback<Preview> callback) {
        execute(() -> {
            rules.requireOwnAccount(userId, accountId);
            Set<String> existing = new HashSet<>(database.financialTransactionDao().findImportIds(accountId));
            Map<String, Long> expense = categoryIds(userId, TransactionType.DESPESA);
            Map<String, Long> income = categoryIds(userId, TransactionType.RECEITA);
            List<Row> rows = new ArrayList<>();
            for (OfxTransaction t : statement.transactions) {
                String name = CategorySuggester.suggest(t.description, t.isIncome());
                Map<String, Long> ids = t.isIncome() ? income : expense;
                Long categoryId = name == null ? null : ids.get(name);
                if (categoryId == null) categoryId = ids.get(DefaultCategories.fallbackName(
                        t.isIncome() ? TransactionType.RECEITA : TransactionType.DESPESA));
                rows.add(new Row(t, existing.contains(t.importId), categoryId));
            }
            matchExisting(accountId, rows);
            Long atLedger = statement.ledgerBalanceCents == null ? null
                    : balanceAt(accountId, statement.ledgerBalanceDate);
            return new Preview(rows, database.accountDao().findBalance(accountId), atLedger);
        }, callback);
    }

    private void matchExisting(long accountId, List<Row> rows) {
        LocalDate first = null;
        LocalDate last = null;
        for (Row row : rows) {
            if (row.duplicate) continue;
            if (first == null || row.source.date.isBefore(first)) first = row.source.date;
            if (last == null || row.source.date.isAfter(last)) last = row.source.date;
        }
        if (first == null) return;
        List<FinancialTransaction> candidates = database.financialTransactionDao().findUnlinkedBetween(
                accountId, first.minusDays(MATCH_WINDOW_DAYS), last.plusDays(MATCH_WINDOW_DAYS));
        List<long[]> pairs = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            if (row.duplicate) continue;
            for (int c = 0; c < candidates.size(); c++) {
                FinancialTransaction t = candidates.get(c);
                if (t.getTransaction_type() != row.type()
                        || t.getTransaction_amount_cents() != Math.abs(row.source.amountCents)) continue;
                long days = Math.abs(ChronoUnit.DAYS.between(t.getTransaction_date(), row.source.date));
                if (days > MATCH_WINDOW_DAYS) continue;
                pairs.add(new long[]{days * 2 + (t.getRecurrence_id() == null ? 1 : 0), r, c});
            }
        }
        pairs.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0])
                : a[1] != b[1] ? Long.compare(a[1], b[1]) : Long.compare(a[2], b[2]));
        Set<Long> used = new HashSet<>();
        for (long[] pair : pairs) {
            Row row = rows.get((int) pair[1]);
            if (row.match != null || used.contains(pair[2])) continue;
            row.match = candidates.get((int) pair[2]);
            row.selected = false;
            used.add(pair[2]);
        }
    }

    public void commit(long userId, long accountId, List<Row> rows, Long ledgerCents, LocalDate ledgerDate,
                       boolean reconcile, RepositoryCallback<Result> callback) {
        execute(() -> database.runInTransaction(() -> {
            rules.requireOwnAccount(userId, accountId);
            long balanceBeforeImport = database.accountDao().findBalance(accountId);
            Set<String> existing = new HashSet<>(database.financialTransactionDao().findImportIds(accountId));
            int imported = 0;
            int linked = 0;
            int skipped = 0;
            List<String> linkedIds = new ArrayList<>();
            for (Row row : rows) {
                if (row.links()) {
                    FinancialTransaction t = database.financialTransactionDao().findById(row.match.getTransaction_id());
                    if (existing.contains(row.source.importId) || t == null
                            || t.getAccount_id() == null || t.getAccount_id() != accountId
                            || t.getTransaction_import_id() != null) {
                        skipped++;
                        continue;
                    }
                    t.setTransaction_import_id(row.source.importId);
                    if (!t.isTransaction_paid()) {
                        t.setTransaction_paid(true);
                        t.setTransaction_date(row.source.date);
                    }
                    rules.prepare(t);
                    database.financialTransactionDao().update(t);
                    existing.add(row.source.importId);
                    linkedIds.add(row.source.importId);
                    linked++;
                    continue;
                }
                if (!row.selected) continue;
                if (existing.contains(row.source.importId)) {
                    skipped++;
                    continue;
                }
                FinancialTransaction t = new FinancialTransaction();
                t.setUser_id(userId);
                t.setAccount_id(accountId);
                t.setTransaction_type(row.type());
                t.setTransaction_amount_cents(Math.abs(row.source.amountCents));
                t.setTransaction_description(row.source.description);
                t.setTransaction_date(row.source.date);
                t.setTransaction_paid(true);
                t.setCategory_id(row.categoryId);
                t.setTransaction_import_id(row.source.importId);
                rules.prepare(t);
                database.financialTransactionDao().insert(t);
                existing.add(row.source.importId);
                imported++;
            }
            long adjustment = 0;
            if (reconcile && ledgerCents != null) {
                LocalDate date = ledgerDate == null ? LocalDate.now() : ledgerDate;
                adjustment = balanceBeforeImport - database.accountDao().findBalance(accountId);
                StringBuilder adjustmentKey = new StringBuilder("adjust:").append(date).append(':')
                        .append(ledgerCents);
                for (Row row : rows) {
                    if (row.selected && !row.duplicate) adjustmentKey.append(':').append(row.source.importId);
                }
                for (String id : linkedIds) adjustmentKey.append(":link:").append(id);
                String adjustmentId = adjustmentKey.toString();
                if (adjustment != 0 && !existing.contains(adjustmentId)) {
                    FinancialTransaction t = new FinancialTransaction();
                    t.setUser_id(userId);
                    t.setAccount_id(accountId);
                    t.setTransaction_type(adjustment > 0 ? TransactionType.RECEITA : TransactionType.DESPESA);
                    t.setTransaction_amount_cents(Math.abs(adjustment));
                    t.setTransaction_description(ADJUSTMENT_DESCRIPTION);
                    t.setTransaction_date(date);
                    t.setTransaction_paid(true);
                    t.setTransaction_import_id(adjustmentId);
                    rules.prepare(t);
                    database.financialTransactionDao().insert(t);
                    existing.add(adjustmentId);
                } else {
                    adjustment = 0;
                }
            }
            require(imported > 0 || linked > 0 || adjustment != 0 || skipped > 0, "Nenhum lançamento selecionado");
            return new Result(imported, linked, skipped, adjustment);
        }), callback);
    }
}
