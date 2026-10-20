package com.opablosantanaa.granalivre.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Installment;

import java.util.List;

@Dao
public abstract class InstallmentDao {
    @Insert
    public abstract long insert(Installment installment);

    @Insert
    protected abstract void insertParcels(List<FinancialTransaction> parcels);

    @Delete
    public abstract void delete(Installment installment);

    @Query("SELECT * FROM installments WHERE user_id = :userId ORDER BY installment_first_date DESC")
    public abstract LiveData<List<Installment>> getByUser(long userId);

    @Query("SELECT * FROM transactions WHERE installment_id = :installmentId "
            + "ORDER BY transaction_installment_number")
    public abstract LiveData<List<FinancialTransaction>> getParcels(long installmentId);

    @Transaction
    public long insertWithParcels(Installment installment, List<FinancialTransaction> parcels) {
        long installmentId = insert(installment);
        for (FinancialTransaction parcel : parcels) {
            parcel.setInstallment_id(installmentId);
        }
        insertParcels(parcels);
        return installmentId;
    }
}
