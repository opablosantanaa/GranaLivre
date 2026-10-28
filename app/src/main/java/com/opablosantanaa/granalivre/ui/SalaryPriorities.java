package com.opablosantanaa.granalivre.ui;

import android.content.Context;
import android.content.SharedPreferences;

import com.opablosantanaa.granalivre.domain.SalaryPlanner;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class SalaryPriorities {
    private static final String PREFS = "grana_livre_salary_priorities";

    private SalaryPriorities() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(long userId, Long categoryId) {
        return userId + ":" + (categoryId == null ? "none" : categoryId);
    }

    public static Map<Long, SalaryPlanner.Priority> load(Context context, long userId, Collection<Long> categoryIds) {
        SharedPreferences prefs = prefs(context);
        Map<Long, SalaryPlanner.Priority> priorities = new HashMap<>();
        for (Long categoryId : categoryIds) {
            String stored = prefs.getString(key(userId, categoryId), null);
            if (stored == null) continue;
            try {
                priorities.put(categoryId, SalaryPlanner.Priority.valueOf(stored));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return priorities;
    }

    public static void save(Context context, long userId, Long categoryId, SalaryPlanner.Priority priority) {
        prefs(context).edit().putString(key(userId, categoryId), priority.name()).apply();
    }
}
