package com.opablosantanaa.granalivre.ui.screen;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.graphics.Rect;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.opablosantanaa.granalivre.App;
import com.opablosantanaa.granalivre.R;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.ofx.OfxException;
import com.opablosantanaa.granalivre.domain.ofx.OfxParser;
import com.opablosantanaa.granalivre.domain.ofx.OfxStatement;
import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.entity.User;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;
import com.opablosantanaa.granalivre.repository.PdfStatementReader;
import com.opablosantanaa.granalivre.ui.Dates;
import com.opablosantanaa.granalivre.ui.FinanceAdvisor;
import com.opablosantanaa.granalivre.ui.MainViewModel;
import com.opablosantanaa.granalivre.ui.Money;
import com.opablosantanaa.granalivre.ui.RecurrenceMath;
import com.opablosantanaa.granalivre.ui.RecurrenceStatus;
import com.opablosantanaa.granalivre.ui.Rows;
import com.opablosantanaa.granalivre.ui.sheet.AccountSheet;
import com.opablosantanaa.granalivre.ui.sheet.CardSheet;
import com.opablosantanaa.granalivre.ui.sheet.EntrySheet;
import com.opablosantanaa.granalivre.ui.sheet.ImportSheet;
import com.opablosantanaa.granalivre.ui.sheet.ProfileSheet;
import com.opablosantanaa.granalivre.ui.sheet.TransactionSheet;
import com.opablosantanaa.granalivre.ui.widget.CoinView;
import com.opablosantanaa.granalivre.ui.widget.HeatmapView;
import com.opablosantanaa.granalivre.ui.widget.PairBarsView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeScreen extends Screen {
    private static final int RECENT = 5;
    private static final int WEEK = 0;
    private static final int MONTH = 1;
    private static final int HERO_INCOME = 0xFF22A06B;
    private static final int HERO_EXPENSE = 0xFFE5484D;

    private int period = WEEK;
    private int selectedBar = -1;
    private LocalDate[] bucketStart = new LocalDate[0];
    private LocalDate[] bucketEnd = new LocalDate[0];
    private long[] bucketIncome = new long[0];
    private long[] bucketExpense = new long[0];

    private static final int[] ACCOUNT_TYPE_LABELS = {R.string.account_corrente, R.string.account_poupanca,
            R.string.account_investimento, R.string.account_carteira};
    private final BalanceAdapter balanceAdapter = new BalanceAdapter();
    private List<FinanceAdvisor.Tip> tips = new ArrayList<>();
    private int tipIndex;
    private int balancePage;

    private static final int MAX_STATEMENT_BYTES = 15 * 1024 * 1024;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String[]> pickStatement =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onStatementPicked);

    public HomeScreen() {
        super(R.layout.screen_home);
    }

    @Override
    public int tab() {
        return App.TAB_HOME;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        padTop(view.findViewById(R.id.header), 12);
        padBottomForTabs(view.findViewById(R.id.sheet), 8);
        ((TextView) view.findViewById(R.id.today)).setText(Dates.todayLabel());

        int income = color(R.color.gl_income);
        int expense = color(R.color.gl_expense);
        PairBarsView bars = view.findViewById(R.id.bars);
        bars.setColors(0x40FFFFFF, HERO_INCOME, HERO_EXPENSE, 0xFF1A2B2E, 0xFF3F5558);
        bars.setOnSelect(index -> {
            selectedBar = index;
            updateBarCaption(view);
        });
        tintLegend(view.findViewById(R.id.legend_income), HERO_INCOME);
        tintLegend(view.findViewById(R.id.legend_expense), HERO_EXPENSE);

        HeatmapView heatmap = view.findViewById(R.id.heatmap);
        heatmap.setColors(color(R.color.gl_line), income, expense, color(R.color.gl_text_secondary));
        heatmap.setOnDay((day, net) -> {
            String label = Dates.relativeDay(day);
            ((TextView) view.findViewById(R.id.heatmap_caption)).setText(net == 0
                    ? getString(R.string.heatmap_day_empty, label)
                    : getString(R.string.heatmap_day, label, Money.plain(net)));
        });
        buildLegend(view.findViewById(R.id.heatmap_swatches), income, expense);

        RecyclerView pager = view.findViewById(R.id.balance_pager);
        pager.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        pager.setAdapter(balanceAdapter);
        new PagerSnapHelper().attachToRecyclerView(pager);
        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                int page = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
                if (page >= 0 && page != balancePage) {
                    balancePage = page;
                    updateBalancePage(view);
                }
            }
        });

        view.findViewById(R.id.period).setOnClickListener(v -> {
            period = period == WEEK ? MONTH : WEEK;
            selectedBar = -1;
            refreshHero(view);
        });

        MainViewModel vm = vm();
        vm.user.observe(getViewLifecycleOwner(), user -> bindUser(view, user));
        vm.totalBalance.observe(getViewLifecycleOwner(), v -> later("refreshBalances", () -> refreshBalances(view)));
        vm.accountList.observe(getViewLifecycleOwner(), v -> {
            later("refreshList", () -> refreshList(view));
            later("refreshBalances", () -> refreshBalances(view));
        });
        vm.cardList.observe(getViewLifecycleOwner(), v -> later("refreshTiles", () -> refreshTiles(view)));
        vm.cardDebts.observe(getViewLifecycleOwner(), v -> later("refreshTiles", () -> refreshTiles(view)));
        vm.recurrenceList.observe(getViewLifecycleOwner(), v -> {
            later("refreshTiles", () -> refreshTiles(view));
            later("refreshWarning", () -> refreshWarning(view));
            later("refreshAdvisor", () -> refreshAdvisor(view));
        });
        vm.pendingList.observe(getViewLifecycleOwner(), v -> {
            later("refreshWarning", () -> refreshWarning(view));
            later("refreshAdvisor", () -> refreshAdvisor(view));
        });
        vm.categoryList.observe(getViewLifecycleOwner(), v -> {
            later("refreshList", () -> refreshList(view));
            later("refreshAdvisor", () -> refreshAdvisor(view));
        });
        vm.budgetList.observe(getViewLifecycleOwner(), v -> {
            later("refreshWarning", () -> refreshWarning(view));
            later("refreshAdvisor", () -> refreshAdvisor(view));
        });
        vm.history.observe(getViewLifecycleOwner(), v -> {
            selectedBar = -1;
            later("refreshHero", () -> refreshHero(view));
            later("refreshTiles", () -> refreshTiles(view));
            later("refreshHeatmap", () -> refreshHeatmap(view));
            later("refreshList", () -> refreshList(view));
            later("refreshAdvisor", () -> refreshAdvisor(view));
        });

        CoinView coin = view.findViewById(R.id.advisor);
        coin.setOnClickListener(v -> {
            coin.hop();
            View bubble = view.findViewById(R.id.advisor_bubble);
            if (bubble.getVisibility() == View.VISIBLE) {
                tipIndex = (tipIndex + 1) % Math.max(1, tips.size());
                showTip(view);
            } else {
                tipIndex = 0;
                showTip(view);
                openBubble(view);
            }
        });
        view.findViewById(R.id.advisor_close).setOnClickListener(v -> closeBubble(view));
        expandCloseTouchArea(view);
        view.findViewById(R.id.advisor_next).setOnClickListener(v -> {
            tipIndex = (tipIndex + 1) % Math.max(1, tips.size());
            coin.hop();
            showTip(view);
        });

        view.findViewById(R.id.identity).setOnClickListener(v ->
                new ProfileSheet().show(getChildFragmentManager(), "profile"));
        view.findViewById(R.id.add_card).setOnClickListener(v ->
                new CardSheet().show(getChildFragmentManager(), "card"));
        view.findViewById(R.id.action_income).setOnClickListener(v -> openEntry(EntrySheet.TYPE_INCOME));
        view.findViewById(R.id.action_expense).setOnClickListener(v -> openEntry(EntrySheet.TYPE_EXPENSE));
        view.findViewById(R.id.action_transfer).setOnClickListener(v -> openEntry(EntrySheet.TYPE_TRANSFER));
        view.findViewById(R.id.import_statement).setOnClickListener(v -> startImport());
        view.findViewById(R.id.see_all).setOnClickListener(v -> app().showTab(App.TAB_MOVES));
        view.findViewById(R.id.tile_income).setOnClickListener(v -> app().showTab(App.TAB_MOVES));
        view.findViewById(R.id.tile_expense).setOnClickListener(v -> app().showTab(App.TAB_ANALYTICS));
        view.findViewById(R.id.tile_fixed).setOnClickListener(v -> app().showRecurring(TransactionType.DESPESA));
        view.findViewById(R.id.tile_card).setOnClickListener(v -> {
            List<Card> cards = vm.cardList.getValue();
            if (cards == null || cards.isEmpty()) new CardSheet().show(getChildFragmentManager(), "card");
            else app().showTab(App.TAB_GOALS);
        });
    }

    private void startImport() {
        List<?> accounts = vm().accountList.getValue();
        if (accounts == null || accounts.isEmpty()) {
            new AccountSheet().show(getChildFragmentManager(), "account");
            return;
        }
        pickStatement.launch(new String[]{"*/*"});
    }

    private void onStatementPicked(Uri uri) {
        if (uri == null || !isAdded()) return;
        Context context = requireContext().getApplicationContext();
        Toast.makeText(context, R.string.import_reading, Toast.LENGTH_SHORT).show();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String error = null;
            OfxStatement statement = null;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_STATEMENT_BYTES) break;
                }
                if (out.size() > MAX_STATEMENT_BYTES) error = context.getString(R.string.import_too_big);
                else if (PdfStatementReader.isPdf(out.toByteArray())) {
                    statement = PdfStatementReader.read(context, out.toByteArray());
                } else {
                    statement = OfxParser.parse(out.toByteArray());
                }
                if (statement != null && statement.transactions.isEmpty() && statement.ledgerBalanceCents == null) {
                    error = context.getString(R.string.import_empty);
                    statement = null;
                }
            } catch (IOException | SecurityException e) {
                error = context.getString(R.string.import_read_error);
            } catch (OfxException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = context.getString(R.string.import_read_error);
            }
            String finalError = error;
            OfxStatement finalStatement = statement;
            mainHandler.post(() -> {
                if (!isAdded()) return;
                if (finalError != null) {
                    Toast.makeText(context, finalError, Toast.LENGTH_LONG).show();
                    return;
                }
                vm().pendingImport = finalStatement;
                new ImportSheet().show(getChildFragmentManager(), "import");
            });
        });
    }

    private void tintLegend(TextView label, int color) {
        TextViewCompat.setCompoundDrawableTintList(label, ColorStateList.valueOf(color));
    }

    private void buildLegend(LinearLayout row, int income, int expense) {
        row.removeAllViews();
        int[] colors = {HeatmapView.legendColor(expense, 4), HeatmapView.legendColor(expense, 1),
                color(R.color.gl_line), HeatmapView.legendColor(income, 1), HeatmapView.legendColor(income, 4)};
        for (int c : colors) {
            View swatch = new View(requireContext());
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(3));
            shape.setColor(c);
            swatch.setBackground(shape);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(12), dp(12));
            params.setMarginStart(dp(3));
            row.addView(swatch, params);
        }
    }

    private void openEntry(int type) {
        List<?> accounts = vm().accountList.getValue();
        if (accounts == null || accounts.isEmpty()) {
            new AccountSheet().show(getChildFragmentManager(), "account");
            return;
        }
        EntrySheet.newInstance(type).show(getChildFragmentManager(), "entry");
    }

    private void bindUser(View view, User user) {
        if (user == null) return;
        String full = user.getUser_name().trim();
        ((TextView) view.findViewById(R.id.name)).setText(full);
view.findViewById(R.id.identity).setContentDescription(full + ", " + Dates.todayLabel() + ". " + getString(R.string.home_profile));
    }

    private static boolean visible(FinancialTransaction t, LocalDate today) {
        return !t.getTransaction_date().isAfter(today);
    }

    private void refreshHero(View view) {
        MainViewModel vm = vm();
        LocalDate today = LocalDate.now();
        String[] labels;
        if (period == WEEK) {
            LocalDate monday = today.with(DayOfWeek.MONDAY);
            bucketStart = new LocalDate[7];
            bucketEnd = new LocalDate[7];
            labels = new String[7];
            for (int i = 0; i < 7; i++) {
                bucketStart[i] = monday.plusDays(i);
                bucketEnd[i] = bucketStart[i];
                labels[i] = Dates.weekdayShort(bucketStart[i]);
            }
        } else {
            YearMonth month = YearMonth.from(today);
            int count = (month.lengthOfMonth() + 6) / 7;
            bucketStart = new LocalDate[count];
            bucketEnd = new LocalDate[count];
            labels = new String[count];
            for (int i = 0; i < count; i++) {
                bucketStart[i] = month.atDay(1 + 7 * i);
                bucketEnd[i] = month.atDay(Math.min(month.lengthOfMonth(), 7 * i + 7));
                labels[i] = getString(R.string.week_of_month, i + 1);
            }
        }
        bucketIncome = new long[labels.length];
        bucketExpense = new long[labels.length];
        List<FinancialTransaction> history = vm.history.getValue();
        if (history != null) {
            for (FinancialTransaction t : history) {
                if (!visible(t, today)) continue;
                LocalDate date = t.getTransaction_date();
                for (int i = 0; i < labels.length; i++) {
                    if (date.isBefore(bucketStart[i]) || date.isAfter(bucketEnd[i])) continue;
                    if (t.getTransaction_type() == TransactionType.RECEITA) bucketIncome[i] += t.getTransaction_amount_cents();
                    else bucketExpense[i] += t.getTransaction_amount_cents();
                }
            }
        }
        long net = 0;
        for (int i = 0; i < labels.length; i++) net += bucketIncome[i] - bucketExpense[i];
        String netText = (net > 0 ? "+" : "") + Money.whole(net);
        ((TextView) view.findViewById(R.id.delta)).setText(getString(
                period == WEEK ? R.string.delta_week : R.string.delta_month, netText));

        TextView chip = view.findViewById(R.id.period);
        String periodName = getString(period == WEEK ? R.string.period_week : R.string.period_month);
        chip.setText(periodName);
        chip.setContentDescription(getString(R.string.period_change, periodName));

        if (selectedBar < 0 || selectedBar >= labels.length) {
            selectedBar = 0;
            for (int i = 0; i < labels.length; i++) {
                if (!today.isBefore(bucketStart[i]) && !today.isAfter(bucketEnd[i])) selectedBar = i;
            }
        }
        ((PairBarsView) view.findViewById(R.id.bars)).setData(bucketIncome, bucketExpense, labels, selectedBar);
        updateBarCaption(view);
    }

    private void refreshAdvisor(View view) {
        MainViewModel vm = vm();
        tips = FinanceAdvisor.tips(vm.history.getValue(), vm.categoryList.getValue(), vm.budgetList.getValue(),
                vm.recurrenceList.getValue(), vm.pendingList.getValue(), LocalDate.now());
        if (tipIndex >= tips.size()) tipIndex = 0;
        FinanceAdvisor.Mood top = tips.get(0).mood;
        ((CoinView) view.findViewById(R.id.advisor)).setMood(top, top == FinanceAdvisor.Mood.ALERT);
        if (view.findViewById(R.id.advisor_bubble).getVisibility() == View.VISIBLE) showTip(view);
    }

    private void showTip(View view) {
        if (tips.isEmpty()) return;
        FinanceAdvisor.Tip tip = tips.get(tipIndex);
        ((CoinView) view.findViewById(R.id.advisor)).setMood(tip.mood, false);
        ((TextView) view.findViewById(R.id.advisor_text)).setText(tip.text);
        int title;
        int tone;
        int icon;
        switch (tip.mood) {
            case ALERT:
                title = R.string.advisor_title_alert;
                tone = R.color.gl_expense;
                icon = R.drawable.ic_alert;
                break;
            case WARN:
                title = R.string.advisor_title_warn;
                tone = R.color.gl_accent;
                icon = R.drawable.ic_alert;
                break;
            case PRAISE:
                title = R.string.advisor_title_praise;
                tone = R.color.gl_income;
                icon = R.drawable.ic_check;
                break;
            default:
                title = R.string.advisor_title;
                tone = R.color.gl_primary;
                icon = R.drawable.ic_info;
                break;
        }
        ((TextView) view.findViewById(R.id.advisor_title)).setText(title);
        ImageView iconView = view.findViewById(R.id.advisor_icon);
        iconView.setImageResource(icon);
        iconView.setImageTintList(ColorStateList.valueOf(color(tone)));
        view.findViewById(R.id.advisor_icon_bg).setBackgroundTintList(
                ColorStateList.valueOf((color(tone) & 0x00FFFFFF) | 0x33000000));
        boolean many = tips.size() > 1;
        view.findViewById(R.id.advisor_footer).setVisibility(many ? View.VISIBLE : View.GONE);
        ((TextView) view.findViewById(R.id.advisor_count)).setText(
                getString(R.string.advisor_count, tipIndex + 1, tips.size()));
        ((MaterialButton) view.findViewById(R.id.advisor_next)).setText(
                tipIndex == tips.size() - 1 ? R.string.advisor_restart : R.string.advisor_next);
    }

    /**
     * A linha do título tem só a altura do ícone (32dp) e o Android corta o toque do X nesse limite;
     * o TouchDelegate devolve ao botão a área de toque de 48dp.
     */
    private void expandCloseTouchArea(View view) {
        View card = view.findViewById(R.id.advisor_card);
        View header = view.findViewById(R.id.advisor_header);
        View close = view.findViewById(R.id.advisor_close);
        card.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            Rect area = new Rect();
            close.getHitRect(area);
            area.offset(header.getLeft(), header.getTop());
            int extra = Math.max(0, (dp(48) - area.height()) / 2);
            area.top -= extra;
            area.bottom += extra;
            card.setTouchDelegate(new TouchDelegate(area, close));
        });
    }

    /** Abre o balão com a ponta sob a moeda, crescendo a partir dela. */
    private void openBubble(View view) {
        View bubble = view.findViewById(R.id.advisor_bubble);
        View coin = view.findViewById(R.id.advisor);
        View tail = view.findViewById(R.id.advisor_tail);
        bubble.setVisibility(View.VISIBLE);
        bubble.setAlpha(0f);
        bubble.post(() -> {
            if (!isAdded()) return;
            int[] coinAt = new int[2];
            int[] bubbleAt = new int[2];
            coin.getLocationInWindow(coinAt);
            bubble.getLocationInWindow(bubbleAt);
            float center = coinAt[0] + coin.getWidth() / 2f - bubbleAt[0];
            tail.setTranslationX(center - tail.getWidth() / 2f);
            bubble.setPivotX(center);
            bubble.setPivotY(0f);
            bubble.setScaleX(0.92f);
            bubble.setScaleY(0.92f);
            bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250)
                    .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
        });
    }

    private void closeBubble(View view) {
        View bubble = view.findViewById(R.id.advisor_bubble);
        bubble.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(150)
                .setInterpolator(new PathInterpolator(0.3f, 0f, 0.8f, 0.15f))
                .withEndAction(() -> {
                    bubble.setVisibility(View.GONE);
                    bubble.setAlpha(1f);
                    bubble.setScaleX(1f);
                    bubble.setScaleY(1f);
                    view.findViewById(R.id.advisor).sendAccessibilityEvent(
                            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
                }).start();
    }

    private void refreshBalances(View view) {
        MainViewModel vm = vm();
        Long total = vm.totalBalance.getValue();
        List<AccountWithBalance> accounts = vm.accountList.getValue();
        List<BalancePage> pages = new ArrayList<>();
        int count = accounts == null ? 0 : accounts.size();
        pages.add(new BalancePage(getString(R.string.card_total), total == null ? 0 : total,
                count == 0 ? "" : count == 1 ? accounts.get(0).account.getAccount_bank_name()
                        : getString(R.string.balance_total_caption, count)));
        if (accounts != null && accounts.size() > 1) {
            for (AccountWithBalance a : accounts) {
                pages.add(new BalancePage(a.account.getAccount_bank_name(), a.balanceCents,
                        getString(ACCOUNT_TYPE_LABELS[a.account.getAccount_type().ordinal()])));
            }
        }
        balanceAdapter.submit(pages);
        if (balancePage >= pages.size()) {
            balancePage = 0;
            ((RecyclerView) view.findViewById(R.id.balance_pager)).scrollToPosition(0);
        }
        LinearLayout dots = view.findViewById(R.id.balance_dots);
        dots.removeAllViews();
        dots.setVisibility(pages.size() > 1 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < pages.size() && pages.size() > 1; i++) {
            View dot = new View(requireContext());
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(0xFF1A2B2E);
            dot.setBackground(shape);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(6), dp(6));
            params.setMarginStart(i == 0 ? 0 : dp(5));
            dots.addView(dot, params);
        }
        updateBalancePage(view);
    }

    private void updateBalancePage(View view) {
        List<BalancePage> pages = balanceAdapter.pages;
        if (pages.isEmpty()) return;
        int page = Math.min(balancePage, pages.size() - 1);
        ((TextView) view.findViewById(R.id.balance_title)).setText(pages.get(page).title);
        LinearLayout dots = view.findViewById(R.id.balance_dots);
        for (int i = 0; i < dots.getChildCount(); i++) dots.getChildAt(i).setAlpha(i == page ? 1f : 0.25f);
    }

    private static final class BalancePage {
        final String title;
        final long cents;
        final String caption;

        BalancePage(String title, long cents, String caption) {
            this.title = title;
            this.cents = cents;
            this.caption = caption;
        }
    }

    private final class BalanceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final List<BalancePage> pages = new ArrayList<>();

        void submit(List<BalancePage> next) {
            pages.clear();
            pages.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_balance_page, parent, false);
            item.setLayoutParams(new RecyclerView.LayoutParams(parent.getWidth() > 0 ? parent.getWidth()
                    : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(item) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            BalancePage page = pages.get(position);
            ((TextView) holder.itemView.findViewById(R.id.balance)).setText(Money.styled(page.cents, 0xFF3F5558));
            ((TextView) holder.itemView.findViewById(R.id.balance_caption)).setText(page.caption);
            holder.itemView.setContentDescription(getString(R.string.balance_page, page.title,
                    Money.plain(page.cents), position + 1, pages.size()));
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }
    }

    private void updateBarCaption(View view) {
        if (selectedBar < 0 || selectedBar >= bucketStart.length) return;
        LocalDate start = bucketStart[selectedBar];
        LocalDate end = bucketEnd[selectedBar];
        String label = period == WEEK ? Dates.relativeDay(start)
                : getString(R.string.week_range, start.getDayOfMonth(), end.getDayOfMonth(),
                Dates.month(YearMonth.from(start)));
        String text = getString(R.string.bar_caption, label,
                Money.plain(bucketIncome[selectedBar]), Money.plain(bucketExpense[selectedBar]));
        TextView caption = view.findViewById(R.id.bar_caption);
        caption.setText(text);
        view.findViewById(R.id.bars).setContentDescription(text);
    }

    private void setTile(View view, int id, CharSequence value, String label, int icon) {
        View tile = view.findViewById(id);
        ((TextView) tile.findViewById(R.id.tile_value)).setText(value);
        ((TextView) tile.findViewById(R.id.tile_label)).setText(label);
        ((ImageView) tile.findViewById(R.id.tile_icon)).setImageResource(icon);
        tile.setContentDescription(value + ", " + label);
    }

    private void refreshTiles(View view) {
        MainViewModel vm = vm();
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        String monthName = Dates.month(month);
        long income = 0;
        long expense = 0;
        List<FinancialTransaction> history = vm.history.getValue();
        if (history != null) {
            for (FinancialTransaction t : history) {
                if (!visible(t, today) || !YearMonth.from(t.getTransaction_date()).equals(month)) continue;
                if (t.getTransaction_type() == TransactionType.RECEITA) income += t.getTransaction_amount_cents();
                else expense += t.getTransaction_amount_cents();
            }
        }
        setTile(view, R.id.tile_income, Money.whole(income),
                getString(R.string.tile_income_label, monthName), R.drawable.ic_arrow_up_right);
        setTile(view, R.id.tile_expense, Money.whole(expense),
                getString(R.string.tile_expense_label, monthName), R.drawable.ic_arrow_up_right);

        List<Card> cards = vm.cardList.getValue();
        Map<Long, Long> debts = vm.cardDebts.getValue();
        if (cards == null || cards.isEmpty()) {
            setTile(view, R.id.tile_card, "+", getString(R.string.tile_card_empty), R.drawable.ic_card);
        } else {
            long debt = 0;
            for (Card card : cards) {
                Long value = debts == null ? null : debts.get(card.getCard_id());
                debt += value == null ? 0 : value;
            }
            String owner = cards.size() == 1 ? cards.get(0).getCard_nickname() : getString(R.string.profile_cards).toLowerCase(Money.BR);
            setTile(view, R.id.tile_card, Money.whole(debt), getString(R.string.tile_card_label, owner),
                    R.drawable.ic_arrow_up_right);
        }

        long fixed = 0;
        List<Recurrence> recurrences = vm.recurrenceList.getValue();
        if (recurrences != null) {
            for (Recurrence r : recurrences) {
                if (r.isRecurrence_active() && r.getRecurrence_type() == TransactionType.DESPESA) {
                    fixed += RecurrenceMath.monthlyCents(r);
                }
            }
        }
        setTile(view, R.id.tile_fixed, Money.whole(fixed), getString(R.string.tile_fixed_label),
                R.drawable.ic_arrow_up_right);
    }

    private void refreshHeatmap(View view) {
        Map<LocalDate, Long> net = new HashMap<>();
        List<FinancialTransaction> history = vm().history.getValue();
        LocalDate today = LocalDate.now();
        if (history != null) {
            for (FinancialTransaction t : history) {
                if (!visible(t, today)) continue;
                long signed = t.getTransaction_type() == TransactionType.RECEITA
                        ? t.getTransaction_amount_cents() : -t.getTransaction_amount_cents();
                Long current = net.get(t.getTransaction_date());
                net.put(t.getTransaction_date(), (current == null ? 0 : current) + signed);
            }
        }
        ((HeatmapView) view.findViewById(R.id.heatmap)).setData(net);
    }

    private void refreshList(View view) {
        MainViewModel vm = vm();
        LinearLayout list = view.findViewById(R.id.list);
        TextView empty = view.findViewById(R.id.empty);
        MaterialButton emptyAction = view.findViewById(R.id.empty_action);
        View seeAll = view.findViewById(R.id.see_all);
        list.removeAllViews();

        List<?> accounts = vm.accountList.getValue();
        if (accounts != null && accounts.isEmpty()) {
            empty.setText(R.string.home_no_account);
            empty.setVisibility(View.VISIBLE);
            emptyAction.setText(R.string.home_add_account);
            emptyAction.setVisibility(View.VISIBLE);
            emptyAction.setOnClickListener(v -> new AccountSheet().show(getChildFragmentManager(), "account"));
            seeAll.setVisibility(View.GONE);
            return;
        }
        emptyAction.setVisibility(View.GONE);

        List<FinancialTransaction> history = vm.history.getValue();
        Rows.Lookup lookup = Rows.lookup(vm.categoryList.getValue(), vm.accountList.getValue(), vm.cardList.getValue());
        LayoutInflater inflater = getLayoutInflater();
        LocalDate today = LocalDate.now();
        int shown = 0;
        if (history != null) {
            for (FinancialTransaction t : history) {
                if (!visible(t, today)) continue;
                View row = Rows.transaction(inflater, list, t, lookup);
                row.setOnClickListener(v -> TransactionSheet.edit(t).show(getChildFragmentManager(), "transaction"));
                list.addView(row);
                if (++shown == RECENT) break;
            }
        }
        empty.setText(R.string.home_empty);
        empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        seeAll.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
        refreshWarning(view);
    }

    private void refreshWarning(View view) {
        MainViewModel vm = vm();
        List<Budget> budgets = vm.budgetList.getValue();
        List<FinancialTransaction> history = vm.history.getValue();
        List<Category> categories = vm.categoryList.getValue();
        View warning = view.findViewById(R.id.warning);
        if (showOverdue(view, warning)) return;
        warning.setOnClickListener(null);
        if (budgets == null || history == null || categories == null) {
            warning.setVisibility(View.GONE);
            return;
        }
        YearMonth month = YearMonth.now();
        Budget worst = null;
        float worstRatio = 0f;
        for (Budget budget : budgets) {
            if (budget.getBudget_limit_cents() <= 0) continue;
            long spent = 0;
            for (FinancialTransaction t : history) {
                if (t.getTransaction_type() == TransactionType.DESPESA
                        && t.getCategory_id() != null && t.getCategory_id() == budget.getCategory_id()
                        && YearMonth.from(t.getTransaction_date()).equals(month)) {
                    spent += t.getTransaction_amount_cents();
                }
            }
            float ratio = (float) spent / budget.getBudget_limit_cents();
            if (ratio > worstRatio) {
                worstRatio = ratio;
                worst = budget;
            }
        }
        if (worst == null || worstRatio < 0.85f) {
            warning.setVisibility(View.GONE);
            return;
        }
        Category category = MainViewModel.find(categories, worst.getCategory_id());
        String name = category == null ? "" : category.getCategory_name();
        // acima de 100% o aviso fala que estourou, sem citar porcentagem
        ((TextView) view.findViewById(R.id.warning_text)).setText(worstRatio > 1f
                ? getString(R.string.budget_warning_over, name, Dates.month(month))
                : getString(R.string.budget_warning, name, Math.round(worstRatio * 100), Dates.month(month)));
        warning.setVisibility(View.VISIBLE);
    }

    private boolean showOverdue(View view, View warning) {
        List<Recurrence> recurrences = vm().recurrenceList.getValue();
        if (recurrences == null) return false;
        LocalDate today = LocalDate.now();
        for (TransactionType type : new TransactionType[]{TransactionType.DESPESA, TransactionType.RECEITA}) {
            List<Recurrence> late = new java.util.ArrayList<>();
            LocalDate since = null;
            for (Recurrence r : recurrences) {
                if (r.getRecurrence_type() != type) continue;
                RecurrenceStatus status = RecurrenceStatus.of(r, vm().pendingList.getValue(), vm().history.getValue(), today);
                if (status.kind != RecurrenceStatus.Kind.OVERDUE) continue;
                late.add(r);
                if (since == null || status.date.isBefore(since)) since = status.date;
            }
            if (late.isEmpty()) continue;
            boolean expense = type == TransactionType.DESPESA;
            String text = late.size() == 1
                    ? getString(expense ? R.string.warning_overdue_expense_one : R.string.warning_overdue_income_one,
                    late.get(0).getRecurrence_description(), Dates.dayMonth(since))
                    : getString(expense ? R.string.warning_overdue_expense : R.string.warning_overdue_income, late.size());
            ((TextView) view.findViewById(R.id.warning_text)).setText(text);
            warning.setOnClickListener(v -> app().showRecurring(type));
            warning.setVisibility(View.VISIBLE);
            return true;
        }
        return false;
    }
}
