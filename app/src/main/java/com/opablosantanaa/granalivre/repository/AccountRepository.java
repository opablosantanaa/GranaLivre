package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.isBlank;
import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.AccountDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;

import java.util.List;
import java.util.concurrent.Executor;

public class AccountRepository extends BaseRepository {
    private final AccountDao accountDao;

    public AccountRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public AccountRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        accountDao = database.accountDao();
    }

    public LiveData<Account> getById(long accountId) {
        return accountDao.getById(accountId);
    }

    public LiveData<List<Account>> getByUser(long userId) {
        return accountDao.getByUser(userId);
    }

    public LiveData<List<AccountWithBalance>> getWithBalanceByUser(long userId) {
        return accountDao.getWithBalanceByUser(userId);
    }

    public LiveData<Long> getBalance(long accountId) {
        return accountDao.getBalance(accountId);
    }

    public LiveData<Long> getTotalBalance(long userId) {
        return accountDao.getTotalBalance(userId);
    }

    public void insert(Account account, RepositoryCallback<Long> callback) {
        execute(() -> {
            validate(account);
            return accountDao.insert(account);
        }, callback);
    }

    public void update(Account account, RepositoryCallback<Void> callback) {
        execute(() -> {
            validate(account);
            accountDao.update(account);
            return null;
        }, callback);
    }

    public void delete(Account account, RepositoryCallback<Void> callback) {
        execute(() -> {
            accountDao.delete(account);
            return null;
        }, callback);
    }

    private void validate(Account account) {
        account.setAccount_bank_name(account.getAccount_bank_name().trim());
        require(!isBlank(account.getAccount_bank_name()), "Informe o nome do banco");
    }
}
