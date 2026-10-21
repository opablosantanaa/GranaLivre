package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.isBlank;
import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.FinancialTransactionDao;
import com.opablosantanaa.granalivre.dao.RecurrenceDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.RecurrenceSchedule;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Recurrence;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

public class RecurrenceRepository extends BaseRepository {
    private final RecurrenceDao recurrenceDao;
    private final TransactionRules rules;
    private final RecurrenceGenerator generator;

    public RecurrenceRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public RecurrenceRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        recurrenceDao = database.recurrenceDao();
        rules = new TransactionRules(database);
        generator = new RecurrenceGenerator(database);
    }

    public LiveData<List<Recurrence>> getByUser(long userId) {
        return recurrenceDao.getByUser(userId);
    }

    public void insert(Recurrence recurrence, RepositoryCallback<Long> callback) {
        execute(() -> database.runInTransaction(() -> {
            validate(recurrence);
            recurrence.setRecurrence_last_generated_date(null);
            long recurrenceId = recurrenceDao.insert(recurrence);
            recurrence.setRecurrence_id(recurrenceId);
            generator.generate(recurrence, LocalDate.now());
            return recurrenceId;
        }), callback);
    }

    public void settle(Recurrence recurrence, LocalDate settledOn, RepositoryCallback<Void> callback) {
        execute(() -> {
            database.runInTransaction(() -> {
                FinancialTransactionDao transactionDao = database.financialTransactionDao();
                FinancialTransaction pending = transactionDao.findOldestPendingByRecurrence(recurrence.getRecurrence_id());
                if (pending != null) {
                    pending.setTransaction_paid(true);
                    pending.setTransaction_date(settledOn);
                    rules.prepare(pending);
                    transactionDao.update(pending);
                    return;
                }
                FinancialTransaction transaction = openNextOccurrence(recurrence.getRecurrence_id());
                transaction.setTransaction_date(settledOn);
                transaction.setTransaction_paid(true);
                rules.prepare(transaction);
                transactionDao.insert(transaction);
            });
            return null;
        }, callback);
    }

    /**
     * Registra um pagamento/recebimento parcial da ocorrência em aberto mais antiga. O valor informado vira
     * um lançamento pago e é abatido da ocorrência, que continua pendente com o restante. Se o valor cobrir
     * o restante, a ocorrência é quitada por inteiro.
     */
    public void settlePartial(Recurrence recurrence, long cents, LocalDate settledOn, RepositoryCallback<Boolean> callback) {
        execute(() -> database.runInTransaction(() -> {
            require(cents > 0, "O valor deve ser maior que zero");
            FinancialTransactionDao transactionDao = database.financialTransactionDao();
            FinancialTransaction pending = transactionDao.findOldestPendingByRecurrence(recurrence.getRecurrence_id());
            if (pending == null) {
                pending = openNextOccurrence(recurrence.getRecurrence_id());
                rules.prepare(pending);
                pending.setTransaction_id(transactionDao.insert(pending));
            }
            if (cents >= pending.getTransaction_amount_cents()) {
                pending.setTransaction_paid(true);
                pending.setTransaction_date(settledOn);
                rules.prepare(pending);
                transactionDao.update(pending);
                return true;
            }
            FinancialTransaction part = new FinancialTransaction();
            part.setUser_id(pending.getUser_id());
            part.setAccount_id(pending.getAccount_id());
            part.setCard_id(pending.getCard_id());
            part.setCategory_id(pending.getCategory_id());
            part.setRecurrence_id(pending.getRecurrence_id());
            part.setTransaction_type(pending.getTransaction_type());
            part.setTransaction_amount_cents(cents);
            part.setTransaction_description(pending.getTransaction_description() + " (parcial)");
            part.setTransaction_date(settledOn);
            part.setTransaction_paid(true);
            rules.prepare(part);
            transactionDao.insert(part);
            pending.setTransaction_amount_cents(pending.getTransaction_amount_cents() - cents);
            rules.prepare(pending);
            transactionDao.update(pending);
            return false;
        }), callback);
    }

    /** Cria (sem gravar) o lançamento pendente da próxima ocorrência ainda não gerada e avança a recorrência. */
    private FinancialTransaction openNextOccurrence(long recurrenceId) {
        Recurrence stored = recurrenceDao.findById(recurrenceId);
        require(stored != null, "Recorrência não encontrada");
        LocalDate next = nextUnsettled(stored);
        require(next != null, "Esta recorrência não tem próximas ocorrências");
        stored.setRecurrence_last_generated_date(next);
        recurrenceDao.update(stored);
        return RecurrenceGenerator.buildTransaction(stored, next);
    }

    public static LocalDate nextUnsettled(Recurrence recurrence) {
        LocalDate last = recurrence.getRecurrence_last_generated_date();
        LocalDate end = recurrence.getRecurrence_end_date();
        for (int n = 0; n < 100_000; n++) {
            LocalDate date = RecurrenceSchedule.occurrence(recurrence.getRecurrence_start_date(),
                    recurrence.getRecurrence_frequency(), n);
            if (end != null && date.isAfter(end)) return null;
            if (last == null || date.isAfter(last)) return date;
        }
        return null;
    }

    public void update(Recurrence recurrence, RepositoryCallback<Void> callback) {
        execute(() -> {
            database.runInTransaction(() -> {
                validate(recurrence);
                Recurrence stored = recurrenceDao.findById(recurrence.getRecurrence_id());
                require(stored != null, "Recorrência não encontrada");
                recurrence.setRecurrence_last_generated_date(stored.getRecurrence_last_generated_date());
                recurrenceDao.update(recurrence);
                if (recurrence.isRecurrence_active()) {
                    generator.generate(recurrence, LocalDate.now());
                }
            });
            return null;
        }, callback);
    }

    public void delete(Recurrence recurrence, RepositoryCallback<Void> callback) {
        execute(() -> {
            recurrenceDao.delete(recurrence);
            return null;
        }, callback);
    }

    private void validate(Recurrence recurrence) {
        recurrence.setRecurrence_description(recurrence.getRecurrence_description().trim());
        require(!isBlank(recurrence.getRecurrence_description()), "Informe a descrição");
        require(recurrence.getRecurrence_amount_cents() > 0, "O valor deve ser maior que zero");
        require(recurrence.getRecurrence_end_date() == null
                        || !recurrence.getRecurrence_end_date().isBefore(recurrence.getRecurrence_start_date()),
                "A data de término deve ser igual ou posterior à data de início");
        rules.validateSource(recurrence.getUser_id(), recurrence.getAccount_id(), recurrence.getCard_id());
        recurrence.setCategory_id(rules.resolveCategory(recurrence.getUser_id(), recurrence.getCategory_id(),
                recurrence.getRecurrence_type()));
    }
}
