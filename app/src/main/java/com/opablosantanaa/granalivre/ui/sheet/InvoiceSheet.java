package com.opablosantanaa.granalivre.ui.sheet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.domain.InvoiceCalculator;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.InvoiceStatus;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;
import com.opablosantanaa.granalivre.relation.InvoiceWithTransactions;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.Rows;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Fatura detalhada de um cartão: navega entre os meses, mostra compras, total, datas e permite pagar. */
public class InvoiceSheet extends BaseSheet {
    private static final String ARG_CARD = "card_id";

    private Card card;
    /** Faturas do cartão, da mais antiga para a mais nova. */
    private final List<Invoice> invoices = new ArrayList<>();
    private int index = -1;
    private LiveData<InvoiceWithTransactions> detail;

    public InvoiceSheet() {
        super(R.layout.sheet_invoice);
    }

    public static InvoiceSheet show(long cardId) {
        InvoiceSheet sheet = new InvoiceSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_CARD, cardId);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        long cardId = requireArguments().getLong(ARG_CARD);
        List<Card> cards = vm().cardList.getValue();
        if (cards != null) for (Card c : cards) if (c.getCard_id() == cardId) card = c;
        if (card == null) {
            dismiss();
            return;
        }
        header(view, card.getCard_nickname());

        ChipGroup accounts = view.findViewById(R.id.accounts);
        List<AccountWithBalance> list = vm().accountList.getValue();
        if (list != null) {
            for (AccountWithBalance a : list) {
                Chip chip = chip(accounts, a.account.getAccount_bank_name(), a.account.getAccount_id());
                Long preferred = card.getCard_payment_account_id();
                if (preferred != null && preferred == a.account.getAccount_id()) chip.setChecked(true);
            }
            if (accounts.getCheckedChipId() == View.NO_ID && accounts.getChildCount() > 0) {
                ((Chip) accounts.getChildAt(0)).setChecked(true);
            }
        }

        view.findViewById(R.id.prev).setOnClickListener(v -> select(view, index - 1));
        view.findViewById(R.id.next).setOnClickListener(v -> select(view, index + 1));

