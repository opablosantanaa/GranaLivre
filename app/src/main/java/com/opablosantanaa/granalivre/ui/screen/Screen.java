package com.opablosantanaa.granalivre.ui.screen;

import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

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

    private final Map<String, Runnable> pendingRefreshes = new LinkedHashMap<>();
    private boolean refreshPosted;

    protected void later(String key, Runnable task) {
        pendingRefreshes.put(key, task);
        flushLater();
    }

    private void flushLater() {
        View view = getView();
        if (refreshPosted || view == null || isHidden() || pendingRefreshes.isEmpty()) return;
        refreshPosted = true;
        view.postOnAnimation(() -> {
            refreshPosted = false;
            if (!isAdded() || getView() == null) {
                pendingRefreshes.clear();
                return;
            }
            if (isHidden()) return;
            Map<String, Runnable> snapshot = new LinkedHashMap<>(pendingRefreshes);
            pendingRefreshes.clear();
            for (Runnable t : snapshot.values()) t.run();
        });
    }

    @Override
    public void onDestroyView() {
        pendingRefreshes.clear();
        refreshPosted = false;
        super.onDestroyView();
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
        if (!isHidden()) app().onScreen(this);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) return;
        if (isResumed()) app().onScreen(this);
        flushLater();
    }
}
