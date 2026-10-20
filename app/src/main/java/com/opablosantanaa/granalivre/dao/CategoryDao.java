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

    @Query("SELECT * FROM categories WHERE category_parent_id = :parentId ORDER BY category_name")
    LiveData<List<Category>> getSubcategories(long parentId);
}
