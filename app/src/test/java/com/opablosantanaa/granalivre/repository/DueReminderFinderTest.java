package com.opablosantanaa.granalivre.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.opablosantanaa.granalivre.domain.DueReminder;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.InvoiceStatus;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.RecurrenceFrequency;
import com.opablosantanaa.granalivre.entity.TransactionType;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class DueReminderFinderTest extends DatabaseTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private long userId;
    private long accountId;

    @Before
    public void createData() {
        userId = createUser("ana@email.com");
        accountId = createAccount(userId, 0);
    }

    private long pending(String description, LocalDate date, TransactionType type, boolean paid) {
        FinancialTransaction t = new FinancialTransaction();
        t.setUser_id(userId);
        t.setAccount_id(accountId);
        t.setTransaction_type(type);
        t.setTransaction_amount_cents(10_000);
        t.setTransaction_description(description);
        t.setTransaction_date(date);
        t.setTransaction_paid(paid);
        return database.financialTransactionDao().insert(t);
    }

    private List<DueReminder> find() {
        return new DueReminderFinder(database).find(userId, TODAY);
    }

    @Test
    public void pendingExpensesInsideWindowAreFound() {
        pending("Ontem", TODAY.minusDays(1), TransactionType.DESPESA, false);
        pending("Daqui a dois dias", TODAY.plusDays(2), TransactionType.DESPESA, false);
        pending("Antes da janela", TODAY.minusDays(2), TransactionType.DESPESA, false);
        pending("Depois da janela", TODAY.plusDays(3), TransactionType.DESPESA, false);
        pending("Paga", TODAY, TransactionType.DESPESA, true);
        pending("Receita", TODAY, TransactionType.RECEITA, false);

        List<DueReminder> reminders = find();
        assertEquals(2, reminders.size());
        assertEquals("Ontem", reminders.get(0).title);
        assertEquals("Daqui a dois dias", reminders.get(1).title);
    }

    @Test
    public void unpaidInvoiceWithTotalIsFound() {
        long cardId = createCard(userId, accountId, 15, 25);
        Invoice invoice = new Invoice();
        invoice.setCard_id(cardId);
        invoice.setInvoice_reference_month(YearMonth.of(2026, 10));
        invoice.setInvoice_due_date(TODAY.plusDays(2));
        invoice.setInvoice_status(InvoiceStatus.FECHADA);
        long invoiceId = database.invoiceDao().insert(invoice);

        FinancialTransaction purchase = new FinancialTransaction();
        purchase.setUser_id(userId);
        purchase.setCard_id(cardId);
        purchase.setInvoice_id(invoiceId);
        purchase.setTransaction_amount_cents(25_000);
        purchase.setTransaction_date(TODAY.minusDays(10));
        purchase.setTransaction_paid(false);
        database.financialTransactionDao().insert(purchase);

        List<DueReminder> reminders = find();
        assertEquals(1, reminders.size());
        assertEquals(DueReminder.Kind.INVOICE, reminders.get(0).kind);
        assertEquals(25_000, reminders.get(0).amountCents);

        invoice.setInvoice_id(invoiceId);
        invoice.setInvoice_status(InvoiceStatus.PAGA);
        database.invoiceDao().update(invoice);
        assertTrue(find().isEmpty());
    }

    @Test
    public void futureRecurrenceOccurrenceIsFoundBeforeBeingGenerated() {
        Recurrence recurrence = new Recurrence();
        recurrence.setUser_id(userId);
        recurrence.setAccount_id(accountId);
        recurrence.setRecurrence_description("Aluguel");
        recurrence.setRecurrence_amount_cents(150_000);
        recurrence.setRecurrence_frequency(RecurrenceFrequency.MENSAL);
        recurrence.setRecurrence_start_date(LocalDate.of(2026, 8, 25));
        recurrence.setRecurrence_last_generated_date(LocalDate.of(2026, 9, 25));
        database.recurrenceDao().insert(recurrence);

        List<DueReminder> reminders = find();
        assertEquals(1, reminders.size());
        assertEquals(DueReminder.Kind.RECURRENCE, reminders.get(0).kind);
        assertEquals(LocalDate.of(2026, 10, 25), reminders.get(0).dueDate);
    }
}
