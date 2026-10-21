package com.opablosantanaa.granalivre.ui.sheet;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.entity.AccountType;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Money;

import java.math.BigDecimal;
import java.util.List;

public class AccountSheet extends BaseSheet {
    private static final String ARG_ID = "account_id";
    private static final int[] TYPE_LABELS = {R.string.account_corrente, R.string.account_poupanca,
            R.string.account_investimento, R.string.account_carteira};

    public AccountSheet() {
        super(R.layout.sheet_account);
    }

    public static AccountSheet edit(long accountId) {
        AccountSheet sheet = new AccountSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_ID, accountId);
        sheet.setArguments(args);
        return sheet;
    }

    private AccountWithBalance findAccount() {
        long id = getArguments() == null ? 0 : getArguments().getLong(ARG_ID, 0);
        List<AccountWithBalance> list = vm().accountList.getValue();
        if (id == 0 || list == null) return null;
        for (AccountWithBalance a : list) if (a.account.getAccount_id() == id) return a;
        return null;
    }

    private static String editable(long cents) {
        return new BigDecimal(cents).movePointLeft(2).toPlainString().replace('.', ',');
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AccountWithBalance editing = findAccount();
        header(view, editing == null ? R.string.account_title : R.string.account_edit_title);
        ChipGroup types = view.findViewById(R.id.types);
        AccountType[] values = AccountType.values();
        for (int i = 0; i < values.length; i++) {
            Chip chip = chip(types, getString(TYPE_LABELS[i]), i + 1);
            boolean selected = editing == null ? i == 0 : editing.account.getAccount_type() == values[i];
            if (selected) chip.setChecked(true);
        }
        if (editing != null) {
            ((TextInputEditText) view.findViewById(R.id.bank)).setText(editing.account.getAccount_bank_name());
            // o campo mostra o saldo atual; ao salvar, a diferença ajusta o saldo inicial da conta
            ((TextInputEditText) view.findViewById(R.id.initial)).setText(editable(editing.balanceCents));
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
            Account account = editing == null ? new Account() : editing.account;
            account.setUser_id(userId());
            account.setAccount_bank_name(text(view.findViewById(R.id.bank)));
            account.setAccount_type(values[(int) checkedTag(types) - 1]);
            if (editing == null) {
                account.setAccount_initial_balance_cents(initial);
            } else {
                account.setAccount_initial_balance_cents(
                        account.getAccount_initial_balance_cents() + initial - editing.balanceCents);
            }
            save.setEnabled(false);
            if (editing == null) {
                vm().accounts.insert(account, callback(error, save, R.string.account_saved));
            } else {
                vm().accounts.update(account, callback(error, save, R.string.account_saved));
            }
        });

        View delete = view.findViewById(R.id.delete);
        if (editing == null) return;
        delete.setVisibility(View.VISIBLE);
        delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.account_delete)
                .setMessage(getString(R.string.account_delete_confirm, editing.account.getAccount_bank_name()))
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(R.string.account_delete, (d, w) -> {
                    delete.setEnabled(false);
                    vm().accounts.delete(editing.account, callback(error, delete, R.string.account_deleted));
                })
                .show());
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
}
