package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.isBlank;
import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.GoalDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.entity.Goal;
import com.opablosantanaa.granalivre.entity.GoalMovement;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

public class GoalRepository extends BaseRepository {
    private final GoalDao goalDao;

    public GoalRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public GoalRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        goalDao = database.goalDao();
    }

    public LiveData<List<Goal>> getByUser(long userId) {
        return goalDao.getByUser(userId);
    }

    public void insert(Goal goal, RepositoryCallback<Long> callback) {
        execute(() -> {
            validate(goal);
            return goalDao.insert(goal);
        }, callback);
    }

    public void update(Goal goal, RepositoryCallback<Void> callback) {
        execute(() -> {
            validate(goal);
            goalDao.update(goal);
            return null;
        }, callback);
    }

    public void delete(Goal goal, RepositoryCallback<Void> callback) {
        execute(() -> {
            goalDao.delete(goal);
            return null;
        }, callback);
    }

    /**
     * Guarda (valor positivo) ou retira (negativo) dinheiro de uma meta, movendo-o de/para uma conta.
     * Guardar exige saldo na conta; retirar não pode passar do que está guardado na meta.
     */
    public void move(long goalId, long accountId, long cents, RepositoryCallback<Void> callback) {
        execute(() -> {
            database.runInTransaction(() -> {
                require(cents != 0, "O valor deve ser maior que zero");
                Goal goal = goalDao.findById(goalId);
                require(goal != null, "Meta não encontrada");
                Account account = database.accountDao().findById(accountId);
                require(account != null && account.getUser_id() == goal.getUser_id(), "Conta inválida");
                if (cents > 0) {
                    require(database.accountDao().findBalance(accountId) >= cents,
                            "Saldo insuficiente em " + account.getAccount_bank_name());
                } else {
                    require(-cents <= goal.getGoal_saved_cents(), "Não há tanto dinheiro guardado nesta meta");
                }
                GoalMovement movement = new GoalMovement();
                movement.setGoal_id(goalId);
                movement.setAccount_id(accountId);
                movement.setMovement_amount_cents(cents);
                movement.setMovement_date(LocalDate.now());
                goalDao.insertMovement(movement);
                goal.setGoal_saved_cents(goal.getGoal_saved_cents() + cents);
                goalDao.update(goal);
            });
            return null;
        }, callback);
    }

    private void validate(Goal goal) {
        goal.setGoal_name(goal.getGoal_name().trim());
        require(!isBlank(goal.getGoal_name()), "Informe o nome da meta");
        require(goal.getGoal_target_cents() > 0, "O valor da meta deve ser maior que zero");
        require(goal.getGoal_saved_cents() >= 0, "O valor guardado não pode ser negativo");
    }
}
