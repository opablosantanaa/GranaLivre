package com.opablosantanaa.granalivre.repository;

import android.content.Context;

import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.InstallmentSplitter;
import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.entity.AccountType;
import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.CardBrand;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Goal;
import com.opablosantanaa.granalivre.entity.Installment;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.InvoiceStatus;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.RecurrenceFrequency;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.entity.Transfer;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;

public class DemoDataSeeder extends BaseRepository {
    private final TransactionRules rules;
    private Map<String, Long> categoryIds;
    private long userId;

    public DemoDataSeeder(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public DemoDataSeeder(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        rules = new TransactionRules(database);
    }

    public void seed(long userId, RepositoryCallback<Void> callback) {
        execute(() -> {
            database.runInTransaction(() -> run(userId));
            return null;
        }, callback);
    }

    private long account(String bank, AccountType type, long initialCents) {
        Account account = new Account();
        account.setUser_id(userId);
        account.setAccount_bank_name(bank);
        account.setAccount_type(type);
        account.setAccount_initial_balance_cents(initialCents);
        return database.accountDao().insert(account);
    }

    private void transaction(TransactionType type, String description, String category, long cents,
                             LocalDate date, Long accountId, Long cardId, Long recurrenceId, boolean paid) {
        FinancialTransaction t = new FinancialTransaction();
        t.setUser_id(userId);
        t.setTransaction_type(type);
        t.setTransaction_description(description);
        t.setCategory_id(categoryIds.get(category));
        t.setTransaction_amount_cents(cents);
        t.setTransaction_date(date);
        t.setAccount_id(accountId);
        t.setCard_id(cardId);
        t.setRecurrence_id(recurrenceId);
        t.setTransaction_paid(paid);
        rules.prepare(t);
        database.financialTransactionDao().insert(t);
    }

    private void recurrence(TransactionType type, String description, String category, long cents, int day,
                            LocalDate start, LocalDate today, Long accountId, Long cardId) {
        Recurrence r = new Recurrence();
        r.setUser_id(userId);
        r.setRecurrence_type(type);
        r.setRecurrence_description(description);
        r.setCategory_id(categoryIds.get(category));
        r.setRecurrence_amount_cents(cents);
        r.setRecurrence_frequency(RecurrenceFrequency.MENSAL);
        r.setRecurrence_start_date(start.withDayOfMonth(day));
        r.setAccount_id(accountId);
        r.setCard_id(cardId);
        long id = database.recurrenceDao().insert(r);
        LocalDate last = null;
        for (LocalDate date = start.withDayOfMonth(day); !date.isAfter(today); date = date.plusMonths(1)) {
            transaction(type, description, category, cents, date, accountId, cardId, id, true);
            last = date;
        }
        r.setRecurrence_id(id);
        r.setRecurrence_last_generated_date(last);
        database.recurrenceDao().update(r);
    }

    private void installment(String description, String category, long totalCents, int count,
                             LocalDate first, Card card) {
        Installment installment = new Installment();
        installment.setUser_id(userId);
        installment.setInstallment_description(description);
        installment.setInstallment_total_cents(totalCents);
        installment.setInstallment_count(count);
        installment.setInstallment_first_date(first);
        List<Long> amounts = InstallmentSplitter.split(totalCents, count);
        LocalDate today = LocalDate.now();
        List<FinancialTransaction> parcels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate date = first.plusMonths(i);
            FinancialTransaction parcel = new FinancialTransaction();
            parcel.setUser_id(userId);
            parcel.setCard_id(card.getCard_id());
            parcel.setInvoice_id(rules.resolveInvoiceId(card, date));
            parcel.setCategory_id(categoryIds.get(category));
            parcel.setTransaction_type(TransactionType.DESPESA);
            parcel.setTransaction_amount_cents(amounts.get(i));
            parcel.setTransaction_date(date);
            parcel.setTransaction_installment_number(i + 1);
            parcel.setTransaction_description(description + " (" + (i + 1) + "/" + count + ")");
            parcel.setTransaction_paid(!date.isAfter(today));
            parcels.add(parcel);
        }
        database.installmentDao().insertWithParcels(installment, parcels);
    }

    private static long between(Random random, long minCents, long maxCents) {
        return minCents + (long) (random.nextDouble() * (maxCents - minCents));
    }

