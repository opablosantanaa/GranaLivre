package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.BackupExporter.ACCOUNTS;
import static com.opablosantanaa.granalivre.repository.BackupExporter.BUDGETS;
import static com.opablosantanaa.granalivre.repository.BackupExporter.CARDS;
import static com.opablosantanaa.granalivre.repository.BackupExporter.CATEGORIES;
import static com.opablosantanaa.granalivre.repository.BackupExporter.GOALS;
import static com.opablosantanaa.granalivre.repository.BackupExporter.GOAL_MOVEMENTS;
import static com.opablosantanaa.granalivre.repository.BackupExporter.INSTALLMENTS;
import static com.opablosantanaa.granalivre.repository.BackupExporter.INVOICES;
import static com.opablosantanaa.granalivre.repository.BackupExporter.RECURRENCES;
import static com.opablosantanaa.granalivre.repository.BackupExporter.TRANSACTIONS;
import static com.opablosantanaa.granalivre.repository.BackupExporter.TRANSFERS;
import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.opablosantanaa.granalivre.dao.BackupDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.entity.AccountType;
import com.opablosantanaa.granalivre.entity.BackupId;
import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.CardBrand;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Goal;
import com.opablosantanaa.granalivre.entity.GoalMovement;
import com.opablosantanaa.granalivre.entity.Installment;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.InvoiceStatus;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.RecurrenceFrequency;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.entity.Transfer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BackupMerger {
    public static final class Result {
        public int added;
        public int updated;
        public int kept;
        public int skipped;
    }

    public static final class Preview {
        public final LocalDateTime createdAt;
        public final String profileName;
        public final int transactions;
        public final int records;

        Preview(LocalDateTime createdAt, String profileName, int transactions, int records) {
            this.createdAt = createdAt;
            this.profileName = profileName;
            this.transactions = transactions;
            this.records = records;
        }
    }

    private static final String[] TABLES = {ACCOUNTS, CATEGORIES, CARDS, INVOICES, INSTALLMENTS, RECURRENCES,
            TRANSACTIONS, TRANSFERS, BUDGETS, GOALS, GOAL_MOVEMENTS};
    private static final Map<String, String> ID_COLUMNS = new HashMap<>();
    private static final Set<String> OWNED = new HashSet<>();

    static {
        ID_COLUMNS.put(ACCOUNTS, "account_id");
        ID_COLUMNS.put(CATEGORIES, "category_id");
        ID_COLUMNS.put(CARDS, "card_id");
        ID_COLUMNS.put(INVOICES, "invoice_id");
        ID_COLUMNS.put(INSTALLMENTS, "installment_id");
        ID_COLUMNS.put(RECURRENCES, "recurrence_id");
        ID_COLUMNS.put(TRANSACTIONS, "transaction_id");
        ID_COLUMNS.put(TRANSFERS, "transfer_id");
        ID_COLUMNS.put(BUDGETS, "budget_id");
        ID_COLUMNS.put(GOALS, "goal_id");
        ID_COLUMNS.put(GOAL_MOVEMENTS, "movement_id");
        for (String table : TABLES) {
            if (!table.equals(INVOICES) && !table.equals(GOAL_MOVEMENTS)) OWNED.add(table);
        }
    }

    private interface Lookup {
        List<Long> find() throws JSONException;
    }

    private interface Insert {
        long insert() throws JSONException;
    }

    private interface Advance {
        boolean apply(long id) throws JSONException;
    }

    private final AppDatabase database;
    private final BackupDao dao;
    private final Map<String, Map<String, Long>> ids = new HashMap<>();
    private final Map<String, Set<Long>> claimed = new HashMap<>();
    private Result result;
    private long userId;

    public BackupMerger(AppDatabase database) {
        this.database = database;
        dao = database.backupDao();
    }

    public static JSONObject parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            require(BackupExporter.FORMAT.equals(root.optString("format")), "Este arquivo não é um backup do Grana Livre");
            require(root.optInt("version", 0) <= BackupExporter.VERSION,
                    "Backup feito por uma versão mais nova do app. Atualize o Grana Livre para importar.");
            return root;
        } catch (JSONException e) {
            throw new ValidationException("Este arquivo não é um backup do Grana Livre");
        }
    }

    public static Preview preview(JSONObject root) {
        LocalDateTime createdAt = null;
        try {
            createdAt = LocalDateTime.parse(root.optString("createdAt"));
        } catch (DateTimeParseException ignored) {
        }
        JSONObject profile = root.optJSONObject("profile");
        int records = 0;
        for (String table : TABLES) {
            JSONArray rows = root.optJSONArray(table);
            if (rows != null) records += rows.length();
        }
        JSONArray transactions = root.optJSONArray(TRANSACTIONS);
        return new Preview(createdAt, profile == null ? "" : profile.optString("name"),
                transactions == null ? 0 : transactions.length(), records);
    }

    public Result merge(long userId, JSONObject root) {
        return database.runInTransaction(() -> {
            try {
                return mergeAll(userId, root);
            } catch (JSONException | IllegalArgumentException | DateTimeParseException e) {
                throw new ValidationException("O backup está corrompido e não foi importado");
            }
        });
    }

    private Result mergeAll(long userId, JSONObject root) throws JSONException {
        this.userId = userId;
        result = new Result();
        ids.clear();
        claimed.clear();
        Set<Long> newGoals = new HashSet<>();

        for (JSONObject a : rows(root, ACCOUNTS)) {
            AccountType type = AccountType.valueOf(a.getString("type"));
            resolve(ACCOUNTS, a, () -> dao.matchAccount(userId, a.getString("bankName"), type), () -> {
                Account account = new Account();
                account.setUser_id(userId);
                account.setAccount_bank_name(a.getString("bankName"));
                account.setAccount_type(type);
                account.setAccount_initial_balance_cents(a.getLong("initialBalanceCents"));
                return database.accountDao().insert(account);
            });
        }

        for (JSONObject c : rows(root, CATEGORIES)) {
            Long parent = ref(CATEGORIES, c, "parent");
            if (!c.isNull("parent") && parent == null) { result.skipped++; continue; }
            TransactionType type = TransactionType.valueOf(c.getString("type"));
            resolve(CATEGORIES, c, () -> dao.matchCategory(userId, c.getString("name"), type, parent), () -> {
                Category category = new Category();
                category.setUser_id(userId);
                category.setCategory_parent_id(parent);
                category.setCategory_name(c.getString("name"));
                category.setCategory_type(type);
                category.setCategory_icon(text(c, "icon"));
                category.setCategory_color(c.getInt("color"));
                return database.categoryDao().insert(category);
            });
        }

        for (JSONObject c : rows(root, CARDS)) {
            resolve(CARDS, c, () -> dao.matchCard(userId, c.getString("nickname"), c.getString("lastDigits")), () -> {
                Card card = new Card();
                card.setUser_id(userId);
                card.setCard_payment_account_id(ref(ACCOUNTS, c, "paymentAccount"));
                card.setCard_nickname(c.getString("nickname"));
                card.setCard_brand(CardBrand.valueOf(c.getString("brand")));
                card.setCard_last_digits(c.getString("lastDigits"));
                card.setCard_limit_cents(c.getLong("limitCents"));
                card.setCard_closing_day(c.getInt("closingDay"));
                card.setCard_due_day(c.getInt("dueDay"));
                return database.cardDao().insert(card);
            });
        }

        for (JSONObject i : rows(root, INVOICES)) {
            Long card = ref(CARDS, i, "card");
            if (card == null) { result.skipped++; continue; }
            YearMonth month = YearMonth.parse(i.getString("referenceMonth"));
            resolve(INVOICES, i, () -> dao.matchInvoice(card, month), () -> {
                Invoice invoice = new Invoice();
                invoice.setCard_id(card);
                invoice.setInvoice_reference_month(month);
                invoice.setInvoice_closing_date(LocalDate.parse(i.getString("closingDate")));
                invoice.setInvoice_due_date(LocalDate.parse(i.getString("dueDate")));
                invoice.setInvoice_status(InvoiceStatus.valueOf(i.getString("status")));
                invoice.setInvoice_payment_account_id(ref(ACCOUNTS, i, "paymentAccount"));
                invoice.setInvoice_paid_cents(i.getLong("paidCents"));
                invoice.setInvoice_paid_date(date(i, "paidDate"));
                return database.invoiceDao().insert(invoice);
            }, id -> advanceInvoice(id, i));
        }

        for (JSONObject i : rows(root, INSTALLMENTS)) {
            resolve(INSTALLMENTS, i, null, () -> {
                Installment installment = new Installment();
                installment.setUser_id(userId);
                installment.setInstallment_description(i.getString("description"));
                installment.setInstallment_total_cents(i.getLong("totalCents"));
                installment.setInstallment_count(i.getInt("count"));
                installment.setInstallment_first_date(LocalDate.parse(i.getString("firstDate")));
                return database.installmentDao().insert(installment);
            });
        }

        for (JSONObject r : rows(root, RECURRENCES)) {
            Long account = ref(ACCOUNTS, r, "account");
            Long card = ref(CARDS, r, "card");
            if (account == null && card == null) { result.skipped++; continue; }
            resolve(RECURRENCES, r, null, () -> {
                Recurrence recurrence = new Recurrence();
                recurrence.setUser_id(userId);
                recurrence.setAccount_id(account);
                recurrence.setCard_id(card);
                recurrence.setCategory_id(ref(CATEGORIES, r, "category"));
                recurrence.setRecurrence_type(TransactionType.valueOf(r.getString("type")));
                recurrence.setRecurrence_amount_cents(r.getLong("amountCents"));
                recurrence.setRecurrence_description(r.getString("description"));
                recurrence.setRecurrence_frequency(RecurrenceFrequency.valueOf(r.getString("frequency")));
                recurrence.setRecurrence_start_date(LocalDate.parse(r.getString("startDate")));
                recurrence.setRecurrence_end_date(date(r, "endDate"));
                recurrence.setRecurrence_active(r.getBoolean("active"));
                recurrence.setRecurrence_last_generated_date(date(r, "lastGeneratedDate"));
                return database.recurrenceDao().insert(recurrence);
            });
        }

        for (JSONObject t : rows(root, TRANSACTIONS)) {
            Long account = ref(ACCOUNTS, t, "account");
            Long card = ref(CARDS, t, "card");
            if (account == null && card == null) { result.skipped++; continue; }
            String importId = text(t, "importId");
            Lookup imported = importId == null || account == null ? null
                    : () -> dao.matchImportedTransaction(account, importId);
            resolve(TRANSACTIONS, t, imported, () -> {
                FinancialTransaction transaction = new FinancialTransaction();
                transaction.setUser_id(userId);
                transaction.setAccount_id(account);
                transaction.setCard_id(card);
                transaction.setInvoice_id(ref(INVOICES, t, "invoice"));
                transaction.setCategory_id(ref(CATEGORIES, t, "category"));
                transaction.setInstallment_id(ref(INSTALLMENTS, t, "installment"));
                transaction.setTransaction_installment_number(t.isNull("installmentNumber")
                        ? null : Integer.valueOf(t.getString("installmentNumber")));
                transaction.setRecurrence_id(ref(RECURRENCES, t, "recurrence"));
                transaction.setTransaction_type(TransactionType.valueOf(t.getString("type")));
                transaction.setTransaction_amount_cents(t.getLong("amountCents"));
                transaction.setTransaction_date(LocalDate.parse(t.getString("date")));
                transaction.setTransaction_description(t.getString("description"));
                transaction.setTransaction_paid(t.getBoolean("paid"));
                transaction.setTransaction_import_id(importId);
                return database.financialTransactionDao().insert(transaction);
            }, id -> t.getBoolean("paid") && dao.markPaid(id) > 0);
        }

        for (JSONObject t : rows(root, TRANSFERS)) {
            Long from = ref(ACCOUNTS, t, "fromAccount");
            Long to = ref(ACCOUNTS, t, "toAccount");
            if (from == null || to == null) { result.skipped++; continue; }
            resolve(TRANSFERS, t, null, () -> {
                Transfer transfer = new Transfer();
                transfer.setUser_id(userId);
                transfer.setTransfer_from_account_id(from);
                transfer.setTransfer_to_account_id(to);
                transfer.setTransfer_amount_cents(t.getLong("amountCents"));
                transfer.setTransfer_date(LocalDate.parse(t.getString("date")));
                transfer.setTransfer_description(t.getString("description"));
                return database.transferDao().insert(transfer);
            });
        }

        for (JSONObject b : rows(root, BUDGETS)) {
            Long category = ref(CATEGORIES, b, "category");
            if (category == null) { result.skipped++; continue; }
            YearMonth month = YearMonth.parse(b.getString("month"));
            resolve(BUDGETS, b, () -> dao.matchBudget(category, month), () -> {
                Budget budget = new Budget();
                budget.setUser_id(userId);
                budget.setCategory_id(category);
                budget.setBudget_month(month);
                budget.setBudget_limit_cents(b.getLong("limitCents"));
                return database.budgetDao().upsert(budget);
            });
        }

        for (JSONObject g : rows(root, GOALS)) {
            resolve(GOALS, g, () -> dao.matchGoal(userId, g.getString("name")), () -> {
                Goal goal = new Goal();
                goal.setUser_id(userId);
                goal.setGoal_name(g.getString("name"));
                goal.setGoal_target_cents(g.getLong("targetCents"));
                goal.setGoal_saved_cents(g.getLong("savedCents"));
                goal.setGoal_deadline(date(g, "deadline"));
                long id = database.goalDao().insert(goal);
                newGoals.add(id);
                return id;
            });
        }

        for (JSONObject m : rows(root, GOAL_MOVEMENTS)) {
            Long goal = ref(GOALS, m, "goal");
            Long account = ref(ACCOUNTS, m, "account");
            if (goal == null || account == null) { result.skipped++; continue; }
            long amount = m.getLong("amountCents");
            resolve(GOAL_MOVEMENTS, m, null, () -> {
                GoalMovement movement = new GoalMovement();
                movement.setGoal_id(goal);
                movement.setAccount_id(account);
                movement.setMovement_amount_cents(amount);
                movement.setMovement_date(LocalDate.parse(m.getString("date")));
                if (!newGoals.contains(goal)) dao.addToGoal(goal, amount);
                return database.goalDao().insertMovement(movement);
            });
        }
        return result;
    }

    private void resolve(String table, JSONObject row, @Nullable Lookup natural, Insert insert) throws JSONException {
        resolve(table, row, natural, insert, null);
    }

    private void resolve(String table, JSONObject row, @Nullable Lookup natural, Insert insert,
                         @Nullable Advance advance) throws JSONException {
        String uuid = row.getString("id");
        Long id = existing(table, uuid);
        if (id == null && natural != null) {
            for (Long candidate : natural.find()) {
                if (!claimed(table).contains(candidate)) {
                    id = candidate;
                    break;
                }
            }
        }
        if (id != null) {
            if (advance != null && advance.apply(id)) result.updated++;
            else result.kept++;
        } else {
            id = insert.insert();
            result.added++;
        }
        dao.link(new BackupId(table, id, uuid));
        map(table).put(uuid, id);
        claimed(table).add(id);
    }

    private boolean advanceInvoice(long id, JSONObject row) throws JSONException {
        Invoice local = database.invoiceDao().findById(id);
        InvoiceStatus incoming = InvoiceStatus.valueOf(row.getString("status"));
        if (local == null || incoming.ordinal() <= local.getInvoice_status().ordinal()) return false;
        local.setInvoice_status(incoming);
        if (incoming == InvoiceStatus.PAGA) {
            Long account = ref(ACCOUNTS, row, "paymentAccount");
            if (account != null) local.setInvoice_payment_account_id(account);
            local.setInvoice_paid_cents(row.getLong("paidCents"));
            local.setInvoice_paid_date(date(row, "paidDate"));
        }
        database.invoiceDao().update(local);
        return true;
    }

    @Nullable
    private Long existing(String table, String uuid) {
        Long local = dao.findLocalId(table, uuid);
        if (local == null) return null;
        String sql = "SELECT 1 FROM " + table + " WHERE " + ID_COLUMNS.get(table) + " = ?"
                + (OWNED.contains(table) ? " AND user_id = ?" : "");
        Object[] args = OWNED.contains(table) ? new Object[]{local, userId} : new Object[]{local};
        try (Cursor cursor = database.query(new SimpleSQLiteQuery(sql, args))) {
            if (cursor.moveToFirst()) return local;
        }
        dao.unlink(table, uuid);
        return null;
    }

    private Set<Long> claimed(String table) {
        Set<Long> set = claimed.get(table);
        if (set == null) {
            set = new HashSet<>();
            claimed.put(table, set);
        }
        return set;
    }

    private Map<String, Long> map(String table) {
        Map<String, Long> map = ids.get(table);
        if (map == null) {
            map = new HashMap<>();
            ids.put(table, map);
        }
        return map;
    }

    @Nullable
    private Long ref(String table, JSONObject row, String key) throws JSONException {
        if (row.isNull(key)) return null;
        return map(table).get(row.getString(key));
    }

    private static Iterable<JSONObject> rows(JSONObject root, String table) throws JSONException {
        JSONArray array = root.optJSONArray(table);
        List<JSONObject> rows = new ArrayList<>();
        if (array == null) return rows;
        for (int i = 0; i < array.length(); i++) rows.add(array.getJSONObject(i));
        return rows;
    }

    @Nullable
    private static String text(JSONObject row, String key) throws JSONException {
        return row.isNull(key) ? null : row.getString(key);
    }

    @Nullable
    private static LocalDate date(JSONObject row, String key) throws JSONException {
        return row.isNull(key) ? null : LocalDate.parse(row.getString(key));
    }
}
