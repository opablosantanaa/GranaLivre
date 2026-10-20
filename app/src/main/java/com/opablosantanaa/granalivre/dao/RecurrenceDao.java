package com.opablosantanaa.granalivre.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.opablosantanaa.granalivre.entity.Recurrence;

import java.util.List;

@Dao
public interface RecurrenceDao {
    @Insert
    long insert(Recurrence recurrence);

    @Update
    void update(Recurrence recurrence);

    @Delete
    void delete(Recurrence recurrence);

    @Query("SELECT * FROM recurrences WHERE recurrence_id = :recurrenceId")
    Recurrence findById(long recurrenceId);

    @Query("SELECT * FROM recurrences WHERE user_id = :userId ORDER BY recurrence_description")
    LiveData<List<Recurrence>> getByUser(long userId);

    @Query("SELECT * FROM recurrences WHERE recurrence_active = 1")
    List<Recurrence> getAllActive();
}
