package com.opablosantanaa.granalivre.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.DefaultCategories;
import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Goal;
import com.opablosantanaa.granalivre.entity.GoalMovement;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.InvoiceStatus;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.entity.Transfer;
import com.opablosantanaa.granalivre.entity.User;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;

public class BackupTest extends DatabaseTest {
    private AppDatabase other;
    private long userId;
    private long accountId;
    private long savingsId;
    private long goalId;
    private long invoiceId;
    private long marketId;

    @Before
    public void createData() {
        userId = createUser("ana@email.com");
        database.categoryDao().insertAll(DefaultCategories.create(userId));
        accountId = createAccount(userId, 100_000);
        savingsId = createAccount(userId, 0);
        long categoryId = createCategory(userId, TransactionType.DESPESA);
        long cardId = createCard(userId, accountId, 5, 15);

        Invoice invoice = new Invoice();
        invoice.setCard_id(cardId);
        invoice.setInvoice_reference_month(YearMonth.of(2026, 9));
        invoice.setInvoice_due_date(LocalDate.of(2026, 9, 15));
        invoice.setInvoice_status(InvoiceStatus.PAGA);
        invoice.setInvoice_payment_account_id(accountId);
        invoice.setInvoice_paid_cents(3_000);
        invoice.setInvoice_paid_date(LocalDate.of(2026, 9, 15));
        invoiceId = database.invoiceDao().insert(invoice);

        Recurrence rent = new Recurrence();
        rent.setUser_id(userId);
        rent.setAccount_id(accountId);
        rent.setRecurrence_description("Aluguel");
        rent.setRecurrence_amount_cents(50_000);
        rent.setRecurrence_start_date(LocalDate.of(2026, 9, 10));
        rent.setRecurrence_last_generated_date(LocalDate.of(2026, 9, 10));
        long rentId = database.recurrenceDao().insert(rent);

        insert(transaction(accountId, null, 50_000, "Aluguel", true, rentId, null));
        marketId = database.financialTransactionDao().insert(
                transaction(null, cardId, 3_000, "Mercado", false, null, invoiceId));
        FinancialTransaction imported = transaction(accountId, null, 1_250, "Padaria", true, null, null);
        imported.setCategory_id(categoryId);
        imported.setTransaction_import_id("fit:42");
        insert(imported);

        Transfer transfer = new Transfer();
        transfer.setUser_id(userId);
        transfer.setTransfer_from_account_id(accountId);
        transfer.setTransfer_to_account_id(savingsId);
        transfer.setTransfer_amount_cents(10_000);
        database.transferDao().insert(transfer);

        Goal goal = new Goal();
        goal.setUser_id(userId);
        goal.setGoal_name("Viagem");
        goal.setGoal_target_cents(500_000);
        goal.setGoal_saved_cents(7_000);
        goalId = database.goalDao().insert(goal);
        GoalMovement movement = new GoalMovement();
        movement.setGoal_id(goalId);
        movement.setAccount_id(accountId);
        movement.setMovement_amount_cents(7_000);
        database.goalDao().insertMovement(movement);

        other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void closeOther() {
        other.close();
    }

    private FinancialTransaction transaction(Long account, Long card, long cents, String description, boolean paid,
                                             Long recurrence, Long invoice) {
        FinancialTransaction t = new FinancialTransaction();
        t.setUser_id(userId);
        t.setAccount_id(account);
        t.setCard_id(card);
        t.setInvoice_id(invoice);
        t.setRecurrence_id(recurrence);
        t.setTransaction_type(TransactionType.DESPESA);
        t.setTransaction_amount_cents(cents);
        t.setTransaction_date(LocalDate.of(2026, 9, 10));
        t.setTransaction_description(description);
        t.setTransaction_paid(paid);
        return t;
    }

    private void insert(FinancialTransaction t) {
        database.financialTransactionDao().insert(t);
    }

    private long newUser(AppDatabase db, boolean withDefaults) {
        User user = new User();
        user.setUser_name("Ana");
        user.setUser_email("ana@outro.local");
        user.setUser_birth_date(LocalDate.of(1995, 5, 20));
        long id = db.userDao().insert(user);
        if (withDefaults) db.categoryDao().insertAll(DefaultCategories.create(id));
        return id;
    }

    private JSONObject backup() {
        return BackupMerger.parse(new BackupExporter(database).export(userId));
    }

    private static int count(AppDatabase db, long user) {
        return db.backupDao().accounts(user).size() + db.backupDao().categories(user).size()
                + db.backupDao().cards(user).size() + db.backupDao().invoices(user).size()
                + db.backupDao().installments(user).size() + db.backupDao().recurrences(user).size()
                + db.backupDao().transactions(user).size() + db.backupDao().transfers(user).size()
                + db.backupDao().budgets(user).size() + db.backupDao().goals(user).size()
                + db.backupDao().goalMovements(user).size();
    }

    @Test
    public void backupIsReadableJson() throws Exception {
        JSONObject root = backup();
        assertEquals("granalivre-backup", root.getString("format"));
        assertEquals("Viagem", root.getJSONArray("goals").getJSONObject(0).getString("name"));
        assertEquals(100_000, root.getJSONArray("accounts").getJSONObject(0).getLong("initialBalanceCents"));
    }

    @Test
    public void mergeIntoEmptyProfileRebuildsEverything() {
        long target = newUser(other, false);
        BackupMerger.Result result = new BackupMerger(other).merge(target, backup());

        assertEquals(0, result.kept);
        assertEquals(0, result.skipped);
        assertEquals(count(database, userId), count(other, target));
        Account restored = other.backupDao().accounts(target).get(0);
        assertEquals(database.accountDao().findBalance(accountId), other.accountDao().findBalance(restored.getAccount_id()));
        assertEquals(7_000, other.backupDao().goals(target).get(0).getGoal_saved_cents());
    }

    @Test
    public void mergingSameBackupTwiceAddsNothing() {
        long target = newUser(other, false);
        JSONObject root = backup();
        new BackupMerger(other).merge(target, root);
        int before = count(other, target);

        BackupMerger.Result again = new BackupMerger(other).merge(target, root);
        assertEquals(0, again.added);
        assertEquals(before, count(other, target));
    }

    @Test
    public void defaultCategoriesOfNewProfileAreNotDuplicated() {
        long target = newUser(other, true);
        int defaults = other.backupDao().categories(target).size();
        new BackupMerger(other).merge(target, backup());
        assertEquals(defaults + 1, other.backupDao().categories(target).size());
    }

    @Test
    public void localEditsWinOverBackup() {
        long target = newUser(other, false);
        JSONObject root = backup();
        new BackupMerger(other).merge(target, root);
        Account local = other.backupDao().accounts(target).get(0);
        local.setAccount_bank_name("Renomeada");
        other.accountDao().update(local);

        new BackupMerger(other).merge(target, root);
        assertEquals("Renomeada", other.backupDao().accounts(target).get(0).getAccount_bank_name());
    }

    @Test
    public void newerBackupAddsOnlyWhatIsMissing() {
        long target = newUser(other, false);
        new BackupMerger(other).merge(target, backup());
        insert(transaction(accountId, null, 900, "Café", true, null, null));

        BackupMerger.Result result = new BackupMerger(other).merge(target, backup());
        assertEquals(1, result.added);
        assertEquals(count(database, userId), count(other, target));
    }

    @Test
    public void movementOnExistingGoalAddsToSaved() {
        long target = newUser(other, false);
        new BackupMerger(other).merge(target, backup());
        GoalMovement extra = new GoalMovement();
        extra.setGoal_id(goalId);
        extra.setAccount_id(accountId);
        extra.setMovement_amount_cents(3_000);
        database.goalDao().insertMovement(extra);

        new BackupMerger(other).merge(target, backup());
        assertEquals(10_000, other.backupDao().goals(target).get(0).getGoal_saved_cents());
    }

    @Test
    public void identityTravelsWithTheData() throws Exception {
        long target = newUser(other, false);
        JSONObject original = backup();
        new BackupMerger(other).merge(target, original);
        JSONObject again = BackupMerger.parse(new BackupExporter(other).export(target));
        assertEquals(original.getJSONArray("transactions").getJSONObject(0).getString("id"),
                again.getJSONArray("transactions").getJSONObject(0).getString("id"));
    }

    @Test
    public void foreignFileIsRejected() {
        assertThrows(ValidationException.class, () -> BackupMerger.parse("{\"hello\": 1}"));
        assertThrows(ValidationException.class, () -> BackupMerger.parse("não é json"));
        assertThrows(ValidationException.class,
                () -> BackupMerger.parse("{\"format\": \"granalivre-backup\", \"version\": 99}"));
    }


    private void setInvoiceStatus(AppDatabase db, long id, InvoiceStatus status) {
        Invoice invoice = db.invoiceDao().findById(id);
        invoice.setInvoice_status(status);
        if (status != InvoiceStatus.PAGA) {
            invoice.setInvoice_payment_account_id(null);
            invoice.setInvoice_paid_cents(0);
            invoice.setInvoice_paid_date(null);
        }
        db.invoiceDao().update(invoice);
    }

    @Test
    public void invoicePaidInBackupBecomesPaidHere() {
        setInvoiceStatus(database, invoiceId, InvoiceStatus.ABERTA);
        long target = newUser(other, false);
        new BackupMerger(other).merge(target, backup());
        Invoice local = other.backupDao().invoices(target).get(0);
        assertEquals(InvoiceStatus.ABERTA, local.getInvoice_status());

        setInvoiceStatus(database, invoiceId, InvoiceStatus.PAGA);
        Invoice paid = database.invoiceDao().findById(invoiceId);
        paid.setInvoice_payment_account_id(accountId);
        paid.setInvoice_paid_cents(3_000);
        paid.setInvoice_paid_date(LocalDate.of(2026, 9, 15));
        database.invoiceDao().update(paid);
        BackupMerger.Result result = new BackupMerger(other).merge(target, backup());

        local = other.invoiceDao().findById(local.getInvoice_id());
        assertEquals(1, result.updated);
        assertEquals(InvoiceStatus.PAGA, local.getInvoice_status());
        assertEquals(3_000, local.getInvoice_paid_cents());
        assertEquals(LocalDate.of(2026, 9, 15), local.getInvoice_paid_date());
        Account restored = other.backupDao().accounts(target).get(0);
        assertEquals(restored.getAccount_id(), (long) local.getInvoice_payment_account_id());
        assertEquals(database.accountDao().findBalance(accountId), other.accountDao().findBalance(restored.getAccount_id()));
    }

    @Test
    public void invoicePaidHereStaysPaidWithOlderBackup() {
        setInvoiceStatus(database, invoiceId, InvoiceStatus.ABERTA);
        JSONObject older = backup();
        long target = newUser(other, false);
        new BackupMerger(other).merge(target, older);
        Invoice local = other.backupDao().invoices(target).get(0);
        local.setInvoice_status(InvoiceStatus.PAGA);
        local.setInvoice_paid_cents(3_000);
        other.invoiceDao().update(local);

        BackupMerger.Result result = new BackupMerger(other).merge(target, older);
        assertEquals(0, result.updated);
        assertEquals(InvoiceStatus.PAGA, other.invoiceDao().findById(local.getInvoice_id()).getInvoice_status());
    }

    @Test
    public void pendingTransactionPaidInBackupBecomesPaid() {
        long target = newUser(other, false);
        new BackupMerger(other).merge(target, backup());
        FinancialTransaction market = database.financialTransactionDao().findById(marketId);
        market.setTransaction_paid(true);
        database.financialTransactionDao().update(market);

        BackupMerger.Result result = new BackupMerger(other).merge(target, backup());
        assertEquals(1, result.updated);
        for (FinancialTransaction t : other.backupDao().transactions(target)) {
            assertEquals(t.getTransaction_description(), true, t.isTransaction_paid());
        }
    }

    @Test
    public void protectedBackupNeedsTheRightPassword() throws Exception {
        String plain = new BackupExporter(database).export(userId);
        String sealed = BackupEnvelope.seal(plain, BackupEnvelope.Key.fromPassword("senha-forte"));
        JSONObject envelope = BackupMerger.parse(sealed);

        assertEquals(true, BackupEnvelope.isEncrypted(envelope));
        assertEquals(false, sealed.contains("Viagem"));
        assertThrows(PasswordRequiredException.class, () -> BackupEnvelope.open(envelope, "senha-errada"));

        JSONObject opened = BackupMerger.parse(BackupEnvelope.open(envelope, "senha-forte"));
        long target = newUser(other, false);
        new BackupMerger(other).merge(target, opened);
        assertEquals(count(database, userId), count(other, target));
    }

    @Test
    public void tamperedProtectedBackupIsRejected() throws Exception {
        String sealed = BackupEnvelope.seal(new BackupExporter(database).export(userId),
                BackupEnvelope.Key.fromPassword("senha-forte"));
        JSONObject envelope = BackupMerger.parse(sealed);
        String data = envelope.getString("data");
        envelope.put("data", (data.charAt(0) == 'A' ? "B" : "A") + data.substring(1));
        assertThrows(PasswordRequiredException.class, () -> BackupEnvelope.open(envelope, "senha-forte"));
    }
}
