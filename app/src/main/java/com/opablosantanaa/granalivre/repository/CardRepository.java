package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.isBlank;
import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.CardDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.entity.Card;

import java.util.List;
import java.util.concurrent.Executor;

public class CardRepository extends BaseRepository {
    private static final int MIN_DAY = 1;
    private static final int MAX_DAY = 31;

    private final CardDao cardDao;
    private final TransactionRules rules;

    public CardRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public CardRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        cardDao = database.cardDao();
        rules = new TransactionRules(database);
    }

    public LiveData<Card> getById(long cardId) {
        return cardDao.getById(cardId);
    }

    public LiveData<List<Card>> getByUser(long userId) {
        return cardDao.getByUser(userId);
    }

    public LiveData<Long> getOpenDebt(long cardId) {
        return cardDao.getOpenDebt(cardId);
    }

    public void insert(Card card, RepositoryCallback<Long> callback) {
        execute(() -> {
            validate(card);
            return cardDao.insert(card);
        }, callback);
    }

    public void update(Card card, RepositoryCallback<Void> callback) {
        execute(() -> {
            validate(card);
            cardDao.update(card);
            return null;
        }, callback);
    }

    public void delete(Card card, RepositoryCallback<Void> callback) {
        execute(() -> {
            cardDao.delete(card);
            return null;
        }, callback);
    }

    private void validate(Card card) {
        card.setCard_nickname(card.getCard_nickname().trim());
        require(!isBlank(card.getCard_nickname()), "Informe um apelido para o cartão");
        require(card.getCard_last_digits().matches("\\d{4}"), "Informe os 4 últimos dígitos do cartão");
        require(card.getCard_limit_cents() >= 0, "O limite não pode ser negativo");
        require(isValidDay(card.getCard_closing_day()), "Dia de fechamento inválido");
        require(isValidDay(card.getCard_due_day()), "Dia de vencimento inválido");
        if (card.getCard_payment_account_id() != null) {
            rules.requireOwnAccount(card.getUser_id(), card.getCard_payment_account_id());
        }
    }

    private static boolean isValidDay(int day) {
        return day >= MIN_DAY && day <= MAX_DAY;
    }
}
