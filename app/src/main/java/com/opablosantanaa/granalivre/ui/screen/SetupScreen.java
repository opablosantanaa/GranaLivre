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

import java.time.LocalDate;

public class SetupScreen extends Screen {
    private LocalDate birth;

    private static void revealOnKeyboard(NestedScrollView scroll, View target) {
        scroll.postDelayed(() -> scroll.smoothScrollTo(0, target.getBottom()), 250);
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
