package com.opablosantanaa.granalivre.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.opablosantanaa.granalivre.converter.Converters;

import java.time.LocalDate;

@Entity (tableName = "transfers",
        foreignKeys = {
                @ForeignKey(entity = User.class,
                        parentColumns = "user_id",
                        childColumns = "user_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Account.class,
                        parentColumns = "account_id",
                        childColumns = "transfer_from_account_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Account.class,
                        parentColumns = "account_id",
                        childColumns = "transfer_to_account_id",
                        onDelete = ForeignKey.CASCADE)},
        indices = {@Index("user_id"), @Index("transfer_from_account_id"), @Index("transfer_to_account_id")})
@TypeConverters(Converters.class)
public class Transfer {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "transfer_id")
    private long transfer_id;

    @ColumnInfo (name = "user_id")
    private long user_id;

    @ColumnInfo (name = "transfer_from_account_id")
    private long transfer_from_account_id;

    @ColumnInfo (name = "transfer_to_account_id")
    private long transfer_to_account_id;

    @ColumnInfo (name = "transfer_amount_cents")
    private long transfer_amount_cents;

    @NonNull
    @ColumnInfo (name = "transfer_date")
    private LocalDate transfer_date = LocalDate.now();

    @NonNull
    @ColumnInfo (name = "transfer_description")
    private String transfer_description = "";

    public long getTransfer_id() { return transfer_id; }
    public void setTransfer_id(long transfer_id) { this.transfer_id = transfer_id; }

    public long getUser_id() { return user_id; }
    public void setUser_id(long user_id) { this.user_id = user_id; }

    public long getTransfer_from_account_id() { return transfer_from_account_id; }
    public void setTransfer_from_account_id(long transfer_from_account_id) { this.transfer_from_account_id = transfer_from_account_id; }

    public long getTransfer_to_account_id() { return transfer_to_account_id; }
    public void setTransfer_to_account_id(long transfer_to_account_id) { this.transfer_to_account_id = transfer_to_account_id; }

    public long getTransfer_amount_cents() { return transfer_amount_cents; }
    public void setTransfer_amount_cents(long transfer_amount_cents) { this.transfer_amount_cents = transfer_amount_cents; }

    @NonNull
    public LocalDate getTransfer_date() { return transfer_date; }
    public void setTransfer_date(@NonNull LocalDate transfer_date) { this.transfer_date = transfer_date; }

    @NonNull
    public String getTransfer_description() { return transfer_description; }
    public void setTransfer_description(@NonNull String transfer_description) { this.transfer_description = transfer_description; }
}
