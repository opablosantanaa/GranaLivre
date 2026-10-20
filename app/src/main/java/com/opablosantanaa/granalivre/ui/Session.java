package com.opablosantanaa.granalivre.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class Session {
    private static final String PREFS = "grana_livre_session";
    private static final String KEY_USER = "user_id";
    private static final String KEY_THEME = "theme_mode";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private Session() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static long userId(Context context) {
        return prefs(context).getLong(KEY_USER, 0L);
    }

    public static void signIn(Context context, long userId) {
        prefs(context).edit().putLong(KEY_USER, userId).apply();
    }

    public static int theme(Context context) {
        return prefs(context).getInt(KEY_THEME, THEME_SYSTEM);
    }

    public static void setTheme(Context context, int mode) {
        prefs(context).edit().putInt(KEY_THEME, mode).apply();
        applyTheme(mode);
    }

    public static void applyTheme(int mode) {
        int night;
        if (mode == THEME_LIGHT) night = AppCompatDelegate.MODE_NIGHT_NO;
        else if (mode == THEME_DARK) night = AppCompatDelegate.MODE_NIGHT_YES;
        else night = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(night);
    }
}
