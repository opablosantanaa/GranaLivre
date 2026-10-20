package com.opablosantanaa.granalivre.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.opablosantanaa.granalivre.entity.Transfer;

import java.time.LocalDate;
import java.util.List;

@Dao
public interface TransferDao {
    @Insert
    long insert(Transfer transfer);

    @Update
    void update(Transfer transfer);

    @Delete
    void delete(Transfer transfer);

    @Query("SELECT * FROM transfers WHERE user_id = :userId "
            + "AND transfer_date BETWEEN :start AND :end "
            + "ORDER BY transfer_date DESC, transfer_id DESC")
    LiveData<List<Transfer>> getByUserBetween(long userId, LocalDate start, LocalDate end);

    @Query("SELECT * FROM transfers WHERE transfer_from_account_id = :accountId "
            + "OR transfer_to_account_id = :accountId "
            + "ORDER BY transfer_date DESC, transfer_id DESC")
    LiveData<List<Transfer>> getByAccount(long accountId);
}
