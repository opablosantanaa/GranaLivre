package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import com.opablosantanaa.granalivre.dao.AccountDao;
import com.opablosantanaa.granalivre.dao.CardDao;
import com.opablosantanaa.granalivre.dao.CategoryDao;
import com.opablosantanaa.granalivre.dao.InvoiceDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.DefaultCategories;
import com.opablosantanaa.granalivre.domain.InvoiceCalculator;
import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.InvoiceStatus;
import com.opablosantanaa.granalivre.entity.TransactionType;

import java.time.LocalDate;
import java.time.YearMonth;

final class TransactionRules {
    private final AccountDao accountDao;
    private final CardDao cardDao;
    private final CategoryDao categoryDao;
    private final InvoiceDao invoiceDao;

    TransactionRules(AppDatabase database) {
        accountDao = database.accountDao();
        cardDao = database.cardDao();
        categoryDao = database.categoryDao();
        invoiceDao = database.invoiceDao();
    }

    void requireOwnAccount(long userId, long accountId) {
        Account account = accountDao.findById(accountId);
        require(account != null && account.getUser_id() == userId, "Conta inválida");
    }

    Card requireOwnCard(long userId, long cardId) {
        Card card = cardDao.findById(cardId);
        require(card != null && card.getUser_id() == userId, "Cartão inválido");
        return card;
    }

    Card validateSource(long userId, Long accountId, Long cardId) {
        require((accountId == null) != (cardId == null), "Informe uma conta ou um cartão, não os dois");
        if (accountId != null) {
            requireOwnAccount(userId, accountId);
            return null;
        }
        return requireOwnCard(userId, cardId);
    }

    void validateCategory(long userId, Long categoryId, TransactionType type) {
        if (categoryId == null) {
            return;
        }
        Category category = categoryDao.findById(categoryId);
        require(category != null && category.getUser_id() == userId, "Categoria inválida");
        require(category.getCategory_type() == type, "A categoria não corresponde ao tipo do lançamento");
    }

    Long resolveCategory(long userId, Long categoryId, TransactionType type) {
        if (categoryId != null) {
            validateCategory(userId, categoryId, type);
            return categoryId;
        }
        return fallbackCategory(categoryDao, userId, type);
    }

    static long fallbackCategory(CategoryDao categoryDao, long userId, TransactionType type) {
        String name = DefaultCategories.fallbackName(type);
        Category existing = categoryDao.findRootByName(userId, type, name);
        if (existing != null) return existing.getCategory_id();
        Category created = DefaultCategories.fallback(userId, type);
        return categoryDao.insert(created);
    }

    long resolveInvoiceId(Card card, LocalDate purchaseDate) {
        YearMonth month = InvoiceCalculator.referenceMonth(purchaseDate, card.getCard_closing_day());
        Invoice invoice = invoiceDao.findByCardAndMonth(card.getCard_id(), month);
        if (invoice != null) {
            require(invoice.getInvoice_status() != InvoiceStatus.PAGA, "A fatura deste período já foi paga");
            return invoice.getInvoice_id();
        }
        invoice = new Invoice();
        invoice.setCard_id(card.getCard_id());
        invoice.setInvoice_reference_month(month);
        invoice.setInvoice_closing_date(InvoiceCalculator.closingDate(month, card.getCard_closing_day()));
        invoice.setInvoice_due_date(InvoiceCalculator.dueDate(month, card.getCard_closing_day(), card.getCard_due_day()));
        return invoiceDao.insert(invoice);
    }

    void prepare(FinancialTransaction transaction) {
        require(transaction.getTransaction_amount_cents() > 0, "O valor deve ser maior que zero");
        Card card = validateSource(transaction.getUser_id(), transaction.getAccount_id(), transaction.getCard_id());
        transaction.setCategory_id(resolveCategory(transaction.getUser_id(), transaction.getCategory_id(),
                transaction.getTransaction_type()));
        transaction.setInvoice_id(card == null ? null : resolveInvoiceId(card, transaction.getTransaction_date()));
    }
}
