package com.opablosantanaa.granalivre.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.entity.AccountType;
import com.opablosantanaa.granalivre.entity.BackupId;
import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Goal;
import com.opablosantanaa.granalivre.entity.GoalMovement;
import com.opablosantanaa.granalivre.entity.Installment;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.Transfer;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.time.YearMonth;
import java.util.List;

@Dao
public interface BackupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void link(BackupId id);

    @Nullable
    @Query("SELECT backup_uuid FROM backup_ids WHERE backup_table = :table AND local_id = :localId")
    String findUuid(String table, long localId);

    @Nullable
    @Query("SELECT local_id FROM backup_ids WHERE backup_table = :table AND backup_uuid = :uuid")
    Long findLocalId(String table, String uuid);

    @Query("DELETE FROM backup_ids WHERE backup_table = :table AND backup_uuid = :uuid")
    void unlink(String table, String uuid);

    @Query("SELECT * FROM accounts WHERE user_id = :userId ORDER BY account_id")
    List<Account> accounts(long userId);

    @Query("SELECT * FROM categories WHERE user_id = :userId ORDER BY category_id")
    List<Category> categories(long userId);

    @Query("SELECT * FROM cards WHERE user_id = :userId ORDER BY card_id")
    List<Card> cards(long userId);

    @Query("SELECT i.* FROM invoices i JOIN cards c ON c.card_id = i.card_id WHERE c.user_id = :userId "
            + "ORDER BY i.invoice_id")
    List<Invoice> invoices(long userId);

    @Query("SELECT * FROM installments WHERE user_id = :userId ORDER BY installment_id")
    List<Installment> installments(long userId);

    @Query("SELECT * FROM recurrences WHERE user_id = :userId ORDER BY recurrence_id")
    List<Recurrence> recurrences(long userId);

    @Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY transaction_date, transaction_id")
    List<FinancialTransaction> transactions(long userId);

    @Query("SELECT * FROM transfers WHERE user_id = :userId ORDER BY transfer_date, transfer_id")
    List<Transfer> transfers(long userId);

    @Query("SELECT * FROM budgets WHERE user_id = :userId ORDER BY budget_id")
    List<Budget> budgets(long userId);

    @Query("SELECT * FROM goals WHERE user_id = :userId ORDER BY goal_id")
    List<Goal> goals(long userId);

    @Query("SELECT m.* FROM goal_movements m JOIN goals g ON g.goal_id = m.goal_id WHERE g.user_id = :userId "
            + "ORDER BY m.movement_date, m.movement_id")
    List<GoalMovement> goalMovements(long userId);

    @Query("SELECT account_id FROM accounts WHERE user_id = :userId AND account_bank_name = :name "
            + "AND account_type = :type ORDER BY account_id")
    List<Long> matchAccount(long userId, String name, AccountType type);

    @Query("SELECT category_id FROM categories WHERE user_id = :userId AND category_name = :name "
            + "AND category_type = :type AND category_parent_id IS :parentId ORDER BY category_id")
    List<Long> matchCategory(long userId, String name, TransactionType type, @Nullable Long parentId);

    @Query("SELECT card_id FROM cards WHERE user_id = :userId AND card_nickname = :nickname "
            + "AND card_last_digits = :lastDigits ORDER BY card_id")
    List<Long> matchCard(long userId, String nickname, String lastDigits);

    @Query("SELECT invoice_id FROM invoices WHERE card_id = :cardId AND invoice_reference_month = :month")
    List<Long> matchInvoice(long cardId, YearMonth month);

    @Query("SELECT budget_id FROM budgets WHERE category_id = :categoryId AND budget_month = :month")
    List<Long> matchBudget(long categoryId, YearMonth month);

    @Query("SELECT goal_id FROM goals WHERE user_id = :userId AND goal_name = :name ORDER BY goal_id")
    List<Long> matchGoal(long userId, String name);

    @Query("SELECT transaction_id FROM transactions WHERE account_id = :accountId "
            + "AND transaction_import_id = :importId")
    List<Long> matchImportedTransaction(long accountId, String importId);

    @Query("UPDATE transactions SET transaction_paid = 1 WHERE transaction_id = :transactionId "
            + "AND transaction_paid = 0")
    int markPaid(long transactionId);

    @Query("UPDATE goals SET goal_saved_cents = goal_saved_cents + :cents WHERE goal_id = :goalId")
    void addToGoal(long goalId, long cents);
}
