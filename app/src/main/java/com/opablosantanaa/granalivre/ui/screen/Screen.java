package com.opablosantanaa.granalivre.ui.screen;

import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.opablosantanaa.granalivre.App;
import com.opablosantanaa.granalivre.ui.MainViewModel;

public abstract class Screen extends Fragment {
    public static final int BACKDROP_SPLASH = 0;
    public static final int BACKDROP_DARK = 1;
    public static final int BACKDROP_LIGHT = 2;

    public Screen(int layout) {
        super(layout);
    }

    public int tab() {
        return -1;
    }

    public int backdrop() {
        return BACKDROP_DARK;
    }

    public boolean inkSheet() {
        return false;
    }

    protected App app() {
        return (App) requireActivity();
    }

    protected MainViewModel vm() {
        return app().viewModel();
    }

    protected int color(int res) {
        return ContextCompat.getColor(requireContext(), res);
    }

    protected int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected void padTop(View view, int extraDp) {
        view.post(() -> {
            if (!isAdded()) return;
            view.setPadding(view.getPaddingLeft(), app().topInset() + dp(extraDp),
                    view.getPaddingRight(), view.getPaddingBottom());
        });
    }

    protected void padBottomForTabs(View view, int extraDp) {
        view.post(() -> {
            if (!isAdded()) return;
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), app().bottomSpace() + dp(extraDp));
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        app().onScreen(this);
    }
}
