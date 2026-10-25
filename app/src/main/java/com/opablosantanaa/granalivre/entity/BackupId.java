package com.opablosantanaa.granalivre.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;

@Entity (tableName = "backup_ids",
        primaryKeys = {"backup_table", "local_id"},
        indices = {@Index(value = {"backup_table", "backup_uuid"}, unique = true)})
public class BackupId {
    @NonNull
    @ColumnInfo (name = "backup_table")
    private String backup_table = "";

    @ColumnInfo (name = "local_id")
    private long local_id;

    @NonNull
    @ColumnInfo (name = "backup_uuid")
    private String backup_uuid = "";

    public BackupId() {}

    @Ignore
    public BackupId(@NonNull String backup_table, long local_id, @NonNull String backup_uuid) {
        this.backup_table = backup_table;
        this.local_id = local_id;
        this.backup_uuid = backup_uuid;
    }

    @NonNull
    public String getBackup_table() { return backup_table; }
    public void setBackup_table(@NonNull String backup_table) { this.backup_table = backup_table; }

    public long getLocal_id() { return local_id; }
    public void setLocal_id(long local_id) { this.local_id = local_id; }

    @NonNull
    public String getBackup_uuid() { return backup_uuid; }
    public void setBackup_uuid(@NonNull String backup_uuid) { this.backup_uuid = backup_uuid; }
}
