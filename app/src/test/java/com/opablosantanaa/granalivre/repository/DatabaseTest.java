package com.opablosantanaa.granalivre.repository;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.TransactionType;
import com.opablosantanaa.granalivre.entity.User;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public abstract class DatabaseTest {
    protected static final Executor DIRECT = Runnable::run;

    protected AppDatabase database;

    @Before
    public void openDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    protected long createUser(String email) {
        User user = new User();
        user.setUser_name("Teste");
        user.setUser_email(email);
        user.setUser_birth_date(LocalDate.of(1995, 5, 20));
        user.setUser_password_hash("hash");
        return database.userDao().insert(user);
    }

    protected long createAccount(long userId, long initialBalanceCents) {
        Account account = new Account();
        account.setUser_id(userId);
        account.setAccount_bank_name("Banco");
        account.setAccount_initial_balance_cents(initialBalanceCents);
        return database.accountDao().insert(account);
    }

    protected long createCard(long userId, Long paymentAccountId, int closingDay, int dueDay) {
        Card card = new Card();
        card.setUser_id(userId);
        card.setCard_nickname("Cartão");
        card.setCard_last_digits("1234");
        card.setCard_limit_cents(500_000);
        card.setCard_payment_account_id(paymentAccountId);
        card.setCard_closing_day(closingDay);
        card.setCard_due_day(dueDay);
        return database.cardDao().insert(card);
    }

    protected long createCategory(long userId, TransactionType type) {
        Category category = new Category();
        category.setUser_id(userId);
        category.setCategory_name(type.name());
        category.setCategory_type(type);
        return database.categoryDao().insert(category);
    }

    protected static <T> T success(Consumer<RepositoryCallback<T>> action) {
        Outcome<T> outcome = run(action);
        assertNull(String.valueOf(outcome.error.get()), outcome.error.get());
        return outcome.result.get();
    }

    protected static <T> ValidationException failure(Consumer<RepositoryCallback<T>> action) {
        Outcome<T> outcome = run(action);
        assertNotNull("Era esperada uma falha", outcome.error.get());
        if (!(outcome.error.get() instanceof ValidationException)) {
            throw new AssertionError(outcome.error.get());
        }
        return (ValidationException) outcome.error.get();
    }

    private static <T> Outcome<T> run(Consumer<RepositoryCallback<T>> action) {
        Outcome<T> outcome = new Outcome<>();
        action.accept(new RepositoryCallback<T>() {
            @Override
            public void onSuccess(T result) {
                outcome.result.set(result);
            }

            @Override
            public void onError(Exception error) {
                outcome.error.set(error);
            }
        });
        return outcome;
    }

    private static final class Outcome<T> {
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<Exception> error = new AtomicReference<>();
    }
}
