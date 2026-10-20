package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.CardDao;
import com.opablosantanaa.granalivre.dao.InvoiceDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.InvoiceStatus;
import com.opablosantanaa.granalivre.relation.InvoiceWithTransactions;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

public class InvoiceRepository extends BaseRepository {
    private final InvoiceDao invoiceDao;
    private final CardDao cardDao;
    private final TransactionRules rules;

    public InvoiceRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public InvoiceRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        invoiceDao = database.invoiceDao();
        cardDao = database.cardDao();
        rules = new TransactionRules(database);
    }

    public LiveData<List<Invoice>> getByCard(long cardId) {
        return invoiceDao.getByCard(cardId);
    }

    public LiveData<InvoiceWithTransactions> getWithTransactions(long invoiceId) {
        return invoiceDao.getWithTransactions(invoiceId);
    }

    public LiveData<Long> getTotal(long invoiceId) {
        return invoiceDao.getTotal(invoiceId);
    }

    public void close(long invoiceId, RepositoryCallback<Void> callback) {
        execute(() -> {
            Invoice invoice = requireInvoice(invoiceId);
            require(invoice.getInvoice_status() == InvoiceStatus.ABERTA, "Só faturas abertas podem ser fechadas");
            invoice.setInvoice_status(InvoiceStatus.FECHADA);
            invoiceDao.update(invoice);
            return null;
        }, callback);
    }

    public void pay(long invoiceId, Long accountId, LocalDate paidDate, RepositoryCallback<Void> callback) {
        execute(() -> {
            database.runInTransaction(() -> {
                Invoice invoice = requireInvoice(invoiceId);
                require(invoice.getInvoice_status() != InvoiceStatus.PAGA, "Esta fatura já foi paga");
                Card card = cardDao.findById(invoice.getCard_id());
                Long payingAccountId = accountId != null ? accountId : card.getCard_payment_account_id();
                require(payingAccountId != null, "Informe a conta de pagamento");
                rules.requireOwnAccount(card.getUser_id(), payingAccountId);
                invoice.setInvoice_status(InvoiceStatus.PAGA);
                invoice.setInvoice_payment_account_id(payingAccountId);
                invoice.setInvoice_paid_cents(invoiceDao.findTotal(invoiceId));
                invoice.setInvoice_paid_date(paidDate == null ? LocalDate.now() : paidDate);
                invoiceDao.update(invoice);
            });
            return null;
        }, callback);
    }

    public void reopen(long invoiceId, RepositoryCallback<Void> callback) {
        execute(() -> {
            Invoice invoice = requireInvoice(invoiceId);
            invoice.setInvoice_status(InvoiceStatus.ABERTA);
            invoice.setInvoice_payment_account_id(null);
            invoice.setInvoice_paid_cents(0);
            invoice.setInvoice_paid_date(null);
            invoiceDao.update(invoice);
            return null;
        }, callback);
    }

    private Invoice requireInvoice(long invoiceId) {
        Invoice invoice = invoiceDao.findById(invoiceId);
        require(invoice != null, "Fatura não encontrada");
        return invoice;
    }
}
