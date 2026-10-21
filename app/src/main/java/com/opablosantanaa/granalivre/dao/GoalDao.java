package com.opablosantanaa.granalivre.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.opablosantanaa.granalivre.entity.Goal;
import com.opablosantanaa.granalivre.entity.GoalMovement;

import java.time.LocalDate;
import java.util.List;

@Dao
public interface GoalDao {
    @Insert
    long insert(Goal goal);

    @Update
    void update(Goal goal);

    @Delete
    void delete(Goal goal);

    @Query("SELECT * FROM goals WHERE user_id = :userId ORDER BY goal_deadline IS NULL, goal_deadline")
    LiveData<List<Goal>> getByUser(long userId);

    @Query("SELECT * FROM goals WHERE goal_id = :goalId")
    Goal findById(long goalId);

    @Insert
    long insertMovement(GoalMovement movement);

    @Query("SELECT COALESCE(SUM(movement_amount_cents), 0) FROM goal_movements "
            + "WHERE account_id = :accountId AND movement_date > :date")
    long findReservedAfter(long accountId, LocalDate date);
}
