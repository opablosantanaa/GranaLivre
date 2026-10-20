package com.opablosantanaa.granalivre.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.entity.Transfer;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Rows {
    public static final class Lookup {
        public final Map<Long, Category> categories = new HashMap<>();
        public final Map<Long, String> accounts = new HashMap<>();
        public final Map<Long, String> cards = new HashMap<>();
    }

    private Rows() {}

    public static Lookup lookup(List<Category> categories, List<AccountWithBalance> accounts, List<Card> cards) {
        Lookup lookup = new Lookup();
        if (categories != null) {
            for (Category c : categories) lookup.categories.put(c.getCategory_id(), c);
        }
        if (accounts != null) {
            for (AccountWithBalance a : accounts) {
                lookup.accounts.put(a.account.getAccount_id(), a.account.getAccount_bank_name());
            }
        }
        if (cards != null) {
            for (Card c : cards) lookup.cards.put(c.getCard_id(), c.getCard_nickname());
        }
        return lookup;
    }

    private static void icon(View row, int iconRes, int tint) {
        ImageView icon = row.findViewById(R.id.icon);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(tint));
        row.findViewById(R.id.icon_bg).setBackgroundTintList(
                ColorStateList.valueOf(CategoryStyle.tint(tint, 0x29)));
    }

    public static View transaction(LayoutInflater inflater, ViewGroup parent, FinancialTransaction t, Lookup lookup) {
        View row = inflater.inflate(R.layout.item_transaction, parent, false);
        bindTransaction(row, t, lookup);
        return row;
    }

    public static void bindTransaction(View row, FinancialTransaction t, Lookup lookup) {
        Context context = row.getContext();
        Category category = t.getCategory_id() == null ? null : lookup.categories.get(t.getCategory_id());
        boolean income = t.getTransaction_type() == TransactionType.RECEITA;
        icon(row, CategoryStyle.icon(category, t.getTransaction_type()), CategoryStyle.color(category));

        String title = t.getTransaction_description();
        if (title == null || title.trim().isEmpty()) {
            title = category != null ? category.getCategory_name() : context.getString(
                    income ? R.string.entry_type_income : R.string.entry_type_expense);
        }
        ((TextView) row.findViewById(R.id.title)).setText(title);

        String source = t.getCard_id() != null ? lookup.cards.get(t.getCard_id()) : lookup.accounts.get(t.getAccount_id());
        StringBuilder subtitle = new StringBuilder();
        if (category != null) subtitle.append(category.getCategory_name());
        if (source != null) {
            if (subtitle.length() > 0) subtitle.append(", ");
            subtitle.append(source);
        }
        ((TextView) row.findViewById(R.id.subtitle)).setText(subtitle);

        int secondary = ContextCompat.getColor(context, R.color.gl_text_secondary);
        long cents = income ? t.getTransaction_amount_cents() : -t.getTransaction_amount_cents();
        TextView amount = row.findViewById(R.id.amount);
        amount.setText(Money.styled(cents, secondary, true));
        amount.setTextColor(ContextCompat.getColor(context, income ? R.color.gl_income : R.color.gl_text));

        TextView tag = row.findViewById(R.id.tag);
        if (tag != null) {
            tag.setVisibility(View.GONE);
            if (!t.isTransaction_paid()) {
                tag.setText(R.string.tag_pending);
                tag.setTextColor(ContextCompat.getColor(context, R.color.gl_accent));
                tag.setVisibility(View.VISIBLE);
            } else if (t.getRecurrence_id() != null) {
                tag.setText(R.string.tag_monthly);
                tag.setVisibility(View.VISIBLE);
            }
        }
        row.setContentDescription(title + ", " + subtitle + ", " + Money.plain(cents) + ". "
                + context.getString(R.string.transaction_edit_hint));
    }

    public static View transfer(LayoutInflater inflater, ViewGroup parent, Transfer t, Lookup lookup) {
        View row = inflater.inflate(R.layout.item_transaction, parent, false);
        bindTransfer(row, t, lookup);
        return row;
    }

    public static void bindTransfer(View row, Transfer t, Lookup lookup) {
        Context context = row.getContext();
        icon(row, R.drawable.ic_swap, ContextCompat.getColor(context, R.color.gl_primary));
        String description = t.getTransfer_description();
        ((TextView) row.findViewById(R.id.title)).setText(description == null || description.trim().isEmpty()
                ? context.getString(R.string.entry_type_transfer) : description);
        String from = lookup.accounts.get(t.getTransfer_from_account_id());
        String to = lookup.accounts.get(t.getTransfer_to_account_id());
        ((TextView) row.findViewById(R.id.subtitle)).setText(context.getString(R.string.transfer_line,
                from == null ? "" : from, to == null ? "" : to));
        TextView amount = row.findViewById(R.id.amount);
        amount.setText(Money.styled(t.getTransfer_amount_cents(),
                ContextCompat.getColor(context, R.color.gl_text_secondary)));
    }
}
