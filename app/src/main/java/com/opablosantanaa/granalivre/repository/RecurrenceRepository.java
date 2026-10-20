package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.isBlank;
import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.RecurrenceDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
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
        rules.validateCategory(recurrence.getUser_id(), recurrence.getCategory_id(), recurrence.getRecurrence_type());
    }
}
