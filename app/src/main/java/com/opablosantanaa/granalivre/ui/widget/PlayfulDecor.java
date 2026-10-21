package com.opablosantanaa.granalivre.ui.widget;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.opablosantanaa.granalivre.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Decoração lúdica das telas de entrada: selos redondos com ícones do app e brilhos de 4 pontas espalhados
 * num palco, flutuando devagar. As posições são frações do palco, então o desenho se adapta ao tamanho da tela.
 * Tudo é decorativo e fica fora da acessibilidade.
 */
public final class PlayfulDecor {
    private final FrameLayout stage;
    private final float density;
    private final List<ValueAnimator> animators = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();

    private static final class Item {
        final View view;
        final float x;
        final float y;
        final int size;

        Item(View view, float x, float y, int size) {
            this.view = view;
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }

    public PlayfulDecor(FrameLayout stage) {
        this.stage = stage;
        density = stage.getResources().getDisplayMetrics().density;
        stage.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place());
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    /** Selo redondo com degradê e um ícone branco; (x, y) é o centro, em fração do palco. */
    public PlayfulDecor badge(float x, float y, int sizeDp, int icon, int from, int to) {
        FrameLayout badge = new FrameLayout(stage.getContext());
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        circle.setShape(GradientDrawable.OVAL);
        circle.setStroke(dp(3), 0x33FFFFFF);
        badge.setBackground(circle);
        badge.setElevation(dp(6));
        ImageView image = new ImageView(stage.getContext());
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        int iconSize = Math.round(dp(sizeDp) * 0.46f);
        badge.addView(image, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
        add(badge, x, y, sizeDp);
        return this;
    }

    public PlayfulDecor sparkle(float x, float y, int sizeDp) {
        ImageView star = new ImageView(stage.getContext());
        star.setImageResource(R.drawable.ic_sparkle);
        add(star, x, y, sizeDp);
        return this;
    }

    private void add(View view, float x, float y, int sizeDp) {
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = dp(sizeDp);
        stage.addView(view, 0, new FrameLayout.LayoutParams(size, size));
        items.add(new Item(view, x, y, size));
    }

    private void place() {
        int w = stage.getWidth();
        int h = stage.getHeight();
        for (Item item : items) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) item.view.getLayoutParams();
            int left = Math.round(item.x * w - item.size / 2f);
            int top = Math.round(item.y * h - item.size / 2f);
            if (params.leftMargin != left || params.topMargin != top) {
                params.leftMargin = left;
                params.topMargin = top;
                item.view.setLayoutParams(params);
            }
        }
    }

    /** Entrada com "pop" escalonado e, depois, flutuação contínua (selos sobem e descem, brilhos pulsam). */
    public void start() {
        stop();
        for (int i = 0; i < items.size(); i++) {
            View view = items.get(i).view;
            boolean star = view instanceof ImageView;
            view.setScaleX(0f);
            view.setScaleY(0f);
            view.animate().scaleX(1f).scaleY(1f).setStartDelay(80L * i).setDuration(450)
                    .setInterpolator(new OvershootInterpolator(2f)).start();

            ValueAnimator wave = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
            wave.setDuration(2600 + 450L * (i % 4));
            wave.setRepeatCount(ValueAnimator.INFINITE);
            wave.setInterpolator(new LinearInterpolator());
            float phase = i * 1.3f;
            float amplitude = dp(star ? 0 : 7);
            wave.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue() + phase;
                if (star) {
                    float s = 0.8f + 0.2f * (float) Math.sin(t);
                    view.setScaleX(s);
                    view.setScaleY(s);
                    view.setRotation(10f * (float) Math.sin(t / 2));
                } else {
                    view.setTranslationY(amplitude * (float) Math.sin(t));
                    view.setRotation(4f * (float) Math.sin(t + 1f));
                }
            });
            wave.setStartDelay(80L * i + 450);
            wave.start();
            animators.add(wave);
        }
    }

    public void stop() {
        for (ValueAnimator animator : animators) animator.cancel();
        animators.clear();
    }
}
