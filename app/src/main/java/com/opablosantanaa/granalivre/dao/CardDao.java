package com.opablosantanaa.granalivre.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.opablosantanaa.granalivre.entity.Card;

import java.util.List;

@Dao
public interface CardDao {
    @Insert
    long insert(Card card);

    @Update
    void update(Card card);

    @Delete
    void delete(Card card);

    @Query("SELECT * FROM cards WHERE card_id = :cardId")
    LiveData<Card> getById(long cardId);

    @Query("SELECT * FROM cards WHERE card_id = :cardId")
    Card findById(long cardId);

    @Query("SELECT * FROM cards WHERE user_id = :userId ORDER BY card_nickname")
    LiveData<List<Card>> getByUser(long userId);

    @Query("SELECT COALESCE(SUM(CASE WHEN t.transaction_type = 'DESPESA' "
            + "THEN t.transaction_amount_cents ELSE -t.transaction_amount_cents END), 0) "
            + "FROM transactions t "
            + "LEFT JOIN invoices i ON i.invoice_id = t.invoice_id "
            + "WHERE t.card_id = :cardId "
            + "AND (i.invoice_status IS NULL OR i.invoice_status != 'PAGA')")
    LiveData<Long> getOpenDebt(long cardId);
}
