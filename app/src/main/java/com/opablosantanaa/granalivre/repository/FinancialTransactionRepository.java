package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.FinancialTransactionDao;
import com.opablosantanaa.granalivre.dao.InvoiceDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.InvoiceStatus;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

public class FinancialTransactionRepository extends BaseRepository {
    private final FinancialTransactionDao transactionDao;
    private final InvoiceDao invoiceDao;
    private final TransactionRules rules;

    public FinancialTransactionRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public FinancialTransactionRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        transactionDao = database.financialTransactionDao();
        invoiceDao = database.invoiceDao();
        rules = new TransactionRules(database);
    }

    public LiveData<FinancialTransaction> getById(long transactionId) {
        return transactionDao.getById(transactionId);
    }

    public LiveData<List<FinancialTransaction>> getByUserBetween(long userId, LocalDate start, LocalDate end) {
        return transactionDao.getByUserBetween(userId, start, end);
    }

    public LiveData<List<FinancialTransaction>> getByAccount(long accountId) {
        return transactionDao.getByAccount(accountId);
    }

    public LiveData<List<FinancialTransaction>> getPending(long userId) {
        return transactionDao.getPending(userId);
    }

    public LiveData<Long> getTotalByTypeBetween(long userId, TransactionType type, LocalDate start, LocalDate end) {
        return transactionDao.getTotalByTypeBetween(userId, type, start, end);
    }

    public LiveData<Long> getExpenseByCategoryBetween(long categoryId, LocalDate start, LocalDate end) {
        return transactionDao.getExpenseByCategoryBetween(categoryId, start, end);
    }

    public void insert(FinancialTransaction transaction, RepositoryCallback<Long> callback) {
        execute(() -> database.runInTransaction(() -> {
            rules.prepare(transaction);
            return transactionDao.insert(transaction);
        }), callback);
    }

    public void update(FinancialTransaction transaction, RepositoryCallback<Void> callback) {
        execute(() -> {
            database.runInTransaction(() -> {
                requireNotInPaidInvoice(transaction.getTransaction_id());
                rules.prepare(transaction);
                transactionDao.update(transaction);
            });
            return null;
        }, callback);
    }

    public void delete(FinancialTransaction transaction, RepositoryCallback<Void> callback) {
        execute(() -> {
            database.runInTransaction(() -> {
                requireNotInPaidInvoice(transaction.getTransaction_id());
                transactionDao.delete(transaction);
            });
            return null;
        }, callback);
    }

    private void requireNotInPaidInvoice(long transactionId) {
        FinancialTransaction stored = transactionDao.findById(transactionId);
        if (stored == null || stored.getInvoice_id() == null) {
            return;
        }
        Invoice invoice = invoiceDao.findById(stored.getInvoice_id());
        require(invoice == null || invoice.getInvoice_status() != InvoiceStatus.PAGA,
                "Lançamentos de uma fatura paga não podem ser alterados");
    }
}
