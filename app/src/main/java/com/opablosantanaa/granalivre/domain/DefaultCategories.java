package com.opablosantanaa.granalivre.domain;

import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.util.ArrayList;
import java.util.List;

public final class DefaultCategories {
    private DefaultCategories() {}

    public static List<Category> create(long userId) {
        List<Category> categories = new ArrayList<>();
        add(categories, userId, "Alimentação", TransactionType.DESPESA, 0xFFE57373);
        add(categories, userId, "Transporte", TransactionType.DESPESA, 0xFF64B5F6);
        add(categories, userId, "Moradia", TransactionType.DESPESA, 0xFFA1887F);
        add(categories, userId, "Contas e serviços", TransactionType.DESPESA, 0xFFFFB74D);
        add(categories, userId, "Saúde", TransactionType.DESPESA, 0xFF81C784);
        add(categories, userId, "Educação", TransactionType.DESPESA, 0xFF9575CD);
        add(categories, userId, "Lazer", TransactionType.DESPESA, 0xFFF06292);
        add(categories, userId, "Compras", TransactionType.DESPESA, 0xFF4DB6AC);
        add(categories, userId, OTHER_EXPENSES, TransactionType.DESPESA, 0xFF90A4AE);
        add(categories, userId, "Salário", TransactionType.RECEITA, 0xFF43A047);
        add(categories, userId, "Investimentos", TransactionType.RECEITA, 0xFF1E88E5);
        add(categories, userId, "Presentes", TransactionType.RECEITA, 0xFFD81B60);
        add(categories, userId, OTHER_INCOMES, TransactionType.RECEITA, 0xFF757575);
        return categories;
    }

    public static final String OTHER_EXPENSES = "Outras despesas";
    public static final String OTHER_INCOMES = "Outras receitas";

    public static String fallbackName(TransactionType type) {
        return type == TransactionType.RECEITA ? OTHER_INCOMES : OTHER_EXPENSES;
    }

    public static Category fallback(long userId, TransactionType type) {
        List<Category> one = new ArrayList<>();
        add(one, userId, fallbackName(type), type, type == TransactionType.RECEITA ? 0xFF757575 : 0xFF90A4AE);
        return one.get(0);
    }

    private static void add(List<Category> categories, long userId, String name, TransactionType type, int color) {
        Category category = new Category();
        category.setUser_id(userId);
        category.setCategory_name(name);
        category.setCategory_type(type);
        category.setCategory_color(color);
        categories.add(category);
    }
}
