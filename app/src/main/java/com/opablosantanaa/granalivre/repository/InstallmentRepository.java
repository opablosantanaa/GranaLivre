package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.isBlank;
import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.InstallmentDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.InstallmentSplitter;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Installment;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class InstallmentRepository extends BaseRepository {
    private static final int MIN_INSTALLMENTS = 2;

    private final InstallmentDao installmentDao;
    private final TransactionRules rules;

    public InstallmentRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public InstallmentRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        installmentDao = database.installmentDao();
        rules = new TransactionRules(database);
    }

    public LiveData<List<Installment>> getByUser(long userId) {
        return installmentDao.getByUser(userId);
    }

    public LiveData<List<FinancialTransaction>> getParcels(long installmentId) {
        return installmentDao.getParcels(installmentId);
    }

    public void create(Installment installment, Long accountId, Long cardId, Long categoryId,
                       RepositoryCallback<Long> callback) {
        execute(() -> database.runInTransaction(() -> {
            installment.setInstallment_description(installment.getInstallment_description().trim());
            require(!isBlank(installment.getInstallment_description()), "Informe a descrição da compra");
            require(installment.getInstallment_count() >= MIN_INSTALLMENTS,
                    "O parcelamento deve ter pelo menos " + MIN_INSTALLMENTS + " parcelas");
            require(installment.getInstallment_total_cents() >= installment.getInstallment_count(),
                    "Valor total insuficiente para o número de parcelas");
            long userId = installment.getUser_id();
            Card card = rules.validateSource(userId, accountId, cardId);
            Long category = rules.resolveCategory(userId, categoryId, TransactionType.DESPESA);
            List<FinancialTransaction> parcels = buildParcels(installment, accountId, card, category);
            return installmentDao.insertWithParcels(installment, parcels);
        }), callback);
    }

    public void delete(Installment installment, RepositoryCallback<Void> callback) {
        execute(() -> {
            installmentDao.delete(installment);
            return null;
        }, callback);
    }

    private List<FinancialTransaction> buildParcels(Installment installment, Long accountId, Card card,
                                                    Long categoryId) {
        int count = installment.getInstallment_count();
        List<Long> amounts = InstallmentSplitter.split(installment.getInstallment_total_cents(), count);
        LocalDate today = LocalDate.now();
        List<FinancialTransaction> parcels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            LocalDate date = installment.getInstallment_first_date().plusMonths(i);
            FinancialTransaction parcel = new FinancialTransaction();
            parcel.setUser_id(installment.getUser_id());
            parcel.setAccount_id(accountId);
            parcel.setCard_id(card == null ? null : card.getCard_id());
            parcel.setInvoice_id(card == null ? null : rules.resolveInvoiceId(card, date));
            parcel.setCategory_id(categoryId);
            parcel.setTransaction_type(TransactionType.DESPESA);
            parcel.setTransaction_amount_cents(amounts.get(i));
            parcel.setTransaction_date(date);
            parcel.setTransaction_installment_number(i + 1);
            parcel.setTransaction_description(
                    installment.getInstallment_description() + " (" + (i + 1) + "/" + count + ")");
            parcel.setTransaction_paid(!date.isAfter(today));
            parcels.add(parcel);
        }
        return parcels;
    }
}
