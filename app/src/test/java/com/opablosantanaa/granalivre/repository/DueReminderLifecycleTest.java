package com.opablosantanaa.granalivre.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.opablosantanaa.granalivre.domain.DueReminder;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.RecurrenceFrequency;
import com.opablosantanaa.granalivre.entity.TransactionType;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DueReminderLifecycleTest extends DatabaseTest {
    private static final LocalDate DUE = LocalDate.of(2026, 11, 10);

    private long userId;
    private long accountId;
    private long recurrenceId;
    private RecurrenceRepository recurrences;

    @Before
    public void createData() {
        userId = createUser("ciclo@email.com");
        accountId = createAccount(userId, 500_000);
        recurrences = new RecurrenceRepository(database, DIRECT, DIRECT);
        Recurrence r = new Recurrence();
        r.setUser_id(userId);
        r.setAccount_id(accountId);
        r.setRecurrence_type(TransactionType.DESPESA);
        r.setRecurrence_amount_cents(150_000);
        r.setRecurrence_description("Aluguel");
        r.setRecurrence_frequency(RecurrenceFrequency.MENSAL);
        r.setRecurrence_start_date(DUE);
        recurrenceId = database.recurrenceDao().insert(r);
    }

    private List<DueReminder> workerOn(LocalDate today) {
        new RecurrenceGenerator(database).generateUntil(today);
        List<DueReminder> mine = new ArrayList<>();
        for (DueReminder reminder : new DueReminderFinder(database).find(userId, today)) {
            if (reminder.title.equals("Aluguel")) mine.add(reminder);
        }
        return mine;
    }

    @Test
    public void remindsTwoDaysBeforeOneDayBeforeOnTheDayAndOneDayAfter() {
        assertTrue("D-3 ainda não avisa", workerOn(DUE.minusDays(3)).isEmpty());

        List<DueReminder> twoBefore = workerOn(DUE.minusDays(2));
        assertEquals(1, twoBefore.size());
        assertEquals(2, twoBefore.get(0).daysUntilDue(DUE.minusDays(2)));
        assertEquals(DueReminder.Kind.RECURRENCE, twoBefore.get(0).kind);

        List<DueReminder> oneBefore = workerOn(DUE.minusDays(1));
        assertEquals(1, oneBefore.size());
        assertEquals(1, oneBefore.get(0).daysUntilDue(DUE.minusDays(1)));

        List<DueReminder> onDay = workerOn(DUE);
        assertEquals(1, onDay.size());
        assertEquals(0, onDay.get(0).daysUntilDue(DUE));
        assertEquals(DueReminder.Kind.TRANSACTION, onDay.get(0).kind);

        List<DueReminder> oneAfter = workerOn(DUE.plusDays(1));
        assertEquals(1, oneAfter.size());
        assertEquals(-1, oneAfter.get(0).daysUntilDue(DUE.plusDays(1)));
        assertEquals(150_000, oneAfter.get(0).amountCents);

        assertTrue("D+2 não avisa mais", workerOn(DUE.plusDays(2)).isEmpty());
    }

    @Test
    public void payingOnTheDueDateStopsTheNextDayReminder() {
        workerOn(DUE);
        Recurrence r = database.recurrenceDao().findById(recurrenceId);
        DatabaseTest.<Void>success(cb -> recurrences.settle(r, DUE, cb));
        assertTrue(workerOn(DUE.plusDays(1)).isEmpty());
    }

    @Test
    public void payingInAdvanceStopsTheRemindersOfThatMonth() {
        workerOn(DUE.minusDays(3));
        Recurrence r = database.recurrenceDao().findById(recurrenceId);
        DatabaseTest.<Void>success(cb -> recurrences.settle(r, DUE.minusDays(3), cb));
        assertTrue(workerOn(DUE.minusDays(2)).isEmpty());
        assertTrue(workerOn(DUE).isEmpty());
        assertTrue(workerOn(DUE.plusDays(1)).isEmpty());
    }

    @Test
    public void partialPaymentKeepsRemindingTheRemainingAmount() {
        workerOn(DUE);
        Recurrence r = database.recurrenceDao().findById(recurrenceId);
        DatabaseTest.<Boolean>success(cb -> recurrences.settlePartial(r, 50_000, DUE, cb));
        List<DueReminder> oneAfter = workerOn(DUE.plusDays(1));
        assertEquals(1, oneAfter.size());
        assertEquals(100_000, oneAfter.get(0).amountCents);
    }

    @Test
    public void nextMonthStartsRemindingAgain() {
        workerOn(DUE);
        Recurrence r = database.recurrenceDao().findById(recurrenceId);
        DatabaseTest.<Void>success(cb -> recurrences.settle(r, DUE, cb));
        LocalDate nextDue = DUE.plusMonths(1);
        List<DueReminder> twoBefore = workerOn(nextDue.minusDays(2));
        assertEquals(1, twoBefore.size());
        assertEquals(nextDue, twoBefore.get(0).dueDate);
    }

    @Test
    public void cardRecurrencesAreLeftToTheInvoiceReminder() {
        long cardId = createCard(userId, accountId, 5, 15);
        Recurrence onCard = new Recurrence();
        onCard.setUser_id(userId);
        onCard.setCard_id(cardId);
        onCard.setRecurrence_type(TransactionType.DESPESA);
        onCard.setRecurrence_amount_cents(5_590);
        onCard.setRecurrence_description("Streaming");
        onCard.setRecurrence_frequency(RecurrenceFrequency.MENSAL);
        onCard.setRecurrence_start_date(DUE);
        database.recurrenceDao().insert(onCard);
        for (DueReminder reminder : new DueReminderFinder(database).find(userId, DUE.minusDays(1))) {
            assertTrue("compra no cartão não gera aviso próprio", !reminder.title.equals("Streaming"));
        }
    }
}
