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

@Entity (tableName = "installments",
        foreignKeys = @ForeignKey(entity = User.class,
                parentColumns = "user_id",
                childColumns = "user_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("user_id")})
@TypeConverters(Converters.class)
public class Installment {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "installment_id")
    private long installment_id;

    @ColumnInfo (name = "user_id")
    private long user_id;

    @NonNull
    @ColumnInfo (name = "installment_description")
    private String installment_description = "";

    @ColumnInfo (name = "installment_total_cents")
    private long installment_total_cents;

    @ColumnInfo (name = "installment_count")
    private int installment_count;

    @NonNull
    @ColumnInfo (name = "installment_first_date")
    private LocalDate installment_first_date = LocalDate.now();

    public long getInstallment_id() { return installment_id; }
    public void setInstallment_id(long installment_id) { this.installment_id = installment_id; }

    public long getUser_id() { return user_id; }
    public void setUser_id(long user_id) { this.user_id = user_id; }

    @NonNull
    public String getInstallment_description() { return installment_description; }
    public void setInstallment_description(@NonNull String installment_description) { this.installment_description = installment_description; }

    public long getInstallment_total_cents() { return installment_total_cents; }
    public void setInstallment_total_cents(long installment_total_cents) { this.installment_total_cents = installment_total_cents; }

    public int getInstallment_count() { return installment_count; }
    public void setInstallment_count(int installment_count) { this.installment_count = installment_count; }

    @NonNull
    public LocalDate getInstallment_first_date() { return installment_first_date; }
    public void setInstallment_first_date(@NonNull LocalDate installment_first_date) { this.installment_first_date = installment_first_date; }
}
