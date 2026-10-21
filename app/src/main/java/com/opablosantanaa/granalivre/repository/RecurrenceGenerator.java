package com.opablosantanaa.granalivre.repository;

import com.opablosantanaa.granalivre.dao.FinancialTransactionDao;
import com.opablosantanaa.granalivre.dao.RecurrenceDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.RecurrenceSchedule;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Recurrence;

import java.time.LocalDate;
import java.util.List;

public class RecurrenceGenerator {
    private final AppDatabase database;
    private final RecurrenceDao recurrenceDao;
    private final FinancialTransactionDao transactionDao;
    private final TransactionRules rules;

    public RecurrenceGenerator(AppDatabase database) {
        this.database = database;
        recurrenceDao = database.recurrenceDao();
        transactionDao = database.financialTransactionDao();
        rules = new TransactionRules(database);
    }

    public int generateUntil(LocalDate today) {
        int created = 0;
        for (Recurrence recurrence : recurrenceDao.getAllActive()) {
            created += database.runInTransaction(() -> generate(recurrence, today));
        }
        return created;
    }

    int generate(Recurrence recurrence, LocalDate today) {
        List<LocalDate> dates = RecurrenceSchedule.pendingOccurrences(
                recurrence.getRecurrence_start_date(),
                recurrence.getRecurrence_end_date(),
                recurrence.getRecurrence_frequency(),
                recurrence.getRecurrence_last_generated_date(),
                today);
        int created = 0;
        for (LocalDate date : dates) {
            FinancialTransaction transaction = buildTransaction(recurrence, date);
            try {
                rules.prepare(transaction);
            } catch (ValidationException skipped) {
                continue;
            }
            transactionDao.insert(transaction);
            created++;
        }
        boolean changed = false;
        if (!dates.isEmpty()) {
            recurrence.setRecurrence_last_generated_date(dates.get(dates.size() - 1));
            changed = true;
        }
        LocalDate end = recurrence.getRecurrence_end_date();
        if (end != null && !end.isAfter(today)) {
            recurrence.setRecurrence_active(false);
            changed = true;
        }
        if (changed) {
            recurrenceDao.update(recurrence);
        }
        return created;
    }

    static FinancialTransaction buildTransaction(Recurrence recurrence, LocalDate date) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setUser_id(recurrence.getUser_id());
        transaction.setAccount_id(recurrence.getAccount_id());
        transaction.setCard_id(recurrence.getCard_id());
        transaction.setCategory_id(recurrence.getCategory_id());
        transaction.setRecurrence_id(recurrence.getRecurrence_id());
        transaction.setTransaction_type(recurrence.getRecurrence_type());
        transaction.setTransaction_amount_cents(recurrence.getRecurrence_amount_cents());
        transaction.setTransaction_description(recurrence.getRecurrence_description());
        transaction.setTransaction_date(date);
        transaction.setTransaction_paid(false);
        return transaction;
    }
}