        vm().invoices.getByCard(cardId).observe(getViewLifecycleOwner(), all -> {
            Long keep = index >= 0 && index < invoices.size() ? invoices.get(index).getInvoice_id() : null;
            invoices.clear();
            if (all != null) {
                invoices.addAll(all);
                java.util.Collections.reverse(invoices);
            }
            int target = -1;
            for (int i = 0; i < invoices.size(); i++) {
                if (keep != null && invoices.get(i).getInvoice_id() == keep) target = i;
            }
            if (target < 0) target = currentIndex();
            select(view, target);
        });
    }

    /** A fatura que está recebendo as compras de hoje; se ainda não existir, a mais recente. */
    private int currentIndex() {
        YearMonth current = InvoiceCalculator.referenceMonth(LocalDate.now(), card.getCard_closing_day());
        int best = invoices.size() - 1;
        for (int i = 0; i < invoices.size(); i++) {
            if (!invoices.get(i).getInvoice_reference_month().isAfter(current)) best = i;
        }
        return best;
    }

    private void select(View view, int position) {
        boolean empty = invoices.isEmpty();
        view.findViewById(R.id.prev).setEnabled(position > 0);
        view.findViewById(R.id.prev).setAlpha(position > 0 ? 1f : 0.3f);
        view.findViewById(R.id.next).setEnabled(position < invoices.size() - 1);
        view.findViewById(R.id.next).setAlpha(position < invoices.size() - 1 ? 1f : 0.3f);
        if (empty) {
            ((TextView) view.findViewById(R.id.month)).setText("");
            ((TextView) view.findViewById(R.id.total)).setText(Money.styled(0, color(R.color.gl_text_secondary)));
            ((TextView) view.findViewById(R.id.dates)).setText(R.string.invoice_none);
            view.findViewById(R.id.status).setVisibility(View.GONE);
            view.findViewById(R.id.pay_group).setVisibility(View.GONE);
            view.findViewById(R.id.pay).setVisibility(View.GONE);
            view.findViewById(R.id.reopen).setVisibility(View.GONE);
            view.findViewById(R.id.empty).setVisibility(View.VISIBLE);
            return;
        }
        index = Math.max(0, Math.min(position, invoices.size() - 1));
        Invoice invoice = invoices.get(index);
        String month = Dates.month(invoice.getInvoice_reference_month());
        month = Character.toUpperCase(month.charAt(0)) + month.substring(1);
        ((TextView) view.findViewById(R.id.month)).setText(getString(R.string.invoice_month, month,
                invoice.getInvoice_reference_month().getYear()));
        ((TextView) view.findViewById(R.id.dates)).setText(getString(R.string.invoice_dates,
                Dates.shortDate(invoice.getInvoice_closing_date()), Dates.shortDate(invoice.getInvoice_due_date())));

        if (detail != null) detail.removeObservers(getViewLifecycleOwner());
        detail = vm().invoices.getWithTransactions(invoice.getInvoice_id());
        detail.observe(getViewLifecycleOwner(), data -> bind(view, data));
    }

    private void bind(View view, InvoiceWithTransactions data) {
        if (data == null || !isAdded()) return;
        Invoice invoice = data.invoice;
        List<FinancialTransaction> items = new ArrayList<>(data.transactions == null
                ? new ArrayList<>() : data.transactions);
        items.sort((a, b) -> b.getTransaction_date().compareTo(a.getTransaction_date()));
        long total = 0;
        for (FinancialTransaction t : items) {
            total += t.getTransaction_type() == TransactionType.DESPESA
                    ? t.getTransaction_amount_cents() : -t.getTransaction_amount_cents();
        }
        boolean paid = invoice.getInvoice_status() == InvoiceStatus.PAGA;
        ((TextView) view.findViewById(R.id.total)).setText(
                Money.styled(paid ? invoice.getInvoice_paid_cents() : total, color(R.color.gl_text_secondary)));

        TextView status = view.findViewById(R.id.status);
        status.setVisibility(View.VISIBLE);
        if (paid) {
            status.setText(getString(R.string.invoice_status_paid, Dates.shortDate(invoice.getInvoice_paid_date())));
            status.setTextColor(color(R.color.gl_income));
        } else if (!LocalDate.now().isBefore(invoice.getInvoice_closing_date())) {
            status.setText(R.string.invoice_status_closed);
            status.setTextColor(color(R.color.gl_accent));
        } else {
            status.setText(R.string.invoice_status_open);
            status.setTextColor(color(R.color.gl_text_secondary));
        }

        LinearLayout list = view.findViewById(R.id.list);
        list.removeAllViews();
        Rows.Lookup lookup = Rows.lookup(vm().categoryList.getValue(), vm().accountList.getValue(),
                vm().cardList.getValue());
        LayoutInflater inflater = getLayoutInflater();
        for (FinancialTransaction t : items) {
            View row = Rows.transaction(inflater, list, t, lookup);
            if (!paid) row.setOnClickListener(v -> TransactionSheet.edit(t).show(getParentFragmentManager(), "transaction"));
            list.addView(row);
        }
        view.findViewById(R.id.empty).setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        boolean canPay = !paid && total > 0;
        view.findViewById(R.id.pay_group).setVisibility(canPay ? View.VISIBLE : View.GONE);
        MaterialButton pay = view.findViewById(R.id.pay);
        pay.setVisibility(canPay ? View.VISIBLE : View.GONE);
        pay.setEnabled(true);
        pay.setText(getString(R.string.invoice_pay, Money.plain(total)));
        TextView error = view.findViewById(R.id.error);
        error.setVisibility(View.GONE);
        pay.setOnClickListener(v -> {
            long account = checkedTag(view.findViewById(R.id.accounts));
            pay.setEnabled(false);
            vm().invoices.pay(invoice.getInvoice_id(), account > 0 ? account : null, LocalDate.now(),
                    toast(error, pay, R.string.invoice_paid_toast));
        });
        View reopen = view.findViewById(R.id.reopen);
        reopen.setVisibility(paid ? View.VISIBLE : View.GONE);
        reopen.setOnClickListener(v -> vm().invoices.reopen(invoice.getInvoice_id(),
                toast(error, reopen, R.string.invoice_reopened)));
    }

    /** Mantém a folha aberta depois de pagar ou desfazer: a tela se atualiza sozinha. */
    private RepositoryCallback<Void> toast(TextView error, View button, int message) {
        return new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded()) return;
                button.setEnabled(true);
                android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                fail(error, button, e.getMessage());
            }
        };
    }
}
