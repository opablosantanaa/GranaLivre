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

@Entity (tableName = "transactions",
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
                @ForeignKey(entity = Invoice.class,
                        parentColumns = "invoice_id",
                        childColumns = "invoice_id",
                        onDelete = ForeignKey.SET_NULL),
                @ForeignKey(entity = Category.class,
                        parentColumns = "category_id",
                        childColumns = "category_id",
                        onDelete = ForeignKey.SET_NULL),
                @ForeignKey(entity = Installment.class,
                        parentColumns = "installment_id",
                        childColumns = "installment_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Recurrence.class,
                        parentColumns = "recurrence_id",
                        childColumns = "recurrence_id",
                        onDelete = ForeignKey.SET_NULL)},
        indices = {@Index("user_id"), @Index("account_id"), @Index("card_id"), @Index("invoice_id"),
                @Index("category_id"), @Index("installment_id"), @Index("recurrence_id"),
                @Index("transaction_date"),
                @Index(value = {"account_id", "transaction_import_id"}, unique = true)})
@TypeConverters(Converters.class)
public class FinancialTransaction {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "transaction_id")
    private long transaction_id;

    @ColumnInfo (name = "user_id")
    private long user_id;

    @Nullable
    @ColumnInfo (name = "account_id")
    private Long account_id;

    @Nullable
    @ColumnInfo (name = "card_id")
    private Long card_id;

    @Nullable
    @ColumnInfo (name = "invoice_id")
    private Long invoice_id;

    @Nullable
    @ColumnInfo (name = "category_id")
    private Long category_id;

    @Nullable
    @ColumnInfo (name = "installment_id")
    private Long installment_id;

    @Nullable
    @ColumnInfo (name = "transaction_installment_number")
    private Integer transaction_installment_number;

    @Nullable
    @ColumnInfo (name = "recurrence_id")
    private Long recurrence_id;

    @NonNull
    @ColumnInfo (name = "transaction_type")
    private TransactionType transaction_type = TransactionType.DESPESA;

    @ColumnInfo (name = "transaction_amount_cents")
    private long transaction_amount_cents;

    @NonNull
    @ColumnInfo (name = "transaction_date")
    private LocalDate transaction_date = LocalDate.now();

    @NonNull
    @ColumnInfo (name = "transaction_description")
    private String transaction_description = "";

    @ColumnInfo (name = "transaction_paid")
    private boolean transaction_paid = true;

    @Nullable
    @ColumnInfo (name = "transaction_import_id")
    private String transaction_import_id;

    public long getTransaction_id() { return transaction_id; }
    public void setTransaction_id(long transaction_id) { this.transaction_id = transaction_id; }

    public long getUser_id() { return user_id; }
    public void setUser_id(long user_id) { this.user_id = user_id; }

    @Nullable
    public Long getAccount_id() { return account_id; }
    public void setAccount_id(@Nullable Long account_id) { this.account_id = account_id; }

    @Nullable
    public Long getCard_id() { return card_id; }
    public void setCard_id(@Nullable Long card_id) { this.card_id = card_id; }

    @Nullable
    public Long getInvoice_id() { return invoice_id; }
    public void setInvoice_id(@Nullable Long invoice_id) { this.invoice_id = invoice_id; }

    @Nullable
    public Long getCategory_id() { return category_id; }
    public void setCategory_id(@Nullable Long category_id) { this.category_id = category_id; }

    @Nullable
    public Long getInstallment_id() { return installment_id; }
    public void setInstallment_id(@Nullable Long installment_id) { this.installment_id = installment_id; }

    @Nullable
    public Integer getTransaction_installment_number() { return transaction_installment_number; }
    public void setTransaction_installment_number(@Nullable Integer transaction_installment_number) { this.transaction_installment_number = transaction_installment_number; }

    @Nullable
    public Long getRecurrence_id() { return recurrence_id; }
    public void setRecurrence_id(@Nullable Long recurrence_id) { this.recurrence_id = recurrence_id; }

    @NonNull
    public TransactionType getTransaction_type() { return transaction_type; }
    public void setTransaction_type(@NonNull TransactionType transaction_type) { this.transaction_type = transaction_type; }

    public long getTransaction_amount_cents() { return transaction_amount_cents; }
    public void setTransaction_amount_cents(long transaction_amount_cents) { this.transaction_amount_cents = transaction_amount_cents; }

    @NonNull
    public LocalDate getTransaction_date() { return transaction_date; }
    public void setTransaction_date(@NonNull LocalDate transaction_date) { this.transaction_date = transaction_date; }

    @NonNull
    public String getTransaction_description() { return transaction_description; }
    public void setTransaction_description(@NonNull String transaction_description) { this.transaction_description = transaction_description; }

    public boolean isTransaction_paid() { return transaction_paid; }
    public void setTransaction_paid(boolean transaction_paid) { this.transaction_paid = transaction_paid; }

    @Nullable
    public String getTransaction_import_id() { return transaction_import_id; }
    public void setTransaction_import_id(@Nullable String transaction_import_id) { this.transaction_import_id = transaction_import_id; }
}
