package com.opablosantanaa.granalivre.ui.sheet;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.entity.AccountType;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;
import com.opablosantanaa.granalivre.repository.DemoDataSeeder;
import com.opablosantanaa.granalivre.repository.RepositoryCallback;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.MainViewModel;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.Session;
import com.opablosantanaa.granalivre.ui.widget.SegmentedControl;

import java.util.List;

public class ProfileSheet extends BaseSheet {
    public ProfileSheet() {
        super(R.layout.sheet_profile);
    }

    private static int accountKind(AccountType type) {
        switch (type) {
            case POUPANCA: return R.string.account_poupanca;
            case INVESTIMENTO: return R.string.account_investimento;
            case CARTEIRA: return R.string.account_carteira;
            default: return R.string.account_corrente;
        }
    }

    private void row(LinearLayout parent, String title, String caption, CharSequence value) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_simple_row, parent, false);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.caption)).setText(caption);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        parent.addView(row);
    }

    private void none(LinearLayout parent, int text) {
        TextView view = new TextView(requireContext(), null, 0, R.style.Text_Caption);
        view.setText(text);
        int unit = Math.round(getResources().getDisplayMetrics().density);
        view.setPadding(0, 12 * unit, 0, 4 * unit);
        parent.addView(view);
    }

    private void open(BaseSheet sheet, String tag) {
        FragmentManager manager = getParentFragmentManager();
        dismiss();
        sheet.show(manager, tag);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MainViewModel vm = vm();
        header(view, "");
        vm.user.observe(getViewLifecycleOwner(), user -> {
            if (user == null) return;
            ((TextView) view.findViewById(R.id.sheet_title)).setText(user.getUser_name());
            ((TextView) view.findViewById(R.id.email)).setText(
                    getString(R.string.profile_born, Dates.shortDate(user.getUser_birth_date())));
        });

        SegmentedControl theme = view.findViewById(R.id.theme);
        theme.setOptions(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark));
        theme.setPalette(color(R.color.gl_line), color(R.color.gl_text_secondary),
                color(R.color.gl_text), color(R.color.gl_bg));
        theme.select(Session.theme(requireContext()), false);
        theme.setOnChange(index -> {
            dismiss();
            Session.setTheme(requireContext(), index);
        });

        int secondary = color(R.color.gl_text_secondary);
        LinearLayout accounts = view.findViewById(R.id.accounts);
        List<AccountWithBalance> accountList = vm.accountList.getValue();
        if (accountList == null || accountList.isEmpty()) none(accounts, R.string.profile_no_accounts);
        else for (AccountWithBalance a : accountList) {
            row(accounts, a.account.getAccount_bank_name(), getString(accountKind(a.account.getAccount_type())),
                    Money.styled(a.balanceCents, secondary));
        }

        LinearLayout cards = view.findViewById(R.id.cards);
        List<Card> cardList = vm.cardList.getValue();
        if (cardList == null || cardList.isEmpty()) none(cards, R.string.profile_no_cards);
        else for (Card c : cardList) {
            row(cards, c.getCard_nickname(), getString(R.string.invoice_line, c.getCard_closing_day(), c.getCard_due_day()),
                    "\u2022\u2022\u2022\u2022 " + c.getCard_last_digits());
        }

        vm.categoryList.observe(getViewLifecycleOwner(), list -> {
            int expense = 0;
            int income = 0;
            if (list != null) {
                for (Category c : list) {
                    if (c.getCategory_type() == TransactionType.RECEITA) income++;
                    else expense++;
                }
            }
            ((TextView) view.findViewById(R.id.categories_count)).setText(
                    getString(R.string.profile_categories_count, expense, income));
        });

        view.findViewById(R.id.add_account).setOnClickListener(v -> open(new AccountSheet(), "account"));
        view.findViewById(R.id.add_card).setOnClickListener(v -> open(new CardSheet(), "card"));
        view.findViewById(R.id.add_category).setOnClickListener(v ->
                open(CategorySheet.create(TransactionType.DESPESA), "category"));

        boolean debuggable = (requireContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        View demo = view.findViewById(R.id.demo);
        demo.setVisibility(debuggable && (accountList == null || accountList.isEmpty()) ? View.VISIBLE : View.GONE);
        demo.setOnClickListener(v -> {
            demo.setEnabled(false);
            new DemoDataSeeder(requireContext()).seed(vm.getUserId(), new RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    done(R.string.profile_demo_done);
                }

                @Override
                public void onError(Exception e) {
                    demo.setEnabled(true);
                    if (isAdded()) Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });

    }
}
