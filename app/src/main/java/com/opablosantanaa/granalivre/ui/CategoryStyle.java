package com.opablosantanaa.granalivre.ui;

import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.text.Normalizer;

public final class CategoryStyle {
    public static final int FALLBACK_COLOR = 0xFF90A4AE;

    private CategoryStyle() {}

    public static int icon(Category category, TransactionType type) {
        if (category == null) {
            return type == TransactionType.RECEITA ? R.drawable.ic_cat_coin : R.drawable.ic_cat_other;
        }
        String name = Normalizer.normalize(category.getCategory_name(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Money.BR);
        if (name.startsWith("aliment")) return R.drawable.ic_cat_food;
        if (name.startsWith("transporte")) return R.drawable.ic_cat_transport;
        if (name.startsWith("moradia")) return R.drawable.ic_cat_home;
        if (name.startsWith("contas")) return R.drawable.ic_cat_bills;
        if (name.startsWith("saude")) return R.drawable.ic_cat_health;
        if (name.startsWith("educa")) return R.drawable.ic_cat_education;
        if (name.startsWith("lazer")) return R.drawable.ic_cat_leisure;
        if (name.startsWith("compras")) return R.drawable.ic_cat_shopping;
        if (name.startsWith("salario")) return R.drawable.ic_cat_salary;
        if (name.startsWith("invest")) return R.drawable.ic_cat_invest;
        if (name.startsWith("presente")) return R.drawable.ic_cat_gift;
        return category.getCategory_type() == TransactionType.RECEITA ? R.drawable.ic_cat_coin : R.drawable.ic_cat_other;
    }

    public static int color(Category category) {
        return category == null || category.getCategory_color() == 0 ? FALLBACK_COLOR : category.getCategory_color();
    }

    public static int tint(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
