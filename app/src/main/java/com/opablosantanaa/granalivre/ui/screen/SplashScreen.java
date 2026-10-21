package com.opablosantanaa.granalivre.ui.screen;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.ui.FinanceAdvisor;
import com.opablosantanaa.granalivre.ui.widget.CoinView;
import com.opablosantanaa.granalivre.ui.widget.PlayfulDecor;

public class SplashScreen extends Screen {
    private static final long HOP_EVERY_MS = 3200;

    private PlayfulDecor decor;
    private Runnable hop;

    public SplashScreen() {
        super(R.layout.screen_splash);
    }

    @Override
    public int backdrop() {
        return BACKDROP_SPLASH;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View stage = view.findViewById(R.id.stage);
        view.post(() -> {
            if (!isAdded()) return;
            view.setPadding(0, app().topInset(), 0, app().bottomInset());
        });

        // selos com ícones do app, nas cores das categorias, ao redor do mascote
        decor = new PlayfulDecor((FrameLayout) stage)
                .badge(0.20f, 0.16f, 72, R.drawable.ic_cat_salary, 0xFF22A06B, 0xFF2EC4B6)
                .badge(0.80f, 0.13f, 64, R.drawable.ic_target, 0xFFFFB547, 0xFFF06292)
                .badge(0.09f, 0.50f, 60, R.drawable.ic_card, 0xFF64B5F6, 0xFF9575CD)
                .badge(0.91f, 0.46f, 60, R.drawable.ic_cat_food, 0xFFE57373, 0xFFFFB74D)
                .badge(0.17f, 0.86f, 56, R.drawable.ic_cat_invest, 0xFF1E88E5, 0xFF4FD1C5)
                .badge(0.84f, 0.82f, 66, R.drawable.ic_repeat, 0xFF4DB6AC, 0xFF0B6E69)
                .sparkle(0.40f, 0.12f, 22)
                .sparkle(0.79f, 0.29f, 28)
                .sparkle(0.24f, 0.66f, 30)
                .sparkle(0.70f, 0.95f, 26);

        CoinView mascot = view.findViewById(R.id.mascot);
        mascot.setMood(FinanceAdvisor.Mood.PRAISE, false);
        mascot.setOnClickListener(v -> mascot.hop());
        hop = () -> {
            mascot.hop();
            mascot.postDelayed(hop, HOP_EVERY_MS);
        };

        view.findViewById(R.id.start).setOnClickListener(v -> app().show(new SetupScreen()));
    }

    @Override
    public void onStart() {
        super.onStart();
        View view = getView();
        if (view == null) return;
        decor.start();
        view.findViewById(R.id.mascot).postDelayed(hop, 900);
    }

    @Override
    public void onStop() {
        View view = getView();
        if (view != null) view.findViewById(R.id.mascot).removeCallbacks(hop);
        decor.stop();
        super.onStop();
    }
}
