package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.isBlank;
import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.CategoryDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.util.List;
import java.util.concurrent.Executor;

public class CategoryRepository extends BaseRepository {
    private final CategoryDao categoryDao;

    public CategoryRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public CategoryRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        categoryDao = database.categoryDao();
    }

    public LiveData<List<Category>> getByUser(long userId) {
        return categoryDao.getByUser(userId);
    }

    public LiveData<List<Category>> getRootsByType(long userId, TransactionType type) {
        return categoryDao.getRootsByType(userId, type);
    }

    public LiveData<List<Category>> getSubcategories(long parentId) {
        return categoryDao.getSubcategories(parentId);
    }

    public void insert(Category category, RepositoryCallback<Long> callback) {
        execute(() -> {
            validate(category);
            return categoryDao.insert(category);
        }, callback);
    }

    public void update(Category category, RepositoryCallback<Void> callback) {
        execute(() -> {
            validate(category);
            categoryDao.update(category);
            return null;
        }, callback);
    }

    public void delete(Category category, RepositoryCallback<Void> callback) {
        execute(() -> {
            categoryDao.delete(category);
            return null;
        }, callback);
    }

    /** Move lançamentos e recorrências sem categoria para "Outras despesas" / "Outras receitas". */
    public void adoptOrphans(long userId) {
        execute(() -> {
            database.runInTransaction(() -> {
                for (TransactionType type : TransactionType.values()) {
                    if (categoryDao.countOrphanTransactions(userId, type) == 0
                            && categoryDao.countOrphanRecurrences(userId, type) == 0) continue;
                    long fallback = TransactionRules.fallbackCategory(categoryDao, userId, type);
                    categoryDao.adoptOrphanTransactions(userId, type, fallback);
                    categoryDao.adoptOrphanRecurrences(userId, type, fallback);
                }
            });
            return null;
        }, null);
    }

    private void validate(Category category) {
        category.setCategory_name(category.getCategory_name().trim());
        require(!isBlank(category.getCategory_name()), "Informe o nome da categoria");
        Long parentId = category.getCategory_parent_id();
        if (parentId == null) {
            return;
        }
        require(parentId != category.getCategory_id(), "A categoria não pode ser pai de si mesma");
        Category parent = categoryDao.findById(parentId);
        require(parent != null && parent.getUser_id() == category.getUser_id(), "Categoria pai inválida");
        require(parent.getCategory_parent_id() == null, "Subcategorias não podem ter subcategorias");
        require(parent.getCategory_type() == category.getCategory_type(),
                "A subcategoria deve ter o mesmo tipo da categoria pai");
    }
}
