package com.opablosantanaa.granalivre.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.relation.InvoiceWithTransactions;

import java.time.YearMonth;
import java.util.List;

@Dao
public interface InvoiceDao {
    @Insert
    long insert(Invoice invoice);

    @Update
    void update(Invoice invoice);

    @Delete
    void delete(Invoice invoice);

    @Query("SELECT * FROM invoices WHERE invoice_id = :invoiceId")
    Invoice findById(long invoiceId);

    @Query("SELECT * FROM invoices WHERE card_id = :cardId ORDER BY invoice_reference_month DESC")
    LiveData<List<Invoice>> getByCard(long cardId);

    @Query("SELECT * FROM invoices WHERE card_id = :cardId AND invoice_reference_month = :month LIMIT 1")
    Invoice findByCardAndMonth(long cardId, YearMonth month);

    @Transaction
    @Query("SELECT * FROM invoices WHERE invoice_id = :invoiceId")
    LiveData<InvoiceWithTransactions> getWithTransactions(long invoiceId);

    @Query("SELECT COALESCE(SUM(CASE WHEN transaction_type = 'DESPESA' "
            + "THEN transaction_amount_cents ELSE -transaction_amount_cents END), 0) "
            + "FROM transactions WHERE invoice_id = :invoiceId")
    LiveData<Long> getTotal(long invoiceId);

    @Query("SELECT COALESCE(SUM(CASE WHEN transaction_type = 'DESPESA' "
            + "THEN transaction_amount_cents ELSE -transaction_amount_cents END), 0) "
            + "FROM transactions WHERE invoice_id = :invoiceId")
    long findTotal(long invoiceId);
}
