package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.isBlank;
import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.opablosantanaa.granalivre.dao.CategoryDao;
import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.dao.UserDao;
import com.opablosantanaa.granalivre.domain.DefaultCategories;
import com.opablosantanaa.granalivre.domain.PasswordHasher;
import com.opablosantanaa.granalivre.entity.User;
import com.opablosantanaa.granalivre.relation.UserWithAccounts;
import com.opablosantanaa.granalivre.relation.UserWithCards;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.Executor;

public class UserRepository extends BaseRepository {
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String LOCAL_EMAIL_PREFIX = "local-";
    private static final String LOCAL_EMAIL_DOMAIN = "@granalivre.local";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserDao userDao;
    private final CategoryDao categoryDao;

    public UserRepository(Context context) {
        this(AppDatabase.getInstance(context), AppDatabase.databaseWriteExecutor, mainThreadExecutor());
    }

    public UserRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        userDao = database.userDao();
        categoryDao = database.categoryDao();
    }

    public LiveData<User> getById(long userId) {
        return userDao.getById(userId);
    }

    public LiveData<UserWithAccounts> getWithAccounts(long userId) {
        return userDao.getWithAccounts(userId);
    }

    public LiveData<UserWithCards> getWithCards(long userId) {
        return userDao.getWithCards(userId);
    }

    public void register(String name, String email, String password, LocalDate birthDate,
                         RepositoryCallback<Long> callback) {
        execute(() -> {
            require(birthDate != null, "Informe a data de nascimento");
            User user = new User();
            user.setUser_name(name == null ? "" : name.trim());
            user.setUser_email(normalizeEmail(email));
            user.setUser_birth_date(birthDate);
            validateProfile(user);
            validatePassword(password);
            user.setUser_password_hash(PasswordHasher.hash(password));
            return database.runInTransaction(() -> {
                long userId = userDao.insert(user);
                categoryDao.insertAll(DefaultCategories.create(userId));
                return userId;
            });
        }, callback);
    }

    public void createLocalProfile(String name, LocalDate birthDate, RepositoryCallback<Long> callback) {
        execute(() -> {
            require(birthDate != null, "Informe a data de nascimento");
            User user = new User();
            user.setUser_name(name == null ? "" : name.trim());
            user.setUser_email(LOCAL_EMAIL_PREFIX + UUID.randomUUID() + LOCAL_EMAIL_DOMAIN);
            user.setUser_birth_date(birthDate);
            validateProfile(user);
            return database.runInTransaction(() -> {
                long userId = userDao.insert(user);
                categoryDao.insertAll(DefaultCategories.create(userId));
                return userId;
            });
        }, callback);
    }

    public static boolean isLocalProfile(User user) {
        return user != null && user.getUser_email().startsWith(LOCAL_EMAIL_PREFIX)
                && user.getUser_email().endsWith(LOCAL_EMAIL_DOMAIN);
    }

    public void login(String email, String password, RepositoryCallback<User> callback) {
        execute(() -> {
            User user = userDao.findByEmail(normalizeEmail(email));
            require(user != null && PasswordHasher.verify(password, user.getUser_password_hash()),
                    "Email ou senha incorretos");
            return user;
        }, callback);
    }

    public void updateProfile(User user, RepositoryCallback<Void> callback) {
        execute(() -> {
            user.setUser_name(user.getUser_name().trim());
            user.setUser_email(normalizeEmail(user.getUser_email()));
            validateProfile(user);
            userDao.update(user);
            return null;
        }, callback);
    }

    public void changePassword(long userId, String currentPassword, String newPassword,
                               RepositoryCallback<Void> callback) {
        execute(() -> {
            User user = userDao.findById(userId);
            require(user != null, "Usuário não encontrado");
            require(PasswordHasher.verify(currentPassword, user.getUser_password_hash()), "Senha atual incorreta");
            validatePassword(newPassword);
            user.setUser_password_hash(PasswordHasher.hash(newPassword));
            userDao.update(user);
            return null;
        }, callback);
    }

    public void delete(User user, RepositoryCallback<Void> callback) {
        execute(() -> {
            userDao.delete(user);
            return null;
        }, callback);
    }

    private void validateProfile(User user) {
        require(!isBlank(user.getUser_name()), "Informe o nome");
        require(EMAIL_PATTERN.matcher(user.getUser_email()).matches(), "Email inválido");
        require(!user.getUser_birth_date().isAfter(LocalDate.now()), "Data de nascimento inválida");
        User sameEmail = userDao.findByEmail(user.getUser_email());
        require(sameEmail == null || sameEmail.getUser_id() == user.getUser_id(), "Email já cadastrado");
    }

    private void validatePassword(String password) {
        require(password != null && password.length() >= MIN_PASSWORD_LENGTH,
                "A senha deve ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres");
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
