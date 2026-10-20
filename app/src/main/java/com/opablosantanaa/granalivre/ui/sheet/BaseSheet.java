package com.opablosantanaa.granalivre.ui.sheet;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.opablosantanaa.granalivre.App;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.ui.MainViewModel;

public abstract class BaseSheet extends BottomSheetDialogFragment {
    private final int layout;

    protected BaseSheet(int layout) {
        this.layout = layout;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(layout, container, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        return dialog;
    }

    private boolean holding;

    @Override
    public void onStart() {
        super.onStart();
        if (!holding && getActivity() instanceof App) {
            holding = true;
            ((App) getActivity()).holdBackdrop();
        }
    }

    @Override
    public void onStop() {
        if (holding && getActivity() instanceof App) {
            holding = false;
            ((App) getActivity()).releaseBackdrop();
        }
        super.onStop();
    }

    protected void header(View view, int title) {
        header(view, getString(title));
    }

    protected void header(View view, String title) {
        ((TextView) view.findViewById(R.id.sheet_title)).setText(title);
        view.findViewById(R.id.sheet_close).setOnClickListener(v -> dismiss());
    }

    protected MainViewModel vm() {
        return ((App) requireActivity()).viewModel();
    }

    protected long userId() {
        return vm().getUserId();
    }

    protected int color(int res) {
        return ContextCompat.getColor(requireContext(), res);
    }

    protected Chip chip(ChipGroup group, String text, long id) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip, group, false);
        chip.setText(text);
        chip.setId(View.generateViewId());
        chip.setTag(id);
        group.addView(chip);
        return chip;
    }

    protected static long checkedTag(ChipGroup group) {
        int id = group.getCheckedChipId();
        if (id == View.NO_ID) return 0;
        Object tag = group.findViewById(id).getTag();
        return tag instanceof Long ? (Long) tag : 0;
    }

    protected static String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    protected void done(int message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        dismiss();
    }

    protected void fail(TextView error, View button, String message) {
        if (!isAdded()) return;
        if (button != null) button.setEnabled(true);
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }
}
