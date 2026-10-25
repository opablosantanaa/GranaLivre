package com.opablosantanaa.granalivre.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class BackupStorage {
    public static final String MIME = "application/json";
    static final String PREFIX = "granalivre-backup-";
    static final String SUFFIX = ".json";
    static final int KEEP = 10;

    private static final String PREFS = "grana_livre_backup";
    private static final String KEY_FOLDER = "folder";
    private static final String KEY_LAST = "last_automatic";
    private static final String KEY_PASSWORD_KEY = "password_key";
    private static final String KEY_PASSWORD_SALT = "password_salt";
    private static final String KEY_PASSWORD_ITERATIONS = "password_iterations";
    private static final long MAX_BYTES = 50L * 1024 * 1024;

    private final Context context;
    private final ContentResolver resolver;

    public BackupStorage(Context context) {
        this.context = context.getApplicationContext();
        resolver = this.context.getContentResolver();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String fileName(LocalDate date) {
        return PREFIX + date + SUFFIX;
    }

    @Nullable
    public Uri folder() {
        String saved = prefs().getString(KEY_FOLDER, null);
        if (saved == null) return null;
        Uri uri = Uri.parse(saved);
        for (UriPermission permission : resolver.getPersistedUriPermissions()) {
            if (permission.getUri().equals(uri) && permission.isWritePermission()) return uri;
        }
        return null;
    }

    public void setFolder(Uri tree) {
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        resolver.takePersistableUriPermission(tree, flags);
        String previous = prefs().getString(KEY_FOLDER, null);
        if (previous != null && !previous.equals(tree.toString())) {
            try {
                resolver.releasePersistableUriPermission(Uri.parse(previous), flags);
            } catch (SecurityException ignored) {
            }
        }
        prefs().edit().putString(KEY_FOLDER, tree.toString()).remove(KEY_LAST).apply();
    }

    @Nullable
    public String folderName() {
        Uri tree = folder();
        if (tree == null) return null;
        Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        return displayName(document);
    }

    @Nullable
    public BackupEnvelope.Key passwordKey() {
        SharedPreferences prefs = prefs();
        String key = prefs.getString(KEY_PASSWORD_KEY, null);
        String salt = prefs.getString(KEY_PASSWORD_SALT, null);
        if (key == null || salt == null) return null;
        return new BackupEnvelope.Key(Base64.getDecoder().decode(key), Base64.getDecoder().decode(salt),
                prefs.getInt(KEY_PASSWORD_ITERATIONS, 0));
    }

    public boolean hasPassword() {
        return passwordKey() != null;
    }

    void setPasswordKey(BackupEnvelope.Key key) {
        prefs().edit()
                .putString(KEY_PASSWORD_KEY, Base64.getEncoder().encodeToString(key.key))
                .putString(KEY_PASSWORD_SALT, Base64.getEncoder().encodeToString(key.salt))
                .putInt(KEY_PASSWORD_ITERATIONS, key.iterations)
                .apply();
    }

    public void clearPassword() {
        prefs().edit().remove(KEY_PASSWORD_KEY).remove(KEY_PASSWORD_SALT).remove(KEY_PASSWORD_ITERATIONS).apply();
    }

    @Nullable
    public LocalDate lastAutomatic() {
        String saved = prefs().getString(KEY_LAST, null);
        return saved == null ? null : LocalDate.parse(saved);
    }

    @Nullable
    public String displayName(Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    public void write(Uri document, String json) throws IOException {
        try (OutputStream out = resolver.openOutputStream(document, "wt")) {
            if (out == null) throw new IOException("Não foi possível gravar o arquivo");
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String read(Uri document) throws IOException {
        try (InputStream in = resolver.openInputStream(document)) {
            if (in == null) throw new IOException("Não foi possível abrir o arquivo");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_BYTES) throw new ValidationException("Arquivo grande demais para ser um backup");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public boolean writeAutomatic(String json, LocalDate today) throws IOException {
        Uri tree = folder();
        if (tree == null) return false;
        String name = fileName(today);
        List<String[]> children = backups(tree);
        Uri target = null;
        for (String[] child : children) {
            if (child[1].equals(name)) target = DocumentsContract.buildDocumentUriUsingTree(tree, child[0]);
        }
        if (target == null) {
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            target = DocumentsContract.createDocument(resolver, parent, MIME, name);
            if (target == null) throw new IOException("Não foi possível criar o arquivo de backup");
        }
        write(target, json);
        prefs().edit().putString(KEY_LAST, today.toString()).apply();
        prune(tree);
        return true;
    }

    private void prune(Uri tree) {
        List<String[]> children = backups(tree);
        Collections.sort(children, (a, b) -> b[1].compareTo(a[1]));
        for (int i = KEEP; i < children.size(); i++) {
            try {
                DocumentsContract.deleteDocument(resolver,
                        DocumentsContract.buildDocumentUriUsingTree(tree, children.get(i)[0]));
            } catch (IOException | RuntimeException ignored) {
            }
        }
    }

    private List<String[]> backups(Uri tree) {
        List<String[]> found = new ArrayList<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor cursor = resolver.query(children, columns, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                String name = cursor.getString(1);
                if (name != null && name.startsWith(PREFIX) && name.endsWith(SUFFIX)) {
                    found.add(new String[]{cursor.getString(0), name});
                }
            }
        }
        return found;
    }
}
