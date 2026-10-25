package com.opablosantanaa.granalivre.repository;

import com.opablosantanaa.granalivre.domain.BackupCipher;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

public final class BackupEnvelope {
    private static final String KEY_ENCRYPTED = "encrypted";

    public static final class Key {
        final byte[] key;
        final byte[] salt;
        final int iterations;

        public Key(byte[] key, byte[] salt, int iterations) {
            this.key = key;
            this.salt = salt;
            this.iterations = iterations;
        }

        public static Key fromPassword(String password) {
            byte[] salt = BackupCipher.newSalt();
            return new Key(BackupCipher.deriveKey(password, salt, BackupCipher.ITERATIONS), salt,
                    BackupCipher.ITERATIONS);
        }
    }

    private BackupEnvelope() {}

    public static boolean isEncrypted(JSONObject root) {
        return root.optBoolean(KEY_ENCRYPTED, false);
    }

    public static String seal(String plainJson, Key key) {
        try {
            JSONObject plain = new JSONObject(plainJson);
            BackupCipher.Sealed sealed = BackupCipher.encrypt(key.key, plainJson.getBytes(StandardCharsets.UTF_8));
            return new JSONObject()
                    .put("format", BackupExporter.FORMAT)
                    .put("version", BackupExporter.VERSION)
                    .put(KEY_ENCRYPTED, true)
                    .put("createdAt", plain.optString("createdAt"))
                    .put("kdf", new JSONObject()
                            .put("algorithm", BackupCipher.KDF)
                            .put("iterations", key.iterations)
                            .put("salt", encode(key.salt)))
                    .put("cipher", new JSONObject()
                            .put("algorithm", BackupCipher.CIPHER)
                            .put("iv", encode(sealed.iv)))
                    .put("data", encode(sealed.data))
                    .toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String open(JSONObject envelope, String password) {
        try {
            JSONObject kdf = envelope.getJSONObject("kdf");
            JSONObject cipher = envelope.getJSONObject("cipher");
            ValidationException.require(BackupCipher.KDF.equals(kdf.getString("algorithm"))
                    && BackupCipher.CIPHER.equals(cipher.getString("algorithm")),
                    "Backup protegido de um jeito que esta versão do app não reconhece");
            byte[] key = BackupCipher.deriveKey(password, decode(kdf.getString("salt")), kdf.getInt("iterations"));
            byte[] plain = BackupCipher.decrypt(key, decode(cipher.getString("iv")), decode(envelope.getString("data")));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new PasswordRequiredException("Senha incorreta");
        } catch (JSONException | IllegalArgumentException e) {
            throw new ValidationException("O backup está corrompido e não foi importado");
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] decode(String text) {
        return Base64.getDecoder().decode(text);
    }
}
