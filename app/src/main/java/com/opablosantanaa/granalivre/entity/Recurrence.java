package com.opablosantanaa.granalivre.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.opablosantanaa.granalivre.converter.Converters;

import java.time.LocalDate;

@Entity (tableName = "recurrences",
        foreignKeys = {
                @ForeignKey(entity = User.class,
                        parentColumns = "user_id",
                        childColumns = "user_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Account.class,
                        parentColumns = "account_id",
                        childColumns = "account_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Card.class,
                        parentColumns = "card_id",
                        childColumns = "card_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Category.class,
                        parentColumns = "category_id",
                        childColumns = "category_id",
                        onDelete = ForeignKey.SET_NULL)},
        indices = {@Index("user_id"), @Index("account_id"), @Index("card_id"), @Index("category_id")})
@TypeConverters(Converters.class)
public class Recurrence {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "recurrence_id")
    private long recurrence_id;

    @ColumnInfo (name = "user_id")
    private long user_id;

    @Nullable
    @ColumnInfo (name = "account_id")
    private Long account_id;

    @Nullable
    @ColumnInfo (name = "card_id")
    private Long card_id;

    @Nullable
    @ColumnInfo (name = "category_id")
    private Long category_id;

    @NonNull
    @ColumnInfo (name = "recurrence_type")
    private TransactionType recurrence_type = TransactionType.DESPESA;

    @ColumnInfo (name = "recurrence_amount_cents")
    private long recurrence_amount_cents;

    @NonNull
    @ColumnInfo (name = "recurrence_description")
    private String recurrence_description = "";

    @NonNull
    @ColumnInfo (name = "recurrence_frequency")
    private RecurrenceFrequency recurrence_frequency = RecurrenceFrequency.MENSAL;

    @NonNull
    @ColumnInfo (name = "recurrence_start_date")
    private LocalDate recurrence_start_date = LocalDate.now();

    @Nullable
    @ColumnInfo (name = "recurrence_end_date")
    private LocalDate recurrence_end_date;

    @ColumnInfo (name = "recurrence_active")
    private boolean recurrence_active = true;

    @Nullable
    @ColumnInfo (name = "recurrence_last_generated_date")
    private LocalDate recurrence_last_generated_date;

    public long getRecurrence_id() { return recurrence_id; }
    public void setRecurrence_id(long recurrence_id) { this.recurrence_id = recurrence_id; }

    public long getUser_id() { return user_id; }
    public void setUser_id(long user_id) { this.user_id = user_id; }

    @Nullable
    public Long getAccount_id() { return account_id; }
    public void setAccount_id(@Nullable Long account_id) { this.account_id = account_id; }

    @Nullable
    public Long getCard_id() { return card_id; }
    public void setCard_id(@Nullable Long card_id) { this.card_id = card_id; }

    @Nullable
    public Long getCategory_id() { return category_id; }
    public void setCategory_id(@Nullable Long category_id) { this.category_id = category_id; }

    @NonNull
    public TransactionType getRecurrence_type() { return recurrence_type; }
    public void setRecurrence_type(@NonNull TransactionType recurrence_type) { this.recurrence_type = recurrence_type; }

    public long getRecurrence_amount_cents() { return recurrence_amount_cents; }
    public void setRecurrence_amount_cents(long recurrence_amount_cents) { this.recurrence_amount_cents = recurrence_amount_cents; }

    @NonNull
    public String getRecurrence_description() { return recurrence_description; }
    public void setRecurrence_description(@NonNull String recurrence_description) { this.recurrence_description = recurrence_description; }

    @NonNull
    public RecurrenceFrequency getRecurrence_frequency() { return recurrence_frequency; }
    public void setRecurrence_frequency(@NonNull RecurrenceFrequency recurrence_frequency) { this.recurrence_frequency = recurrence_frequency; }

    @NonNull
    public LocalDate getRecurrence_start_date() { return recurrence_start_date; }
    public void setRecurrence_start_date(@NonNull LocalDate recurrence_start_date) { this.recurrence_start_date = recurrence_start_date; }

    @Nullable
    public LocalDate getRecurrence_end_date() { return recurrence_end_date; }
    public void setRecurrence_end_date(@Nullable LocalDate recurrence_end_date) { this.recurrence_end_date = recurrence_end_date; }

    public boolean isRecurrence_active() { return recurrence_active; }
    public void setRecurrence_active(boolean recurrence_active) { this.recurrence_active = recurrence_active; }

    @Nullable
    public LocalDate getRecurrence_last_generated_date() { return recurrence_last_generated_date; }
    public void setRecurrence_last_generated_date(@Nullable LocalDate recurrence_last_generated_date) { this.recurrence_last_generated_date = recurrence_last_generated_date; }
}
