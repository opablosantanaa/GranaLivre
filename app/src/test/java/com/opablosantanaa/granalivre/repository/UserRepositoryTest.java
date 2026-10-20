package com.opablosantanaa.granalivre.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.opablosantanaa.granalivre.entity.User;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;

public class UserRepositoryTest extends DatabaseTest {
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 5, 20);

    private UserRepository repository;

    @Before
    public void createRepository() {
        repository = new UserRepository(database, DIRECT, DIRECT);
    }

    @Test
    public void registerNormalizesEmailHashesPasswordAndCreatesDefaultCategories() {
        long userId = success((RepositoryCallback<Long> callback) -> repository.register(
                "  Ana  ", " Ana@Email.COM ", "senhaSegura", BIRTH_DATE, callback));
        User user = database.userDao().findById(userId);
        assertEquals("Ana", user.getUser_name());
        assertEquals("ana@email.com", user.getUser_email());
        assertNotEquals("senhaSegura", user.getUser_password_hash());
        assertEquals(13, database.query("SELECT * FROM categories WHERE user_id = " + userId, null).getCount());
    }

    @Test
    public void duplicateEmailIsRejectedIgnoringCase() {
        success((RepositoryCallback<Long> callback) -> repository.register("Ana", "ana@email.com", "senhaSegura", BIRTH_DATE, callback));
        ValidationException error = failure((RepositoryCallback<Long> callback) -> repository.register(
                "Outra", "ANA@email.com", "senhaSegura", BIRTH_DATE, callback));
        assertEquals("Email já cadastrado", error.getMessage());
    }

    @Test
    public void loginAcceptsCorrectPasswordAndRejectsWrongOne() {
        long userId = success((RepositoryCallback<Long> callback) -> repository.register(
                "Ana", "ana@email.com", "senhaSegura", BIRTH_DATE, callback));
        User logged = success((RepositoryCallback<User> callback) -> repository.login("ANA@email.com", "senhaSegura", callback));
        assertEquals(userId, logged.getUser_id());
        ValidationException error = failure((RepositoryCallback<User> callback) -> repository.login("ana@email.com", "errada123", callback));
        assertEquals("Email ou senha incorretos", error.getMessage());
    }

    @Test
    public void shortPasswordAndFutureBirthDateAreRejected() {
        failure((RepositoryCallback<Long> callback) -> repository.register("Ana", "ana@email.com", "curta", BIRTH_DATE, callback));
        failure((RepositoryCallback<Long> callback) -> repository.register(
                "Ana", "ana@email.com", "senhaSegura", LocalDate.now().plusDays(1), callback));
    }
}
