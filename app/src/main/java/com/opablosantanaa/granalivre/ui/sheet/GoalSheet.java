package com.opablosantanaa.granalivre.ui.sheet;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Goal;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.Money;

import java.time.LocalDate;
import java.util.List;

public class GoalSheet extends BaseSheet {
    private static final String ARG_GOAL = "goal_id";
    private LocalDate deadline;

    public GoalSheet() {
        super(R.layout.sheet_goal);
    }

    public static GoalSheet create() {
        return new GoalSheet();
    }

    public static GoalSheet edit(Goal goal) {
        GoalSheet sheet = new GoalSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_GOAL, goal.getGoal_id());
        sheet.setArguments(args);
        return sheet;
    }

    private Goal findGoal() {
        long id = getArguments() == null ? 0 : getArguments().getLong(ARG_GOAL, 0);
        List<Goal> goals = vm().goalList.getValue();
        if (id == 0 || goals == null) return null;
        for (Goal goal : goals) if (goal.getGoal_id() == id) return goal;
        return null;
    }

    private <T> RepositoryCallback<T> callback(TextView error, View button, int message) {
        return new RepositoryCallback<T>() {
            @Override
            public void onSuccess(T result) {
                done(message);
            }

            @Override
            public void onError(Exception e) {
                fail(error, button, e.getMessage());
            }
        };
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Goal goal = findGoal();
        TextView error = view.findViewById(R.id.error);
        MaterialButton save = view.findViewById(R.id.save);

        if (goal == null) {
            header(view, R.string.goal_title);
            save.setText(R.string.goal_save);
            MaterialButton deadlineButton = view.findViewById(R.id.deadline);
            deadlineButton.setOnClickListener(v -> {
                LocalDate initial = deadline != null ? deadline : LocalDate.now().plusMonths(6);
                new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
                    deadline = LocalDate.of(y, m + 1, d);
                    deadlineButton.setText(Dates.shortDate(deadline));
                }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
            });
            save.setOnClickListener(v -> {
                error.setVisibility(View.GONE);
                long target = Money.parse(text(view.findViewById(R.id.target)));
                String savedText = text(view.findViewById(R.id.saved));
                long saved = savedText.isEmpty() ? 0 : Money.parse(savedText);
                if (target <= 0 || saved < 0) {
                    fail(error, null, getString(R.string.error_amount));
                    return;
                }
                Goal created = new Goal();
                created.setUser_id(userId());
                created.setGoal_name(text(view.findViewById(R.id.name)));
                created.setGoal_target_cents(target);
                created.setGoal_saved_cents(saved);
                created.setGoal_deadline(deadline);
                save.setEnabled(false);
                vm().goals.insert(created, callback(error, save, R.string.goal_saved_toast));
            });
            return;
        }

        header(view, goal.getGoal_name());
        view.findViewById(R.id.create_group).setVisibility(View.GONE);
        view.findViewById(R.id.edit_group).setVisibility(View.VISIBLE);
        ((TextView) view.findViewById(R.id.summary)).setText(getString(R.string.goals_progress,
                Money.plain(goal.getGoal_saved_cents()), Money.plain(goal.getGoal_target_cents())));
        save.setText(R.string.goal_add_save);
        save.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            long add = Money.parse(text(view.findViewById(R.id.add)));
            if (add <= 0) {
                fail(error, null, getString(R.string.error_amount));
                return;
            }
            goal.setGoal_saved_cents(goal.getGoal_saved_cents() + add);
            save.setEnabled(false);
            vm().goals.update(goal, callback(error, save, R.string.goal_added));
        });
        View delete = view.findViewById(R.id.delete);
        delete.setVisibility(View.VISIBLE);
        delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.goal_delete)
                .setMessage(getString(R.string.goal_delete_confirm, goal.getGoal_name()))
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(R.string.goal_delete, (d, w) ->
                        vm().goals.delete(goal, callback(error, delete, R.string.goal_deleted)))
                .show());
    }
}
