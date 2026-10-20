package com.opablosantanaa.granalivre.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MigrationTest {
    private static final String NAME = "migration-test.db";
    private static final String INSERT = "INSERT INTO transactions (user_id, account_id, transaction_type, "
            + "transaction_amount_cents, transaction_date, transaction_description, transaction_paid, "
            + "transaction_import_id) VALUES (1, 1, 'RECEITA', 100, 20001, 'A', 1, 'fit:1')";

    private static void createVersion1(File file) throws Exception {
        File schema = new File("schemas/com.opablosantanaa.granalivre.database.AppDatabase/1.json");
        JSONObject root = new JSONObject(new String(Files.readAllBytes(schema.toPath()), StandardCharsets.UTF_8));
        JSONObject db = root.getJSONObject("database");
        SQLiteDatabase sqlite = SQLiteDatabase.openOrCreateDatabase(file, null);
        JSONArray entities = db.getJSONArray("entities");
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.getJSONObject(i);
            String table = entity.getString("tableName");
            sqlite.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", table));
            JSONArray indices = entity.optJSONArray("indices");
            if (indices == null) continue;
            for (int j = 0; j < indices.length(); j++) {
                sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", table));
            }
        }
        JSONArray setup = db.getJSONArray("setupQueries");
        for (int i = 0; i < setup.length(); i++) sqlite.execSQL(setup.getString(i));
        sqlite.setVersion(1);

        ContentValues user = new ContentValues();
        user.put("user_id", 1);
        user.put("user_name", "Ana");
        user.put("user_birth_date", 9000);
        user.put("user_email", "ana@email.com");
        user.put("user_password_hash", "x");
        sqlite.insertOrThrow("users", null, user);
        ContentValues account = new ContentValues();
        account.put("account_id", 1);
        account.put("user_id", 1);
        account.put("account_bank_name", "Banco");
        account.put("account_type", "CORRENTE");
        account.put("account_initial_balance_cents", 1000);
        sqlite.insertOrThrow("accounts", null, account);
        ContentValues transaction = new ContentValues();
        transaction.put("user_id", 1);
        transaction.put("account_id", 1);
        transaction.put("transaction_type", "DESPESA");
        transaction.put("transaction_amount_cents", 250);
        transaction.put("transaction_date", 20_000);
        transaction.put("transaction_description", "Antigo");
        transaction.put("transaction_paid", 1);
        sqlite.insertOrThrow("transactions", null, transaction);
        sqlite.close();
    }

    @Test
    public void migratesVersion1KeepingDataAndAddingUniqueImportId() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath(NAME);
        file.getParentFile().mkdirs();
        file.delete();
        createVersion1(file);

        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build();
        assertEquals(750, database.accountDao().findBalance(1));
        assertTrue(database.financialTransactionDao().findImportIds(1).isEmpty());

        SupportSQLiteDatabase sql = database.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = sql.query("SELECT transaction_import_id FROM transactions")) {
            assertTrue(cursor.moveToFirst());
            assertTrue(cursor.isNull(0));
        }
        sql.execSQL(INSERT);
        assertThrows(SQLiteConstraintException.class, () -> sql.execSQL(INSERT));
        database.close();
    }
}
