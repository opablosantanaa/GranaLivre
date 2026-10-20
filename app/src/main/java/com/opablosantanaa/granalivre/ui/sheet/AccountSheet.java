package com.opablosantanaa.granalivre.ui.sheet;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.entity.AccountType;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Money;

public class AccountSheet extends BaseSheet {
    private static final int[] TYPE_LABELS = {R.string.account_corrente, R.string.account_poupanca,
            R.string.account_investimento, R.string.account_carteira};

    public AccountSheet() {
        super(R.layout.sheet_account);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        header(view, R.string.account_title);
        ChipGroup types = view.findViewById(R.id.types);
        AccountType[] values = AccountType.values();
        for (int i = 0; i < values.length; i++) {
            Chip chip = chip(types, getString(TYPE_LABELS[i]), i + 1);
            if (i == 0) chip.setChecked(true);
        }
        TextView error = view.findViewById(R.id.error);
        View save = view.findViewById(R.id.save);
        save.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            String initialText = text(view.findViewById(R.id.initial));
            long initial = initialText.isEmpty() ? 0 : Money.parse(initialText.replace("-", ""));
            if (initial < 0) {
                fail(error, null, getString(R.string.error_amount));
                return;
            }
            if (initialText.startsWith("-")) initial = -initial;
            Account account = new Account();
            account.setUser_id(userId());
            account.setAccount_bank_name(text(view.findViewById(R.id.bank)));
            account.setAccount_type(values[(int) checkedTag(types) - 1]);
            account.setAccount_initial_balance_cents(initial);
            save.setEnabled(false);
            vm().accounts.insert(account, new RepositoryCallback<Long>() {
                @Override
                public void onSuccess(Long result) {
                    done(R.string.account_saved);
                }

                @Override
                public void onError(Exception e) {
                    fail(error, save, e.getMessage());
                }
            });
        });
    }
}
