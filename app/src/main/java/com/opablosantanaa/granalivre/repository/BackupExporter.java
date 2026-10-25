package com.opablosantanaa.granalivre.repository;

import com.opablosantanaa.granalivre.dao.BackupDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.entity.BackupId;
import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Goal;
import com.opablosantanaa.granalivre.entity.GoalMovement;
import com.opablosantanaa.granalivre.entity.Installment;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.Transfer;
import com.opablosantanaa.granalivre.entity.User;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BackupExporter {
    public static final String FORMAT = "granalivre-backup";
    public static final int VERSION = 1;

    static final String ACCOUNTS = "accounts";
    static final String CATEGORIES = "categories";
    static final String CARDS = "cards";
    static final String INVOICES = "invoices";
    static final String INSTALLMENTS = "installments";
    static final String RECURRENCES = "recurrences";
    static final String TRANSACTIONS = "transactions";
    static final String TRANSFERS = "transfers";
    static final String BUDGETS = "budgets";
    static final String GOALS = "goals";
    static final String GOAL_MOVEMENTS = "goal_movements";

    private final AppDatabase database;
    private final BackupDao dao;

    public BackupExporter(AppDatabase database) {
        this.database = database;
        dao = database.backupDao();
    }

    public String export(long userId) {
        return database.runInTransaction(() -> {
            try {
                return build(userId).toString(2);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private String uuid(String table, long localId) {
        String uuid = dao.findUuid(table, localId);
        if (uuid != null) return uuid;
        uuid = UUID.randomUUID().toString();
        dao.link(new BackupId(table, localId, uuid));
        return uuid;
    }

    private Object ref(String table, Long localId) {
        return localId == null ? JSONObject.NULL : uuid(table, localId);
    }

    private static Object text(Object value) {
        return value == null ? JSONObject.NULL : value.toString();
    }

    JSONObject build(long userId) throws JSONException {
        User user = database.userDao().findById(userId);
        if (user == null) throw new ValidationException("Perfil não encontrado");

        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("createdAt", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        root.put("profile", new JSONObject()
                .put("name", user.getUser_name())
                .put("birthDate", user.getUser_birth_date().toString()));

        JSONArray accounts = new JSONArray();
        for (Account a : dao.accounts(userId)) {
            accounts.put(new JSONObject()
                    .put("id", uuid(ACCOUNTS, a.getAccount_id()))
                    .put("bankName", a.getAccount_bank_name())
                    .put("type", a.getAccount_type().name())
                    .put("initialBalanceCents", a.getAccount_initial_balance_cents()));
        }
        root.put(ACCOUNTS, accounts);

        JSONArray categories = new JSONArray();
        for (Category c : parentsFirst(dao.categories(userId))) {
            categories.put(new JSONObject()
                    .put("id", uuid(CATEGORIES, c.getCategory_id()))
                    .put("parent", ref(CATEGORIES, c.getCategory_parent_id()))
                    .put("name", c.getCategory_name())
                    .put("type", c.getCategory_type().name())
                    .put("icon", text(c.getCategory_icon()))
                    .put("color", c.getCategory_color()));
        }
        root.put(CATEGORIES, categories);

        JSONArray cards = new JSONArray();
        for (Card c : dao.cards(userId)) {
            cards.put(new JSONObject()
                    .put("id", uuid(CARDS, c.getCard_id()))
                    .put("paymentAccount", ref(ACCOUNTS, c.getCard_payment_account_id()))
                    .put("nickname", c.getCard_nickname())
                    .put("brand", c.getCard_brand().name())
                    .put("lastDigits", c.getCard_last_digits())
                    .put("limitCents", c.getCard_limit_cents())
                    .put("closingDay", c.getCard_closing_day())
                    .put("dueDay", c.getCard_due_day()));
        }
        root.put(CARDS, cards);

        JSONArray invoices = new JSONArray();
        for (Invoice i : dao.invoices(userId)) {
            invoices.put(new JSONObject()
                    .put("id", uuid(INVOICES, i.getInvoice_id()))
                    .put("card", uuid(CARDS, i.getCard_id()))
                    .put("referenceMonth", i.getInvoice_reference_month().toString())
                    .put("closingDate", i.getInvoice_closing_date().toString())
                    .put("dueDate", i.getInvoice_due_date().toString())
                    .put("status", i.getInvoice_status().name())
                    .put("paymentAccount", ref(ACCOUNTS, i.getInvoice_payment_account_id()))
                    .put("paidCents", i.getInvoice_paid_cents())
                    .put("paidDate", text(i.getInvoice_paid_date())));
        }
        root.put(INVOICES, invoices);

        JSONArray installments = new JSONArray();
        for (Installment i : dao.installments(userId)) {
            installments.put(new JSONObject()
                    .put("id", uuid(INSTALLMENTS, i.getInstallment_id()))
                    .put("description", i.getInstallment_description())
                    .put("totalCents", i.getInstallment_total_cents())
                    .put("count", i.getInstallment_count())
                    .put("firstDate", i.getInstallment_first_date().toString()));
        }
        root.put(INSTALLMENTS, installments);

        JSONArray recurrences = new JSONArray();
        for (Recurrence r : dao.recurrences(userId)) {
            recurrences.put(new JSONObject()
                    .put("id", uuid(RECURRENCES, r.getRecurrence_id()))
                    .put("account", ref(ACCOUNTS, r.getAccount_id()))
                    .put("card", ref(CARDS, r.getCard_id()))
                    .put("category", ref(CATEGORIES, r.getCategory_id()))
                    .put("type", r.getRecurrence_type().name())
                    .put("amountCents", r.getRecurrence_amount_cents())
                    .put("description", r.getRecurrence_description())
                    .put("frequency", r.getRecurrence_frequency().name())
                    .put("startDate", r.getRecurrence_start_date().toString())
                    .put("endDate", text(r.getRecurrence_end_date()))
                    .put("active", r.isRecurrence_active())
                    .put("lastGeneratedDate", text(r.getRecurrence_last_generated_date())));
        }
        root.put(RECURRENCES, recurrences);

        JSONArray transactions = new JSONArray();
        for (FinancialTransaction t : dao.transactions(userId)) {
            transactions.put(new JSONObject()
                    .put("id", uuid(TRANSACTIONS, t.getTransaction_id()))
                    .put("date", t.getTransaction_date().toString())
                    .put("type", t.getTransaction_type().name())
                    .put("amountCents", t.getTransaction_amount_cents())
                    .put("description", t.getTransaction_description())
                    .put("paid", t.isTransaction_paid())
                    .put("account", ref(ACCOUNTS, t.getAccount_id()))
                    .put("card", ref(CARDS, t.getCard_id()))
                    .put("invoice", ref(INVOICES, t.getInvoice_id()))
                    .put("category", ref(CATEGORIES, t.getCategory_id()))
                    .put("installment", ref(INSTALLMENTS, t.getInstallment_id()))
                    .put("installmentNumber", text(t.getTransaction_installment_number()))
                    .put("recurrence", ref(RECURRENCES, t.getRecurrence_id()))
                    .put("importId", text(t.getTransaction_import_id())));
        }
        root.put(TRANSACTIONS, transactions);

        JSONArray transfers = new JSONArray();
        for (Transfer t : dao.transfers(userId)) {
            transfers.put(new JSONObject()
                    .put("id", uuid(TRANSFERS, t.getTransfer_id()))
                    .put("date", t.getTransfer_date().toString())
                    .put("fromAccount", uuid(ACCOUNTS, t.getTransfer_from_account_id()))
                    .put("toAccount", uuid(ACCOUNTS, t.getTransfer_to_account_id()))
                    .put("amountCents", t.getTransfer_amount_cents())
                    .put("description", t.getTransfer_description()));
        }
        root.put(TRANSFERS, transfers);

        JSONArray budgets = new JSONArray();
        for (Budget b : dao.budgets(userId)) {
            budgets.put(new JSONObject()
                    .put("id", uuid(BUDGETS, b.getBudget_id()))
                    .put("category", uuid(CATEGORIES, b.getCategory_id()))
                    .put("month", b.getBudget_month().toString())
                    .put("limitCents", b.getBudget_limit_cents()));
        }
        root.put(BUDGETS, budgets);

        JSONArray goals = new JSONArray();
        for (Goal g : dao.goals(userId)) {
            goals.put(new JSONObject()
                    .put("id", uuid(GOALS, g.getGoal_id()))
                    .put("name", g.getGoal_name())
                    .put("targetCents", g.getGoal_target_cents())
                    .put("savedCents", g.getGoal_saved_cents())
                    .put("deadline", text(g.getGoal_deadline())));
        }
        root.put(GOALS, goals);

        JSONArray movements = new JSONArray();
        for (GoalMovement m : dao.goalMovements(userId)) {
            movements.put(new JSONObject()
                    .put("id", uuid(GOAL_MOVEMENTS, m.getMovement_id()))
                    .put("goal", uuid(GOALS, m.getGoal_id()))
                    .put("account", uuid(ACCOUNTS, m.getAccount_id()))
                    .put("amountCents", m.getMovement_amount_cents())
                    .put("date", m.getMovement_date().toString()));
        }
        root.put(GOAL_MOVEMENTS, movements);
        return root;
    }

    static List<Category> parentsFirst(List<Category> categories) {
        List<Category> ordered = new ArrayList<>(categories.size());
        List<Long> placed = new ArrayList<>();
        List<Category> pending = new ArrayList<>(categories);
        while (!pending.isEmpty()) {
            List<Category> next = new ArrayList<>();
            for (Category c : pending) {
                Long parent = c.getCategory_parent_id();
                if (parent == null || placed.contains(parent)) {
                    ordered.add(c);
                    placed.add(c.getCategory_id());
                } else {
                    next.add(c);
                }
            }
            if (next.size() == pending.size()) {
                ordered.addAll(next);
                break;
            }
            pending = next;
        }
        return ordered;
    }
}
