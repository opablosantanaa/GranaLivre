package com.opablosantanaa.granalivre.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Goal;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.Transfer;
import com.opablosantanaa.granalivre.entity.User;
import com.opablosantanaa.granalivre.relation.AccountWithBalance;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.ofx.OfxStatement;
import com.opablosantanaa.granalivre.repository.AccountRepository;
import com.opablosantanaa.granalivre.repository.BudgetRepository;
import com.opablosantanaa.granalivre.repository.CardRepository;
import com.opablosantanaa.granalivre.repository.CategoryRepository;
import com.opablosantanaa.granalivre.repository.FinancialTransactionRepository;
import com.opablosantanaa.granalivre.repository.GoalRepository;
import com.opablosantanaa.granalivre.repository.InstallmentRepository;
import com.opablosantanaa.granalivre.repository.InvoiceRepository;
import com.opablosantanaa.granalivre.repository.RecurrenceGenerator;
import com.opablosantanaa.granalivre.repository.StatementImportRepository;
import com.opablosantanaa.granalivre.repository.RecurrenceRepository;
import com.opablosantanaa.granalivre.repository.TransferRepository;
import com.opablosantanaa.granalivre.repository.UserRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainViewModel extends AndroidViewModel {
    public final UserRepository users;
    public final AccountRepository accounts;
    public final CardRepository cards;
    public final CategoryRepository categories;
    public final FinancialTransactionRepository transactions;
    public final InstallmentRepository installments;
    public final InvoiceRepository invoices;
    public final RecurrenceRepository recurrences;
    public final TransferRepository transfers;
    public final BudgetRepository budgets;
    public final GoalRepository goals;
    public final StatementImportRepository imports;
    public OfxStatement pendingImport;

    private final MutableLiveData<Long> userId = new MutableLiveData<>();
    private final MutableLiveData<YearMonth> selectedMonth = new MutableLiveData<>(YearMonth.now());

    public final LiveData<User> user;
    public final LiveData<List<AccountWithBalance>> accountList;
    public final LiveData<Long> totalBalance;
    public final LiveData<List<Card>> cardList;
    public final LiveData<List<Category>> categoryList;
    public final LiveData<List<FinancialTransaction>> history;
    public final LiveData<List<FinancialTransaction>> monthTransactions;
    public final LiveData<List<Transfer>> monthTransfers;
    public final LiveData<List<Goal>> goalList;
    public final LiveData<List<Budget>> budgetList;
    public final LiveData<List<Recurrence>> recurrenceList;
    public final LiveData<List<FinancialTransaction>> pendingList;
    public final MediatorLiveData<Map<Long, Long>> cardDebts = new MediatorLiveData<>();

    private final List<LiveData<Long>> debtSources = new ArrayList<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        users = new UserRepository(application);
        accounts = new AccountRepository(application);
        cards = new CardRepository(application);
        categories = new CategoryRepository(application);
        transactions = new FinancialTransactionRepository(application);
        installments = new InstallmentRepository(application);
        invoices = new InvoiceRepository(application);
        recurrences = new RecurrenceRepository(application);
        transfers = new TransferRepository(application);
        budgets = new BudgetRepository(application);
        goals = new GoalRepository(application);
        imports = new StatementImportRepository(application);

        user = Transformations.switchMap(userId, users::getById);
        accountList = Transformations.switchMap(userId, accounts::getWithBalanceByUser);
        totalBalance = Transformations.switchMap(userId, accounts::getTotalBalance);
        cardList = Transformations.switchMap(userId, cards::getByUser);
        categoryList = Transformations.switchMap(userId, categories::getByUser);
        goalList = Transformations.switchMap(userId, goals::getByUser);
        recurrenceList = Transformations.switchMap(userId, recurrences::getByUser);
        pendingList = Transformations.switchMap(userId, transactions::getPending);
        AppDatabase.databaseWriteExecutor.execute(() ->
                new RecurrenceGenerator(AppDatabase.getInstance(application)).generateUntil(LocalDate.now()));
        budgetList = Transformations.switchMap(userId, id -> budgets.getByUserAndMonth(id, YearMonth.now()));
        history = Transformations.switchMap(userId, id -> {
            LocalDate today = LocalDate.now();
            return transactions.getByUserBetween(id,
                    today.minusMonths(11).withDayOfMonth(1),
                    YearMonth.from(today).atEndOfMonth());
        });
        monthTransactions = Transformations.switchMap(selectedMonth, month ->
                Transformations.switchMap(userId, id ->
                        transactions.getByUserBetween(id, month.atDay(1), month.atEndOfMonth())));
        monthTransfers = Transformations.switchMap(selectedMonth, month ->
                Transformations.switchMap(userId, id ->
                        transfers.getByUserBetween(id, month.atDay(1), month.atEndOfMonth())));

        cardDebts.addSource(cardList, this::trackDebts);
    }

    private void trackDebts(List<Card> list) {
        for (LiveData<Long> source : debtSources) {
            cardDebts.removeSource(source);
        }
        debtSources.clear();
        Map<Long, Long> values = new HashMap<>();
        cardDebts.setValue(values);
        if (list == null) return;
        for (Card card : list) {
            LiveData<Long> source = cards.getOpenDebt(card.getCard_id());
            debtSources.add(source);
            cardDebts.addSource(source, debt -> {
                Map<Long, Long> next = new HashMap<>(cardDebts.getValue() == null ? values : cardDebts.getValue());
                next.put(card.getCard_id(), debt == null ? 0L : debt);
                cardDebts.setValue(next);
            });
        }
    }

    public void setUserId(long id) {
        if (userId.getValue() == null || userId.getValue() != id) {
            categories.adoptOrphans(id);
            userId.setValue(id);
        }
    }

    public long getUserId() {
        Long id = userId.getValue();
        return id == null ? 0L : id;
    }

    public LiveData<YearMonth> getSelectedMonth() {
        return selectedMonth;
    }

    public void shiftMonth(int delta) {
        YearMonth current = selectedMonth.getValue();
        selectedMonth.setValue((current == null ? YearMonth.now() : current).plusMonths(delta));
    }

    public static Category find(List<Category> list, Long id) {
        if (list == null || id == null) return null;
        for (Category category : list) {
            if (category.getCategory_id() == id) return category;
        }
        return null;
    }
}
