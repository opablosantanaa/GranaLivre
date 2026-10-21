package com.opablosantanaa.granalivre.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.util.List;

@Dao
public interface CategoryDao {
    @Insert
    long insert(Category category);

    @Insert
    List<Long> insertAll(List<Category> categories);

    @Update
    void update(Category category);

    @Delete
    void delete(Category category);

    @Query("SELECT * FROM categories WHERE category_id = :categoryId")
    Category findById(long categoryId);

    @Query("SELECT * FROM categories WHERE user_id = :userId ORDER BY category_name")
    LiveData<List<Category>> getByUser(long userId);

    @Query("SELECT * FROM categories WHERE user_id = :userId ORDER BY category_name")
    List<Category> findByUser(long userId);

    @Query("SELECT * FROM categories WHERE user_id = :userId AND category_type = :type "
            + "AND category_parent_id IS NULL ORDER BY category_name")
    LiveData<List<Category>> getRootsByType(long userId, TransactionType type);

    @Query("SELECT * FROM categories WHERE user_id = :userId AND category_type = :type "
            + "AND category_parent_id IS NULL AND category_name = :name LIMIT 1")
    Category findRootByName(long userId, TransactionType type, String name);

    @Query("SELECT COUNT(*) FROM transactions WHERE user_id = :userId AND category_id IS NULL "
            + "AND transaction_type = :type")
    int countOrphanTransactions(long userId, TransactionType type);

    @Query("SELECT COUNT(*) FROM recurrences WHERE user_id = :userId AND category_id IS NULL "
            + "AND recurrence_type = :type")
    int countOrphanRecurrences(long userId, TransactionType type);

    @Query("UPDATE transactions SET category_id = :categoryId WHERE user_id = :userId "
            + "AND category_id IS NULL AND transaction_type = :type")
    void adoptOrphanTransactions(long userId, TransactionType type, long categoryId);

    @Query("UPDATE recurrences SET category_id = :categoryId WHERE user_id = :userId "
            + "AND category_id IS NULL AND recurrence_type = :type")
    void adoptOrphanRecurrences(long userId, TransactionType type, long categoryId);

    @Query("SELECT * FROM categories WHERE category_parent_id = :parentId ORDER BY category_name")
    LiveData<List<Category>> getSubcategories(long parentId);
}
