package com.opablosantanaa.granalivre.ui.screen;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.opablosantanaa.granalivre.R;

public class SplashScreen extends Screen {
    public SplashScreen() {
        super(R.layout.screen_splash);
    }

    @Override
    public int backdrop() {
        return BACKDROP_SPLASH;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View brand = view.findViewById(R.id.brand);
        View start = view.findViewById(R.id.start);
        view.post(() -> {
            if (!isAdded()) return;
            brand.setTranslationY(app().topInset());
            start.setTranslationY(-app().bottomInset());
        });

        start.setOnClickListener(v -> app().show(new SetupScreen()));
    }
}
