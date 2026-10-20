package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.TransferDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.entity.Transfer;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

public class TransferRepository extends BaseRepository {
    private final TransferDao transferDao;
    private final TransactionRules rules;

    public TransferRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public TransferRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        transferDao = database.transferDao();
        rules = new TransactionRules(database);
    }

    public LiveData<List<Transfer>> getByUserBetween(long userId, LocalDate start, LocalDate end) {
        return transferDao.getByUserBetween(userId, start, end);
    }

    public LiveData<List<Transfer>> getByAccount(long accountId) {
        return transferDao.getByAccount(accountId);
    }

    public void insert(Transfer transfer, RepositoryCallback<Long> callback) {
        execute(() -> {
            validate(transfer);
            return transferDao.insert(transfer);
        }, callback);
    }

    public void update(Transfer transfer, RepositoryCallback<Void> callback) {
        execute(() -> {
            validate(transfer);
            transferDao.update(transfer);
            return null;
        }, callback);
    }

    public void delete(Transfer transfer, RepositoryCallback<Void> callback) {
        execute(() -> {
            transferDao.delete(transfer);
            return null;
        }, callback);
    }

    private void validate(Transfer transfer) {
        require(transfer.getTransfer_amount_cents() > 0, "O valor deve ser maior que zero");
        require(transfer.getTransfer_from_account_id() != transfer.getTransfer_to_account_id(),
                "As contas de origem e destino devem ser diferentes");
        rules.requireOwnAccount(transfer.getUser_id(), transfer.getTransfer_from_account_id());
        rules.requireOwnAccount(transfer.getUser_id(), transfer.getTransfer_to_account_id());
    }
}
