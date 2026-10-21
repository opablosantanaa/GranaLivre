package com.opablosantanaa.granalivre.ui.screen;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.FinanceAdvisor;
import com.opablosantanaa.granalivre.ui.widget.CoinView;
import com.opablosantanaa.granalivre.ui.widget.PlayfulDecor;

import java.time.LocalDate;

public class SetupScreen extends Screen {
    private LocalDate birth;
    private PlayfulDecor decor;
    private PlayfulDecor sky;
    private int step;

    private static void revealOnKeyboard(NestedScrollView scroll, View target) {
        scroll.postDelayed(() -> scroll.smoothScrollTo(0, target.getBottom()), 250);
    }

    /** O mascote reage ao cadastro: curioso até saber o nome, feliz depois; pula quando a fala muda. */
    private void updateBubble(View view) {
        TextInputEditText name = view.findViewById(R.id.name);
        String typed = name.getText() == null ? "" : name.getText().toString().trim();
        String first = typed.isEmpty() ? "" : typed.split("\\s+")[0];
        int step = first.isEmpty() ? 0 : birth == null ? 1 : 2;
        String text = step == 0 ? getString(R.string.setup_title)
                : step == 1 ? getString(R.string.setup_bubble_named, first)
                : getString(R.string.setup_bubble_ready, first);
        CoinView mascot = view.findViewById(R.id.mascot);
        mascot.setCurious(step == 0);
        mascot.setMood(FinanceAdvisor.Mood.PRAISE, false);
        ((TextView) view.findViewById(R.id.bubble)).setText(text);
        if (step != this.step) mascot.hop();
        this.step = step;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (decor != null) decor.start();
        if (sky != null) sky.start();
    }

    @Override
    public void onStop() {
        if (decor != null) decor.stop();
        if (sky != null) sky.stop();
        super.onStop();
    }

    public SetupScreen() {
        super(R.layout.screen_setup);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        padTop(view.findViewById(R.id.top), 8);
        View sheet = view.findViewById(R.id.sheet);
        view.post(() -> {
            if (!isAdded()) return;
            sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(),
                    sheet.getPaddingRight(), dp(24) + app().bottomInset());
        });

        decor = new PlayfulDecor(view.findViewById(R.id.setup_stage))
                .sparkle(0.04f, 0.10f, 22)
                .sparkle(0.30f, 0.92f, 18)
                .sparkle(0.96f, 0.06f, 26);
        sky = new PlayfulDecor(view.findViewById(R.id.setup_sky))
                .badge(0.18f, 0.30f, 52, R.drawable.ic_cat_salary, 0xFF22A06B, 0xFF2EC4B6)
                .badge(0.78f, 0.22f, 56, R.drawable.ic_target, 0xFFFFB547, 0xFFF06292)
                .badge(0.62f, 0.70f, 44, R.drawable.ic_card, 0xFF64B5F6, 0xFF9575CD)
                .sparkle(0.40f, 0.18f, 20)
                .sparkle(0.12f, 0.78f, 24);
        CoinView mascot = view.findViewById(R.id.mascot);
        mascot.setCurious(true);
        mascot.setOnClickListener(v -> mascot.hop());

        TextInputLayout birthLayout = view.findViewById(R.id.birth_layout);
        TextInputLayout nameLayout = view.findViewById(R.id.name_layout);
        TextInputEditText name = view.findViewById(R.id.name);
        NestedScrollView scroll = (NestedScrollView) view;
        name.setOnFocusChangeListener((v, focused) -> {
            if (focused) revealOnKeyboard(scroll, view.findViewById(R.id.sheet));
        });
        name.setOnClickListener(v -> revealOnKeyboard(scroll, view.findViewById(R.id.sheet)));
        TextInputEditText birthField = view.findViewById(R.id.birth);
        TextView error = view.findViewById(R.id.error);
        MaterialButton submit = view.findViewById(R.id.submit);

        View.OnClickListener pickBirth = v -> {
            LocalDate initial = birth != null ? birth : LocalDate.now().minusYears(25);
            new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
                birth = LocalDate.of(y, m + 1, d);
                birthField.setText(Dates.shortDate(birth));
                updateBubble(view);
            }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
        };
        birthField.setOnClickListener(pickBirth);
        birthLayout.setEndIconOnClickListener(pickBirth);
        name.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            v.clearFocus();
            InputMethodManager keyboard = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(v.getWindowToken(), 0);
            if (birth == null) pickBirth.onClick(birthField);
            return true;
        });
        name.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) { updateBubble(view); }
        });
        view.findViewById(R.id.back).setOnClickListener(v -> app().show(new SplashScreen()));

        submit.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            submit.setEnabled(false);
            String typed = name.getText() == null ? "" : name.getText().toString();
            vm().users.createLocalProfile(typed, birth, new RepositoryCallback<Long>() {
                @Override
                public void onSuccess(Long userId) {
                    app().signedIn(userId);
                }

                @Override
                public void onError(Exception e) {
                    if (!isAdded()) return;
                    submit.setEnabled(true);
                    error.setText(e.getMessage());
                    error.setVisibility(View.VISIBLE);
                }
            });
        });
    }
}
