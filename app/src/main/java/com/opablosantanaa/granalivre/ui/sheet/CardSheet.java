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
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.CardBrand;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Money;

import java.math.BigDecimal;
import java.util.List;

public class CardSheet extends BaseSheet {
    private static final String ARG_ID = "card_id";

    public CardSheet() {
        super(R.layout.sheet_card);
    }

    public static CardSheet edit(long cardId) {
        CardSheet sheet = new CardSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_ID, cardId);
        sheet.setArguments(args);
        return sheet;
    }

    private Card findCard() {
        long id = getArguments() == null ? 0 : getArguments().getLong(ARG_ID, 0);
        List<Card> cards = vm().cardList.getValue();
        if (id == 0 || cards == null) return null;
        for (Card card : cards) if (card.getCard_id() == id) return card;
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

    private static int day(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Card editing = findCard();
        header(view, editing == null ? R.string.card_title : R.string.card_edit_title);
        ChipGroup brands = view.findViewById(R.id.brands);
        CardBrand[] values = CardBrand.values();
        for (int i = 0; i < values.length; i++) {
            String name = values[i] == CardBrand.OUTRA ? "Outra"
                    : values[i].name().charAt(0) + values[i].name().substring(1).toLowerCase(Money.BR);
            Chip chip = chip(brands, name, i + 1);
            if (editing == null ? i == 0 : editing.getCard_brand() == values[i]) chip.setChecked(true);
        }
        ChipGroup accounts = view.findViewById(R.id.accounts);
        List<AccountWithBalance> list = vm().accountList.getValue();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                long id = list.get(i).account.getAccount_id();
                Chip chip = chip(accounts, list.get(i).account.getAccount_bank_name(), id);
                Long payment = editing == null ? null : editing.getCard_payment_account_id();
                if (editing == null ? i == 0 : payment != null && payment == id) chip.setChecked(true);
            }
        }

        if (editing != null) {
            ((TextInputEditText) view.findViewById(R.id.nickname)).setText(editing.getCard_nickname());
            ((TextInputEditText) view.findViewById(R.id.digits)).setText(editing.getCard_last_digits());
            ((TextInputEditText) view.findViewById(R.id.limit)).setText(new BigDecimal(editing.getCard_limit_cents())
                    .movePointLeft(2).toPlainString().replace('.', ','));
            ((TextInputEditText) view.findViewById(R.id.closing)).setText(String.valueOf(editing.getCard_closing_day()));
            ((TextInputEditText) view.findViewById(R.id.due)).setText(String.valueOf(editing.getCard_due_day()));
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
            Card card = editing == null ? new Card() : editing;
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
            if (editing == null) vm().cards.insert(card, callback(error, save, R.string.card_saved));
            else vm().cards.update(card, callback(error, save, R.string.card_saved));
        });

        View delete = view.findViewById(R.id.delete);
        if (editing == null) return;
        delete.setVisibility(View.VISIBLE);
        delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.card_delete)
                .setMessage(getString(R.string.card_delete_confirm, editing.getCard_nickname()))
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(R.string.card_delete, (d, w) -> {
                    delete.setEnabled(false);
                    vm().cards.delete(editing, callback(error, delete, R.string.card_deleted));
                })
                .show());
    }
}
