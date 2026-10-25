package com.opablosantanaa.granalivre.repository;

import static com.opablosantanaa.granalivre.repository.ValidationException.require;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.opablosantanaa.granalivre.database.AppDatabase;
import com.opablosantanaa.granalivre.domain.BackupCipher;
import com.opablosantanaa.granalivre.entity.User;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executor;

public class BackupRepository extends BaseRepository {
    private final BackupStorage storage;

    public BackupRepository(Context context) {
        this(AppDatabase.getInstance(context), new BackupStorage(context), AppDatabase.databaseWriteExecutor,
                mainThreadExecutor());
    }

    public BackupRepository(AppDatabase database, BackupStorage storage, Executor backgroundExecutor,
                            Executor callbackExecutor) {
        super(database, backgroundExecutor, callbackExecutor);
        this.storage = storage;
    }

    public BackupStorage storage() {
        return storage;
    }

    String content(long userId) {
        String json = new BackupExporter(database).export(userId);
        BackupEnvelope.Key key = storage.passwordKey();
        return key == null ? json : BackupEnvelope.seal(json, key);
    }

    JSONObject load(Uri document, @Nullable String password) throws Exception {
        JSONObject root = BackupMerger.parse(storage.read(document));
        if (!BackupEnvelope.isEncrypted(root)) return root;
        if (password == null) throw new PasswordRequiredException();
        return BackupMerger.parse(BackupEnvelope.open(root, password));
    }

    public void exportTo(long userId, Uri document, RepositoryCallback<Void> callback) {
        execute(() -> {
            storage.write(document, content(userId));
            return null;
        }, callback);
    }

    public boolean writeAutomaticNow(long userId) throws Exception {
        if (storage.folder() == null) return false;
        return storage.writeAutomatic(content(userId), LocalDate.now());
    }

    public void setPassword(String password, RepositoryCallback<Void> callback) {
        execute(() -> {
            require(password != null && password.length() >= BackupCipher.MIN_PASSWORD_LENGTH,
                    "A senha precisa ter pelo menos " + BackupCipher.MIN_PASSWORD_LENGTH + " caracteres");
            storage.setPasswordKey(BackupEnvelope.Key.fromPassword(password));
            return null;
        }, callback);
    }

    public void preview(Uri document, @Nullable String password, RepositoryCallback<BackupMerger.Preview> callback) {
        execute(() -> BackupMerger.preview(load(document, password)), callback);
    }

    public void mergeInto(long userId, Uri document, @Nullable String password,
                          RepositoryCallback<BackupMerger.Result> callback) {
        execute(() -> new BackupMerger(database).merge(userId, load(document, password)), callback);
    }

    public void restoreProfile(Uri document, @Nullable String password, RepositoryCallback<Long> callback) {
        execute(() -> {
            JSONObject root = load(document, password);
            JSONObject profile = root.optJSONObject("profile");
            require(profile != null, "O backup está corrompido e não foi importado");
            User user = new User();
            user.setUser_name(profile.optString("name").trim());
            try {
                user.setUser_birth_date(LocalDate.parse(profile.optString("birthDate")));
            } catch (DateTimeParseException e) {
                throw new ValidationException("O backup está corrompido e não foi importado");
            }
            user.setUser_email(UserRepository.newLocalEmail());
            long userId = database.runInTransaction(() -> {
                long id = database.userDao().insert(user);
                new BackupMerger(database).merge(id, root);
                return id;
            });
            if (password != null) storage.setPasswordKey(BackupEnvelope.Key.fromPassword(password));
            return userId;
        }, callback);
    }
}
