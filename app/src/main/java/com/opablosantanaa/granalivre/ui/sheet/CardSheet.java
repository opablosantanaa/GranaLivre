package com.opablosantanaa.granalivre.ui.sheet;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.CardBrand;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Money;

import java.util.List;

public class CardSheet extends BaseSheet {
    public CardSheet() {
        super(R.layout.sheet_card);
    }

    private static int day(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        header(view, R.string.card_title);
        ChipGroup brands = view.findViewById(R.id.brands);
        CardBrand[] values = CardBrand.values();
        for (int i = 0; i < values.length; i++) {
            String name = values[i] == CardBrand.OUTRA ? "Outra"
                    : values[i].name().charAt(0) + values[i].name().substring(1).toLowerCase(Money.BR);
            Chip chip = chip(brands, name, i + 1);
            if (i == 0) chip.setChecked(true);
        }
        ChipGroup accounts = view.findViewById(R.id.accounts);
        List<AccountWithBalance> list = vm().accountList.getValue();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                Chip chip = chip(accounts, list.get(i).account.getAccount_bank_name(), list.get(i).account.getAccount_id());
                if (i == 0) chip.setChecked(true);
            }
        }

        TextView error = view.findViewById(R.id.error);
        View save = view.findViewById(R.id.save);
        save.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            String digits = text(view.findViewById(R.id.digits));
            int closing = day(text(view.findViewById(R.id.closing)));
            int due = day(text(view.findViewById(R.id.due)));
            long limit = Money.parse(text(view.findViewById(R.id.limit)));
            if (!digits.matches("\\d{4}")) {
                fail(error, null, getString(R.string.error_digits));
                return;
            }
            if (closing < 1 || closing > 31 || due < 1 || due > 31) {
                fail(error, null, getString(R.string.error_day));
                return;
            }
            if (limit <= 0) {
                fail(error, null, getString(R.string.error_amount));
                return;
            }
            Card card = new Card();
            card.setUser_id(userId());
            card.setCard_nickname(text(view.findViewById(R.id.nickname)));
            card.setCard_brand(values[(int) checkedTag(brands) - 1]);
            card.setCard_last_digits(digits);
            card.setCard_limit_cents(limit);
            card.setCard_closing_day(closing);
            card.setCard_due_day(due);
            long account = checkedTag(accounts);
            card.setCard_payment_account_id(account > 0 ? account : null);
            save.setEnabled(false);
            vm().cards.insert(card, new RepositoryCallback<Long>() {
                @Override
                public void onSuccess(Long result) {
                    done(R.string.card_saved);
                }

                @Override
                public void onError(Exception e) {
                    fail(error, save, e.getMessage());
                }
            });
        });
    }
}
