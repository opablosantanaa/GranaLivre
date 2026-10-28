package com.opablosantanaa.granalivre.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import com.opablosantanaa.granalivre.entity.Budget;

import java.time.YearMonth;
import java.util.List;

@Dao
public interface BudgetDao {
    @Upsert
    long upsert(Budget budget);

    @Delete
    void delete(Budget budget);

    @Query("SELECT * FROM budgets WHERE category_id = :categoryId AND budget_month = :month LIMIT 1")
    Budget findByCategoryAndMonth(long categoryId, YearMonth month);

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND budget_month = :month")
    LiveData<List<Budget>> getByUserAndMonth(long userId, YearMonth month);

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND budget_month = :month")
    List<Budget> findByUserAndMonth(long userId, YearMonth month);

    @Query("SELECT * FROM budgets WHERE budget_id = :budgetId")
    Budget findById(long budgetId);
}