    private void run(long userId) {
        this.userId = userId;
        categoryIds = new HashMap<>();
        for (Category category : database.categoryDao().findByUser(userId)) {
            categoryIds.put(category.getCategory_name(), category.getCategory_id());
        }
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(11).withDayOfMonth(1);
        Random random = new Random(42);

        long nubank = account("Nubank", AccountType.CORRENTE, 320_000);
        long caixa = account("Caixa", AccountType.POUPANCA, 1_250_000);
        long inter = account("Inter", AccountType.INVESTIMENTO, 2_100_000);
        long wallet = account("Carteira", AccountType.CARTEIRA, 15_000);

        Card card = new Card();
        card.setUser_id(userId);
        card.setCard_nickname("Nubank Ultravioleta");
        card.setCard_brand(CardBrand.MASTERCARD);
        card.setCard_last_digits("1145");
        card.setCard_limit_cents(800_000);
        card.setCard_closing_day(5);
        card.setCard_due_day(15);
        card.setCard_payment_account_id(nubank);
        card.setCard_id(database.cardDao().insert(card));
        long cardId = card.getCard_id();

        recurrence(TransactionType.RECEITA, "Sal\u00e1rio", "Sal\u00e1rio", 680_000, 5, start, today, nubank, null);
        recurrence(TransactionType.DESPESA, "Aluguel", "Moradia", 190_000, 10, start, today, nubank, null);
        recurrence(TransactionType.DESPESA, "Internet", "Contas e servi\u00e7os", 11_990, 15, start, today, nubank, null);
        recurrence(TransactionType.DESPESA, "Streaming", "Lazer", 5_590, 12, start, today, null, cardId);
        recurrence(TransactionType.DESPESA, "Academia", "Sa\u00fade", 10_990, 8, start, today, null, cardId);

        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            if (random.nextDouble() < 0.22) {
                transaction(TransactionType.DESPESA, "Mercado", "Alimenta\u00e7\u00e3o",
                        between(random, 6_000, 32_000), date, nubank, null, null, true);
            }
            if (random.nextDouble() < 0.30) {
                transaction(TransactionType.DESPESA, random.nextBoolean() ? "Uber" : "99", "Transporte",
                        between(random, 1_200, 4_200), date, null, cardId, null, true);
            }
            if (random.nextDouble() < 0.06) {
                transaction(TransactionType.DESPESA, "Posto de combust\u00edvel", "Transporte",
                        between(random, 18_000, 26_000), date, nubank, null, null, true);
            }
            if (random.nextDouble() < 0.18) {
                transaction(TransactionType.DESPESA, random.nextBoolean() ? "Restaurante" : "iFood",
                        "Alimenta\u00e7\u00e3o", between(random, 2_800, 11_000), date, null, cardId, null, true);
            }
            if (random.nextDouble() < 0.04) {
                transaction(TransactionType.DESPESA, "Farm\u00e1cia", "Sa\u00fade",
                        between(random, 2_500, 14_000), date, nubank, null, null, true);
            }
            if (random.nextDouble() < 0.07) {
                transaction(TransactionType.DESPESA, random.nextBoolean() ? "Cinema" : "Bar com amigos", "Lazer",
                        between(random, 4_000, 16_000), date, null, cardId, null, true);
            }
            if (random.nextDouble() < 0.03) {
                transaction(TransactionType.DESPESA, "Loja de roupas", "Compras",
                        between(random, 8_000, 45_000), date, null, cardId, null, true);
            }
            if (random.nextDouble() < 0.10) {
                transaction(TransactionType.DESPESA, "Caf\u00e9", "Alimenta\u00e7\u00e3o",
                        between(random, 800, 2_200), date, wallet, null, null, true);
            }
            if (date.getDayOfMonth() == 20) {
                transaction(TransactionType.RECEITA, "Projeto freelance", "Outras receitas",
                        between(random, 90_000, 180_000), date, nubank, null, null, true);
            }
            if (date.getDayOfMonth() == 1) {
                transaction(TransactionType.RECEITA, "Rendimento", "Investimentos",
                        between(random, 15_000, 22_000), date, inter, null, null, true);
            }
            if (date.getDayOfMonth() == 6) {
                Transfer transfer = new Transfer();
                transfer.setUser_id(userId);
                transfer.setTransfer_from_account_id(nubank);
                transfer.setTransfer_to_account_id(caixa);
                transfer.setTransfer_amount_cents(100_000);
                transfer.setTransfer_date(date);
                transfer.setTransfer_description("Reserva do m\u00eas");
                database.transferDao().insert(transfer);
            }
        }

        LocalDate light = today.plusDays(5);
        if (YearMonth.from(light).equals(YearMonth.from(today))) {
            transaction(TransactionType.DESPESA, "Conta de luz", "Contas e servi\u00e7os", 18_640,
                    light, nubank, null, null, false);
        }

        installment("Curso de ingl\u00eas", "Educa\u00e7\u00e3o", 189_000, 10, today.minusMonths(3).withDayOfMonth(18), card);
        installment("Fone de ouvido", "Compras", 59_700, 3, today.minusMonths(1).withDayOfMonth(9), card);

        for (YearMonth month = YearMonth.from(start); !month.isAfter(YearMonth.from(today).plusMonths(1));
             month = month.plusMonths(1)) {
            Invoice invoice = database.invoiceDao().findByCardAndMonth(cardId, month);
            if (invoice == null) continue;
            if (invoice.getInvoice_due_date().isBefore(today)) {
                invoice.setInvoice_status(InvoiceStatus.PAGA);
                invoice.setInvoice_paid_cents(database.invoiceDao().findTotal(invoice.getInvoice_id()));
                invoice.setInvoice_payment_account_id(nubank);
                invoice.setInvoice_paid_date(invoice.getInvoice_due_date());
                database.invoiceDao().update(invoice);
            } else if (!invoice.getInvoice_closing_date().isAfter(today)) {
                invoice.setInvoice_status(InvoiceStatus.FECHADA);
                database.invoiceDao().update(invoice);
            }
        }

        goal("Reserva de emerg\u00eancia", 3_000_000, 1_845_000, null);
        goal("Viagem para Salvador", 600_000, 412_000, today.plusMonths(3));
        goal("Notebook novo", 750_000, 190_000, today.plusMonths(8));

        budget("Alimenta\u00e7\u00e3o", 160_000);
        budget("Transporte", 70_000);
        budget("Lazer", 35_000);
        budget("Compras", 50_000);
        budget("Sa\u00fade", 40_000);
    }

    private void goal(String name, long target, long saved, LocalDate deadline) {
        Goal goal = new Goal();
        goal.setUser_id(userId);
        goal.setGoal_name(name);
        goal.setGoal_target_cents(target);
        goal.setGoal_saved_cents(saved);
        goal.setGoal_deadline(deadline);
        database.goalDao().insert(goal);
    }

    private void budget(String category, long limit) {
        Long categoryId = categoryIds.get(category);
        if (categoryId == null) return;
        Budget budget = new Budget();
        budget.setUser_id(userId);
        budget.setCategory_id(categoryId);
        budget.setBudget_month(YearMonth.now());
        budget.setBudget_limit_cents(limit);
        database.budgetDao().upsert(budget);
    }
}
