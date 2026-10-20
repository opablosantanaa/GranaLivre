package com.opablosantanaa.granalivre.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.time.LocalDate;
import java.util.List;

@Dao
public interface FinancialTransactionDao {
    @Insert
    long insert(FinancialTransaction transaction);

    @Insert
    List<Long> insertAll(List<FinancialTransaction> transactions);

    @Update
    void update(FinancialTransaction transaction);

    @Delete
    void delete(FinancialTransaction transaction);

    @Query("SELECT * FROM transactions WHERE transaction_id = :transactionId")
    LiveData<FinancialTransaction> getById(long transactionId);

    @Query("SELECT * FROM transactions WHERE transaction_id = :transactionId")
    FinancialTransaction findById(long transactionId);

    @Query("SELECT * FROM transactions WHERE user_id = :userId "
            + "AND transaction_date BETWEEN :start AND :end "
            + "ORDER BY transaction_date DESC, transaction_id DESC")
    LiveData<List<FinancialTransaction>> getByUserBetween(long userId, LocalDate start, LocalDate end);

    @Query("SELECT * FROM transactions WHERE account_id = :accountId "
            + "ORDER BY transaction_date DESC, transaction_id DESC")
    LiveData<List<FinancialTransaction>> getByAccount(long accountId);

    @Query("SELECT * FROM transactions WHERE transaction_paid = 0 AND user_id = :userId "
            + "ORDER BY transaction_date")
    LiveData<List<FinancialTransaction>> getPending(long userId);

    @Query("SELECT COALESCE(SUM(transaction_amount_cents), 0) FROM transactions "
            + "WHERE user_id = :userId AND transaction_type = :type "
            + "AND transaction_date BETWEEN :start AND :end")
    LiveData<Long> getTotalByTypeBetween(long userId, TransactionType type, LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(transaction_amount_cents), 0) FROM transactions "
            + "WHERE category_id = :categoryId AND transaction_type = 'DESPESA' "
            + "AND transaction_date BETWEEN :start AND :end")
    LiveData<Long> getExpenseByCategoryBetween(long categoryId, LocalDate start, LocalDate end);
}
