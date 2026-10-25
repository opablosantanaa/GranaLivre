package com.opablosantanaa.granalivre.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.DefaultCategories;
import com.opablosantanaa.granalivre.domain.DueReminder;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.InvoiceStatus;
import com.opablosantanaa.granalivre.entity.TransactionType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class BackupRoundTripTest extends DatabaseTest {
    private static final LocalDate TODAY = LocalDate.now();

    private AppDatabase other;
    private BackupStorage storage;
    private File file;
    private long userId;
    private long accountId;

    @Before
    public void createData() throws Exception {
        storage = new BackupStorage(ApplicationProvider.getApplicationContext());
        storage.clearPassword();
        file = File.createTempFile("granalivre-backup-", ".json");

        userId = createUser("ana@email.com");
        database.categoryDao().insertAll(DefaultCategories.create(userId));
        accountId = createAccount(userId, 100_000);
        long cardId = createCard(userId, accountId, 5, 15);

        Invoice paid = invoice(cardId, YearMonth.from(TODAY.minusMonths(1)), TODAY.minusMonths(1));
        paid.setInvoice_status(InvoiceStatus.PAGA);
        paid.setInvoice_payment_account_id(accountId);
        paid.setInvoice_paid_cents(3_000);
        paid.setInvoice_paid_date(TODAY.minusMonths(1));
        purchase(cardId, database.invoiceDao().insert(paid), 3_000, "Mercado");

        Invoice open = invoice(cardId, YearMonth.from(TODAY), TODAY.plusDays(1));
        open.setInvoice_status(InvoiceStatus.ABERTA);
        purchase(cardId, database.invoiceDao().insert(open), 8_000, "Farmácia");

        other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void cleanUp() {
        other.close();
        storage.clearPassword();
        file.delete();
    }

    private Invoice invoice(long cardId, YearMonth month, LocalDate due) {
        Invoice invoice = new Invoice();
        invoice.setCard_id(cardId);
        invoice.setInvoice_reference_month(month);
        invoice.setInvoice_due_date(due);
        return invoice;
    }

    private void purchase(long cardId, long invoiceId, long cents, String description) {
        FinancialTransaction t = new FinancialTransaction();
        t.setUser_id(userId);
        t.setCard_id(cardId);
        t.setInvoice_id(invoiceId);
        t.setTransaction_type(TransactionType.DESPESA);
        t.setTransaction_amount_cents(cents);
        t.setTransaction_date(TODAY.minusDays(10));
        t.setTransaction_description(description);
        t.setTransaction_paid(false);
        database.financialTransactionDao().insert(t);
    }

    private Invoice find(List<Invoice> invoices, InvoiceStatus status) {
        for (Invoice invoice : invoices) {
            if (invoice.getInvoice_status() == status) return invoice;
        }
        throw new AssertionError("Fatura " + status + " não encontrada");
    }

    @Test
    public void protectedBackupRestoresPaidAndOpenInvoicesWithReminders() throws Exception {
        BackupRepository source = new BackupRepository(database, storage, DIRECT, DIRECT);
        DatabaseTest.<Void>success(callback -> source.setPassword("senha-forte", callback));
        assertTrue(storage.hasPassword());
        Uri document = Uri.fromFile(file);
        DatabaseTest.<Void>success(callback -> source.exportTo(userId, document, callback));

        String written = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(written.contains("\"encrypted\": true"));
        assertFalse(written.contains("Farmácia"));

        storage.clearPassword();
        BackupRepository target = new BackupRepository(other, storage, DIRECT, DIRECT);
        assertTrue(DatabaseTest.<Long>failure(callback -> target.restoreProfile(document, null, callback))
                instanceof PasswordRequiredException);
        assertTrue(DatabaseTest.<Long>failure(callback -> target.restoreProfile(document, "senha-errada", callback))
                instanceof PasswordRequiredException);
        assertNull(other.userDao().findById(1));

        long restored = success(callback -> target.restoreProfile(document, "senha-forte", callback));
        assertTrue(storage.hasPassword());

        List<Invoice> invoices = other.backupDao().invoices(restored);
        assertEquals(2, invoices.size());
        long restoredAccount = other.backupDao().accounts(restored).get(0).getAccount_id();

        Invoice paid = find(invoices, InvoiceStatus.PAGA);
        assertEquals(YearMonth.from(TODAY.minusMonths(1)), paid.getInvoice_reference_month());
        assertEquals(TODAY.minusMonths(1), paid.getInvoice_due_date());
        assertEquals(3_000, paid.getInvoice_paid_cents());
        assertEquals(TODAY.minusMonths(1), paid.getInvoice_paid_date());
        assertEquals(restoredAccount, (long) paid.getInvoice_payment_account_id());
        assertEquals(3_000, other.invoiceDao().findTotal(paid.getInvoice_id()));

        Invoice open = find(invoices, InvoiceStatus.ABERTA);
        assertEquals(YearMonth.from(TODAY), open.getInvoice_reference_month());
        assertEquals(TODAY.plusDays(1), open.getInvoice_due_date());
        assertEquals(0, open.getInvoice_paid_cents());
        assertNull(open.getInvoice_paid_date());
        assertNull(open.getInvoice_payment_account_id());
        assertEquals(8_000, other.invoiceDao().findTotal(open.getInvoice_id()));

        assertEquals(database.accountDao().findBalance(accountId), other.accountDao().findBalance(restoredAccount));

        List<DueReminder> reminders = new DueReminderFinder(other).find(restored, TODAY);
        assertEquals(1, reminders.size());
        DueReminder reminder = reminders.get(0);
        assertEquals(DueReminder.Kind.INVOICE, reminder.kind);
        assertEquals(open.getInvoice_id(), reminder.id);
        assertEquals(8_000, reminder.amountCents);
        assertEquals(TODAY.plusDays(1), reminder.dueDate);

        String next = target.content(restored);
        assertNotNull(next);
        assertTrue(next.contains("\"encrypted\": true"));
        assertTrue(BackupMerger.parse(BackupEnvelope.open(BackupMerger.parse(next), "senha-forte"))
                .getJSONArray("invoices").length() == 2);
    }
}
