package com.opablosantanaa.granalivre.repository;

import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.DueReminder;
import com.opablosantanaa.granalivre.domain.RecurrenceSchedule;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DueReminderFinder {
    private final AppDatabase database;

    public DueReminderFinder(AppDatabase database) {
        this.database = database;
    }

    public List<DueReminder> find(long userId, LocalDate today) {
        LocalDate start = DueReminder.windowStart(today);
        LocalDate end = DueReminder.windowEnd(today);
        List<DueReminder> reminders = new ArrayList<>();

        for (Invoice invoice : database.invoiceDao().findUnpaidDueBetween(userId, start, end)) {
            long total = database.invoiceDao().findTotal(invoice.getInvoice_id());
            if (total <= 0) continue;
            Card card = database.cardDao().findById(invoice.getCard_id());
            String name = card == null ? "" : card.getCard_nickname();
            reminders.add(new DueReminder(DueReminder.Kind.INVOICE, invoice.getInvoice_id(), name, total,
                    invoice.getInvoice_due_date()));
        }

        for (FinancialTransaction t : database.financialTransactionDao()
                .findPendingExpensesBetween(userId, start, end)) {
            reminders.add(new DueReminder(DueReminder.Kind.TRANSACTION, t.getTransaction_id(),
                    t.getTransaction_description(), t.getTransaction_amount_cents(), t.getTransaction_date()));
        }

        for (Recurrence r : database.recurrenceDao().getAllActive()) {
            if (r.getUser_id() != userId || r.getRecurrence_type() != TransactionType.DESPESA
                    || r.getCard_id() != null) continue;
            for (LocalDate date : RecurrenceSchedule.pendingOccurrences(r.getRecurrence_start_date(),
                    r.getRecurrence_end_date(), r.getRecurrence_frequency(),
                    r.getRecurrence_last_generated_date(), end)) {
                if (!date.isAfter(today)) continue;
                reminders.add(new DueReminder(DueReminder.Kind.RECURRENCE, r.getRecurrence_id(),
                        r.getRecurrence_description(), r.getRecurrence_amount_cents(), date));
            }
        }

        reminders.sort(Comparator.comparing(reminder -> reminder.dueDate));
        return reminders;
    }
}
