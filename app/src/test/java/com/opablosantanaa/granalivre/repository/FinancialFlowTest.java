package com.opablosantanaa.granalivre.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Installment;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.InvoiceStatus;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.RecurrenceFrequency;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.entity.Transfer;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinancialFlowTest extends DatabaseTest {
    private long userId;
    private long accountId;
    private long expenseCategoryId;
    private FinancialTransactionRepository transactions;
    private InvoiceRepository invoices;

    @Before
    public void createData() {
        userId = createUser("ana@email.com");
        accountId = createAccount(userId, 100_000);
        expenseCategoryId = createCategory(userId, TransactionType.DESPESA);
        transactions = new FinancialTransactionRepository(database, DIRECT, DIRECT);
        invoices = new InvoiceRepository(database, DIRECT, DIRECT);
    }

    private FinancialTransaction transaction(TransactionType type, long amount, LocalDate date, boolean paid) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setUser_id(userId);
        transaction.setTransaction_type(type);
        transaction.setTransaction_amount_cents(amount);
        transaction.setTransaction_date(date);
        transaction.setTransaction_paid(paid);
        return transaction;
    }

    private long insert(FinancialTransaction transaction) {
        return success((RepositoryCallback<Long> callback) -> transactions.insert(transaction, callback));
    }

    @Test
    public void balanceCombinesInitialPaidTransactionsTransfersAndPaidInvoices() {
        long savingsId = createAccount(userId, 0);
        FinancialTransaction income = transaction(TransactionType.RECEITA, 50_000, LocalDate.now(), true);
        income.setAccount_id(accountId);
        insert(income);
        FinancialTransaction expense = transaction(TransactionType.DESPESA, 20_000, LocalDate.now(), true);
        expense.setAccount_id(accountId);
        insert(expense);
        FinancialTransaction pending = transaction(TransactionType.DESPESA, 10_000, LocalDate.now(), false);
        pending.setAccount_id(accountId);
        insert(pending);

        Transfer transfer = new Transfer();
        transfer.setUser_id(userId);
        transfer.setTransfer_from_account_id(accountId);
        transfer.setTransfer_to_account_id(savingsId);
        transfer.setTransfer_amount_cents(30_000);
        success((RepositoryCallback<Long> callback) -> new TransferRepository(database, DIRECT, DIRECT).insert(transfer, callback));

        long cardId = createCard(userId, accountId, 25, 5);
        FinancialTransaction purchase = transaction(TransactionType.DESPESA, 15_000, LocalDate.now(), true);
        purchase.setCard_id(cardId);
        long purchaseId = insert(purchase);
        long invoiceId = database.financialTransactionDao().findById(purchaseId).getInvoice_id();
        success((RepositoryCallback<Void> callback) -> invoices.pay(invoiceId, null, LocalDate.now(), callback));

        assertEquals(100_000 + 50_000 - 20_000 - 30_000 - 15_000, database.accountDao().findBalance(accountId));
        assertEquals(30_000, database.accountDao().findBalance(savingsId));
    }

    @Test
    public void transactionNeedsExactlyOneSourceOwnedByUser() {
        long cardId = createCard(userId, null, 25, 5);
        FinancialTransaction both = transaction(TransactionType.DESPESA, 1_000, LocalDate.now(), true);
        both.setAccount_id(accountId);
        both.setCard_id(cardId);
        failure((RepositoryCallback<Long> callback) -> transactions.insert(both, callback));

        FinancialTransaction none = transaction(TransactionType.DESPESA, 1_000, LocalDate.now(), true);
        failure((RepositoryCallback<Long> callback) -> transactions.insert(none, callback));

        long otherAccountId = createAccount(createUser("outro@email.com"), 0);
        FinancialTransaction foreign = transaction(TransactionType.DESPESA, 1_000, LocalDate.now(), true);
        foreign.setAccount_id(otherAccountId);
        assertEquals("Conta inválida", failure((RepositoryCallback<Long> callback) -> transactions.insert(foreign, callback)).getMessage());
    }

    @Test
    public void categoryMustMatchTransactionType() {
        FinancialTransaction income = transaction(TransactionType.RECEITA, 1_000, LocalDate.now(), true);
        income.setAccount_id(accountId);
        income.setCategory_id(expenseCategoryId);
        failure((RepositoryCallback<Long> callback) -> transactions.insert(income, callback));
    }

    @Test
    public void cardPurchasesAreGroupedIntoInvoicesByClosingDay() {
        long cardId = createCard(userId, accountId, 25, 5);
        FinancialTransaction before = transaction(TransactionType.DESPESA, 1_000, LocalDate.of(2026, 10, 10), true);
        before.setCard_id(cardId);
        FinancialTransaction sameInvoice = transaction(TransactionType.DESPESA, 2_000, LocalDate.of(2026, 10, 24), true);
        sameInvoice.setCard_id(cardId);
        FinancialTransaction after = transaction(TransactionType.DESPESA, 4_000, LocalDate.of(2026, 10, 25), true);
        after.setCard_id(cardId);

        long firstInvoice = database.financialTransactionDao().findById(insert(before)).getInvoice_id();
        long secondInvoice = database.financialTransactionDao().findById(insert(sameInvoice)).getInvoice_id();
        long nextInvoice = database.financialTransactionDao().findById(insert(after)).getInvoice_id();

        assertEquals(firstInvoice, secondInvoice);
        assertNotEquals(firstInvoice, nextInvoice);
        Invoice october = database.invoiceDao().findById(firstInvoice);
        assertEquals(YearMonth.of(2026, 10), october.getInvoice_reference_month());
        assertEquals(LocalDate.of(2026, 11, 5), october.getInvoice_due_date());
        assertEquals(3_000, database.invoiceDao().findTotal(firstInvoice));
    }

    @Test
    public void paidInvoiceIsProtected() {
        long cardId = createCard(userId, accountId, 25, 5);
        FinancialTransaction purchase = transaction(TransactionType.DESPESA, 1_000, LocalDate.of(2026, 10, 10), true);
        purchase.setCard_id(cardId);
        long purchaseId = insert(purchase);
        long invoiceId = database.financialTransactionDao().findById(purchaseId).getInvoice_id();
        success((RepositoryCallback<Void> callback) -> invoices.pay(invoiceId, null, null, callback));

        Invoice paid = database.invoiceDao().findById(invoiceId);
        assertEquals(InvoiceStatus.PAGA, paid.getInvoice_status());
        assertEquals(1_000, paid.getInvoice_paid_cents());

        FinancialTransaction stored = database.financialTransactionDao().findById(purchaseId);
        failure((RepositoryCallback<Void> callback) -> transactions.delete(stored, callback));

        FinancialTransaction late = transaction(TransactionType.DESPESA, 500, LocalDate.of(2026, 10, 11), true);
        late.setCard_id(cardId);
        failure((RepositoryCallback<Long> callback) -> transactions.insert(late, callback));
        failure((RepositoryCallback<Void> callback) -> invoices.pay(invoiceId, null, null, callback));
    }

    @Test
    public void installmentOnCardSpreadsParcelsAcrossInvoices() {
        long cardId = createCard(userId, accountId, 25, 5);
        Installment installment = new Installment();
        installment.setUser_id(userId);
        installment.setInstallment_description("Notebook");
        installment.setInstallment_total_cents(1_000);
        installment.setInstallment_count(3);
        installment.setInstallment_first_date(LocalDate.of(2026, 10, 10));

        InstallmentRepository repository = new InstallmentRepository(database, DIRECT, DIRECT);
        long installmentId = success((RepositoryCallback<Long> callback) -> repository.create(
                installment, null, cardId, expenseCategoryId, callback));

        assertEquals(3, database.query("SELECT * FROM invoices WHERE card_id = " + cardId, null).getCount());
        assertEquals(334, database.invoiceDao().findTotal(
                database.invoiceDao().findByCardAndMonth(cardId, YearMonth.of(2026, 10)).getInvoice_id()));
        assertEquals(333, database.invoiceDao().findTotal(
                database.invoiceDao().findByCardAndMonth(cardId, YearMonth.of(2026, 12)).getInvoice_id()));
        assertEquals(3, database.query(
                "SELECT * FROM transactions WHERE installment_id = " + installmentId, null).getCount());
    }

    @Test
    public void recurrenceGeneratesPendingTransactionsOnlyOnce() {
        Recurrence recurrence = new Recurrence();
        recurrence.setUser_id(userId);
        recurrence.setAccount_id(accountId);
        recurrence.setCategory_id(expenseCategoryId);
        recurrence.setRecurrence_type(TransactionType.DESPESA);
        recurrence.setRecurrence_amount_cents(5_000);
        recurrence.setRecurrence_description("Aluguel");
        recurrence.setRecurrence_frequency(RecurrenceFrequency.MENSAL);
        recurrence.setRecurrence_start_date(LocalDate.now().minusMonths(3));

        long recurrenceId = success((RepositoryCallback<Long> callback) -> new RecurrenceRepository(database, DIRECT, DIRECT)
                .insert(recurrence, callback));

        String generated = "SELECT * FROM transactions WHERE recurrence_id = " + recurrenceId;
        assertEquals(4, database.query(generated, null).getCount());
        assertEquals(0, new RecurrenceGenerator(database).generateUntil(LocalDate.now()));
        assertEquals(4, database.query(generated, null).getCount());
        assertEquals(100_000, database.accountDao().findBalance(accountId));
    }

    @Test
    public void recurrenceWithPastEndDateIsDeactivated() {
        Recurrence recurrence = new Recurrence();
        recurrence.setUser_id(userId);
        recurrence.setAccount_id(accountId);
        recurrence.setRecurrence_type(TransactionType.DESPESA);
        recurrence.setRecurrence_amount_cents(5_000);
        recurrence.setRecurrence_description("Academia");
        recurrence.setRecurrence_frequency(RecurrenceFrequency.MENSAL);
        recurrence.setRecurrence_start_date(LocalDate.now().minusMonths(5));
        recurrence.setRecurrence_end_date(LocalDate.now().minusMonths(4));

        long recurrenceId = success((RepositoryCallback<Long> callback) -> new RecurrenceRepository(database, DIRECT, DIRECT)
                .insert(recurrence, callback));

        assertFalse(database.recurrenceDao().findById(recurrenceId).isRecurrence_active());
        assertEquals(2, database.query(
                "SELECT * FROM transactions WHERE recurrence_id = " + recurrenceId, null).getCount());
    }

    @Test
    public void savingBudgetTwiceForSameMonthUpdatesInsteadOfDuplicating() {
        BudgetRepository repository = new BudgetRepository(database, DIRECT, DIRECT);
        Budget first = new Budget();
        first.setUser_id(userId);
        first.setCategory_id(expenseCategoryId);
        first.setBudget_month(YearMonth.of(2026, 10));
        first.setBudget_limit_cents(50_000);
        long firstId = success((RepositoryCallback<Long> callback) -> repository.save(first, callback));

        Budget second = new Budget();
        second.setUser_id(userId);
        second.setCategory_id(expenseCategoryId);
        second.setBudget_month(YearMonth.of(2026, 10));
        second.setBudget_limit_cents(80_000);
        long secondId = success((RepositoryCallback<Long> callback) -> repository.save(second, callback));

        assertEquals(firstId, secondId);
        Budget stored = database.budgetDao().findByCategoryAndMonth(expenseCategoryId, YearMonth.of(2026, 10));
        assertEquals(80_000, stored.getBudget_limit_cents());
        assertEquals(1, database.query("SELECT * FROM budgets", null).getCount());
    }

    private Budget budget(long ownerId, YearMonth month, long cents) {
        Budget budget = new Budget();
        budget.setUser_id(ownerId);
        budget.setCategory_id(expenseCategoryId);
        budget.setBudget_month(month);
        budget.setBudget_limit_cents(cents);
        return budget;
    }

    @Test
    public void budgetsAreKeptPerMonthAndListedByMonth() {
        BudgetRepository repository = new BudgetRepository(database, DIRECT, DIRECT);
        success((RepositoryCallback<Long> callback) -> repository.save(budget(userId, YearMonth.of(2026, 10), 50_000), callback));
        success((RepositoryCallback<Long> callback) -> repository.save(budget(userId, YearMonth.of(2026, 12), 70_000), callback));

        List<Budget> december = success(callback -> repository.findByMonth(userId, YearMonth.of(2026, 12), callback));
        assertEquals(1, december.size());
        assertEquals(70_000, december.get(0).getBudget_limit_cents());
        List<Budget> november = success(callback -> repository.findByMonth(userId, YearMonth.of(2026, 11), callback));
        assertTrue(november.isEmpty());
        assertEquals(50_000, database.budgetDao()
                .findByCategoryAndMonth(expenseCategoryId, YearMonth.of(2026, 10)).getBudget_limit_cents());
    }

    @Test
    public void savingAllLimitsForMonthCreatesOrReplacesAndSkipsUncategorized() {
        BudgetRepository repository = new BudgetRepository(database, DIRECT, DIRECT);
        YearMonth november = YearMonth.of(2026, 11);
        success((RepositoryCallback<Long> callback) -> repository.save(budget(userId, november, 10_000), callback));
        long otherCategory = createCategory(userId, TransactionType.DESPESA);
        Map<Long, Long> limits = new HashMap<>();
        limits.put(expenseCategoryId, 25_000L);
        limits.put(otherCategory, 5_000L);
        limits.put(null, 9_000L);

        int saved = success(callback -> repository.saveAll(userId, november, limits, callback));
        assertEquals(2, saved);
        assertEquals(25_000, database.budgetDao().findByCategoryAndMonth(expenseCategoryId, november).getBudget_limit_cents());
        assertEquals(5_000, database.budgetDao().findByCategoryAndMonth(otherCategory, november).getBudget_limit_cents());
        assertEquals(2, database.query("SELECT * FROM budgets", null).getCount());
    }

    @Test
    public void deletingBudgetRemovesOnlyThatMonthAndChecksOwner() {
        BudgetRepository repository = new BudgetRepository(database, DIRECT, DIRECT);
        long octoberId = success((RepositoryCallback<Long> callback) ->
                repository.save(budget(userId, YearMonth.of(2026, 10), 50_000), callback));
        success((RepositoryCallback<Long> callback) -> repository.save(budget(userId, YearMonth.of(2026, 11), 60_000), callback));
        Budget october = database.budgetDao().findById(octoberId);

        Budget foreign = database.budgetDao().findById(octoberId);
        foreign.setUser_id(createUser("outro@email.com"));
        DatabaseTest.<Void>failure(callback -> repository.delete(foreign, callback));
        assertEquals(2, database.query("SELECT * FROM budgets", null).getCount());

        success((RepositoryCallback<Void> callback) -> repository.delete(october, callback));
        assertNull(database.budgetDao().findById(octoberId));
        assertEquals(60_000, database.budgetDao()
                .findByCategoryAndMonth(expenseCategoryId, YearMonth.of(2026, 11)).getBudget_limit_cents());
    }
}
