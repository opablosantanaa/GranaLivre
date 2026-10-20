package com.opablosantanaa.granalivre.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.opablosantanaa.granalivre.entity.User;
import com.opablosantanaa.granalivre.relation.UserWithAccounts;
import com.opablosantanaa.granalivre.relation.UserWithCards;

@Dao
public interface UserDao {
    @Insert
    long insert(User user);

    @Update
    void update(User user);

    @Delete
    void delete(User user);

    @Query("SELECT * FROM users WHERE user_id = :userId")
    LiveData<User> getById(long userId);

    @Query("SELECT * FROM users WHERE user_id = :userId")
    User findById(long userId);

    @Query("SELECT * FROM users WHERE user_email = :email LIMIT 1")
    User findByEmail(String email);

    @Transaction
    @Query("SELECT * FROM users WHERE user_id = :userId")
    LiveData<UserWithAccounts> getWithAccounts(long userId);

    @Transaction
    @Query("SELECT * FROM users WHERE user_id = :userId")
    LiveData<UserWithCards> getWithCards(long userId);
}
