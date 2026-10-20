package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.BudgetDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Executor;

public class BudgetRepository extends BaseRepository {
    private static final long UPDATED_ROW = -1L;

    private final BudgetDao budgetDao;
    private final TransactionRules rules;

    public BudgetRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public BudgetRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        budgetDao = database.budgetDao();
        rules = new TransactionRules(database);
    }

    public LiveData<List<Budget>> getByUserAndMonth(long userId, YearMonth month) {
        return budgetDao.getByUserAndMonth(userId, month);
    }

    public void save(Budget budget, RepositoryCallback<Long> callback) {
        execute(() -> database.runInTransaction(() -> {
            require(budget.getBudget_limit_cents() > 0, "O limite deve ser maior que zero");
            rules.validateCategory(budget.getUser_id(), budget.getCategory_id(), TransactionType.DESPESA);
            Budget existing = budgetDao.findByCategoryAndMonth(budget.getCategory_id(), budget.getBudget_month());
            if (existing != null) {
                budget.setBudget_id(existing.getBudget_id());
            }
            long rowId = budgetDao.upsert(budget);
            return rowId == UPDATED_ROW ? budget.getBudget_id() : rowId;
        }), callback);
    }

    public void delete(Budget budget, RepositoryCallback<Void> callback) {
        execute(() -> {
            budgetDao.delete(budget);
            return null;
        }, callback);
    }
}
