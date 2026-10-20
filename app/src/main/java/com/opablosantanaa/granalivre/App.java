package com.opablosantanaa.granalivre;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.opablosantanaa.granalivre.ui.MainViewModel;
import com.opablosantanaa.granalivre.ui.Session;
import com.opablosantanaa.granalivre.ui.screen.AnalyticsScreen;
import com.opablosantanaa.granalivre.ui.screen.GoalsScreen;
import com.opablosantanaa.granalivre.ui.screen.HomeScreen;
import com.opablosantanaa.granalivre.ui.screen.MovesScreen;
import com.opablosantanaa.granalivre.ui.screen.RecurringScreen;
import com.opablosantanaa.granalivre.ui.screen.Screen;
import com.opablosantanaa.granalivre.ui.screen.SplashScreen;
import com.opablosantanaa.granalivre.ui.prism.PrismView;

public class App extends AppCompatActivity {
    public static final int TAB_HOME = 0;
    public static final int TAB_MOVES = 1;
    public static final int TAB_FIXED_EXPENSE = 2;
    public static final int TAB_FIXED_INCOME = 3;
    public static final int TAB_ANALYTICS = 4;
    public static final int TAB_GOALS = 5;

    private static final int[] TAB_IDS = {R.id.tab_home, R.id.tab_moves, R.id.tab_fixed_expense,
            R.id.tab_fixed_income, R.id.tab_analytics, R.id.tab_goals};
    private static final int[] TAB_ICONS = {R.drawable.ic_home, R.drawable.ic_swap, R.drawable.ic_fixed_expense,
            R.drawable.ic_fixed_income, R.drawable.ic_chart, R.drawable.ic_target};
    private static final int[] TAB_LABELS = {R.string.tab_home, R.string.tab_moves, R.string.tab_fixed_expense,
            R.string.tab_fixed_income, R.string.tab_analytics, R.string.tab_goals};

    private PrismView prism;
    private View tabbar;
    private MainViewModel viewModel;
    private int topInset;
    private int bottomInset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        prism = findViewById(R.id.prism);
        tabbar = findViewById(R.id.tabbar);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            topInset = bars.top;
            bottomInset = bars.bottom;
            View container = findViewById(R.id.container);
            container.setPadding(0, 0, 0, Math.max(0, ime.bottom - bars.bottom));
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tabbar.getLayoutParams();
            params.bottomMargin = bottomInset + Math.round(12 * getResources().getDisplayMetrics().density);
            tabbar.setLayoutParams(params);
            return insets;
        });

        for (int i = 0; i < TAB_IDS.length; i++) {
            View tab = findViewById(TAB_IDS[i]);
            ((ImageView) tab.findViewById(R.id.tab_icon)).setImageResource(TAB_ICONS[i]);
            tab.setContentDescription(getString(TAB_LABELS[i]));
            TooltipCompat.setTooltipText(tab, getString(TAB_LABELS[i]));
            int index = i;
            tab.setOnClickListener(v -> showTab(index));
        }

        long userId = Session.userId(this);
        if (userId > 0) viewModel.setUserId(userId);
        if (savedInstanceState == null) {
            if (userId > 0) showTab(TAB_HOME);
            else show(new SplashScreen());
        }
    }

    private int backdropHolds;

    public void holdBackdrop() {
        backdropHolds++;
        prism.setPaused(true);
    }

    public void releaseBackdrop() {
        backdropHolds = Math.max(0, backdropHolds - 1);
        prism.setPaused(backdropHolds > 0);
    }

    public MainViewModel viewModel() {
        return viewModel;
    }

    public int topInset() {
        return topInset;
    }

    public int bottomInset() {
        return bottomInset;
    }

    public int bottomSpace() {
        return Math.round(84 * getResources().getDisplayMetrics().density) + bottomInset;
    }

    private static String tabTag(int tab) {
        return "tab_" + tab;
    }

    private static Fragment createTab(int tab) {
        if (tab == TAB_MOVES) return new MovesScreen();
        if (tab == TAB_FIXED_EXPENSE) return RecurringScreen.expenses();
        if (tab == TAB_FIXED_INCOME) return RecurringScreen.incomes();
        if (tab == TAB_ANALYTICS) return new AnalyticsScreen();
        if (tab == TAB_GOALS) return new GoalsScreen();
        return new HomeScreen();
    }

    public void show(Fragment screen) {
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction().setReorderingAllowed(true);
        for (Fragment fragment : manager.getFragments()) {
            if (fragment.getId() == R.id.container) transaction.remove(fragment);
        }
        transaction.add(R.id.container, screen).commit();
    }

    public void showTab(int tab) {
        FragmentManager manager = getSupportFragmentManager();
        if (manager.isStateSaved()) return;
        Fragment target = manager.findFragmentByTag(tabTag(tab));
        if (target != null && target.isVisible()) return;
        FragmentTransaction transaction = manager.beginTransaction().setReorderingAllowed(true);
        for (Fragment fragment : manager.getFragments()) {
            if (fragment.getId() != R.id.container || fragment == target) continue;
            if (fragment instanceof Screen && ((Screen) fragment).tab() >= 0) transaction.hide(fragment);
            else transaction.remove(fragment);
        }
        if (target == null) transaction.add(R.id.container, createTab(tab), tabTag(tab));
        else transaction.show(target);
        transaction.commit();
    }

    public void signedIn(long userId) {
        Session.signIn(this, userId);
        viewModel.setUserId(userId);
        showTab(TAB_HOME);
    }

    public boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    public void onScreen(Screen screen) {
        int backdrop = screen.backdrop();
        boolean night = isNight();
        float screenDp = getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density;
        PrismView.Style style = new PrismView.Style();
        style.backdrop = ContextCompat.getColor(this, R.color.gl_silk_backdrop);
        style.colors = new int[]{0xFF0B6E69, 0xFF2EC4B6, 0xFF4FD1C5, 0xFF22A06B};
        style.glow = 1.15f;
        style.saturation = 1.7f;
        style.timeScale = 0.62f;
        if (backdrop == Screen.BACKDROP_SPLASH) {
            style.scale = 3.6f;
            style.offsetY = -screenDp * 0.12f;
        } else {
            style.scale = 2.4f;
            style.offsetY = -screenDp * 0.3f;
        }
        boolean lightIcons = true;
        prism.setStyle(style);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!lightIcons);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(!night && screen.tab() >= 0 && !screen.inkSheet());

        int tab = screen.tab();
        tabbar.setVisibility(tab >= 0 ? View.VISIBLE : View.GONE);
        if (tab < 0) return;
        int active = 0xFF172124;
        int idle = ContextCompat.getColor(this, R.color.gl_on_ink_secondary);
        for (int i = 0; i < TAB_IDS.length; i++) {
            View item = findViewById(TAB_IDS[i]);
            boolean selected = i == tab;
            ImageView icon = item.findViewById(R.id.tab_icon);
            icon.setImageTintList(ColorStateList.valueOf(selected ? active : idle));
            item.setBackgroundResource(selected ? R.drawable.bg_tab_active : 0);
            item.setSelected(selected);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        prism.onResume();
    }

    @Override
    protected void onPause() {
        prism.onPause();
        super.onPause();
    }
}
