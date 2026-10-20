package com.opablosantanaa.granalivre.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity (tableName = "accounts",
        foreignKeys = @ForeignKey(entity = User.class,
                parentColumns = "user_id",
                childColumns = "user_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("user_id")})
public class Account {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "account_id")
    private long account_id;

    @ColumnInfo (name = "user_id")
    private long user_id;

    @NonNull
    @ColumnInfo (name = "account_bank_name")
    private String account_bank_name = "";

    @NonNull
    @ColumnInfo (name = "account_type")
    private AccountType account_type = AccountType.CORRENTE;

    @ColumnInfo (name = "account_initial_balance_cents")
    private long account_initial_balance_cents;

    public long getAccount_id() { return account_id; }
    public void setAccount_id(long account_id) { this.account_id = account_id; }

    public long getUser_id() { return user_id; }
    public void setUser_id(long user_id) { this.user_id = user_id; }

    @NonNull
    public String getAccount_bank_name() { return account_bank_name; }
    public void setAccount_bank_name(@NonNull String account_bank_name) { this.account_bank_name = account_bank_name; }

    @NonNull
    public AccountType getAccount_type() { return account_type; }
    public void setAccount_type(@NonNull AccountType account_type) { this.account_type = account_type; }

    public long getAccount_initial_balance_cents() { return account_initial_balance_cents; }
    public void setAccount_initial_balance_cents(long account_initial_balance_cents) { this.account_initial_balance_cents = account_initial_balance_cents; }
}
