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

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `goal_movements` (`movement_id` INTEGER PRIMARY KEY AUTOINCREMENT "
                    + "NOT NULL, `goal_id` INTEGER NOT NULL, `account_id` INTEGER NOT NULL, "
                    + "`movement_amount_cents` INTEGER NOT NULL, `movement_date` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`goal_id`) REFERENCES `goals`(`goal_id`) ON UPDATE NO ACTION ON DELETE CASCADE , "
                    + "FOREIGN KEY(`account_id`) REFERENCES `accounts`(`account_id`) ON UPDATE NO ACTION "
                    + "ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_movements_goal_id` ON `goal_movements` (`goal_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_movements_account_id` ON `goal_movements` (`account_id`)");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `backup_ids` (`backup_table` TEXT NOT NULL, "
                    + "`local_id` INTEGER NOT NULL, `backup_uuid` TEXT NOT NULL, "
                    + "PRIMARY KEY(`backup_table`, `local_id`))");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_backup_ids_backup_table_backup_uuid` "
                    + "ON `backup_ids` (`backup_table`, `backup_uuid`)");
        }
    };
}
