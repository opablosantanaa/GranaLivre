package com.opablosantanaa.granalivre.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;

import java.util.List;

@Dao
public interface AccountDao {
    String BALANCE = "(a.account_initial_balance_cents"
            + " + COALESCE((SELECT SUM(CASE WHEN t.transaction_type = 'RECEITA'"
            + " THEN t.transaction_amount_cents ELSE -t.transaction_amount_cents END)"
            + " FROM transactions t WHERE t.account_id = a.account_id AND t.transaction_paid = 1), 0)"
            + " + COALESCE((SELECT SUM(tr.transfer_amount_cents) FROM transfers tr"
            + " WHERE tr.transfer_to_account_id = a.account_id), 0)"
            + " - COALESCE((SELECT SUM(tr.transfer_amount_cents) FROM transfers tr"
            + " WHERE tr.transfer_from_account_id = a.account_id), 0)"
            + " - COALESCE((SELECT SUM(i.invoice_paid_cents) FROM invoices i"
            + " WHERE i.invoice_payment_account_id = a.account_id AND i.invoice_status = 'PAGA'), 0)"
            + " - COALESCE((SELECT SUM(gm.movement_amount_cents) FROM goal_movements gm"
            + " WHERE gm.account_id = a.account_id), 0))";

    @Insert
    long insert(Account account);

    @Update
    void update(Account account);

    @Delete
    void delete(Account account);

    @Query("SELECT * FROM accounts WHERE account_id = :accountId")
    LiveData<Account> getById(long accountId);

    @Query("SELECT * FROM accounts WHERE account_id = :accountId")
    Account findById(long accountId);

    @Query("SELECT * FROM accounts WHERE user_id = :userId ORDER BY account_bank_name")
    LiveData<List<Account>> getByUser(long userId);

    @Query("SELECT a.*, " + BALANCE + " AS balance_cents FROM accounts a "
            + "WHERE a.user_id = :userId ORDER BY a.account_bank_name")
    LiveData<List<AccountWithBalance>> getWithBalanceByUser(long userId);

    @Query("SELECT " + BALANCE + " FROM accounts a WHERE a.account_id = :accountId")
    LiveData<Long> getBalance(long accountId);

    @Query("SELECT " + BALANCE + " FROM accounts a WHERE a.account_id = :accountId")
    long findBalance(long accountId);

    @Query("SELECT COALESCE(SUM" + BALANCE + ", 0) FROM accounts a WHERE a.user_id = :userId")
    LiveData<Long> getTotalBalance(long userId);
}
