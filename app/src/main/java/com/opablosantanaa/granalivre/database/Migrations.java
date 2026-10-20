package com.opablosantanaa.granalivre.database;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public final class Migrations {
    private Migrations() {}

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN transaction_import_id TEXT");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_account_id_transaction_import_id "
                    + "ON transactions (account_id, transaction_import_id)");
        }
    };
}
