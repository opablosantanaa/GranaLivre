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
import java.time.YearMonth;

@Entity (tableName = "invoices",
        foreignKeys = {
                @ForeignKey(entity = Card.class,
                        parentColumns = "card_id",
                        childColumns = "card_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Account.class,
                        parentColumns = "account_id",
                        childColumns = "invoice_payment_account_id",
                        onDelete = ForeignKey.SET_NULL)},
        indices = {@Index(value = {"card_id", "invoice_reference_month"}, unique = true),
                @Index("invoice_payment_account_id")})
@TypeConverters(Converters.class)
public class Invoice {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "invoice_id")
    private long invoice_id;

    @ColumnInfo (name = "card_id")
    private long card_id;

    @NonNull
    @ColumnInfo (name = "invoice_reference_month")
    private YearMonth invoice_reference_month = YearMonth.now();

    @NonNull
    @ColumnInfo (name = "invoice_closing_date")
    private LocalDate invoice_closing_date = LocalDate.now();

    @NonNull
    @ColumnInfo (name = "invoice_due_date")
    private LocalDate invoice_due_date = LocalDate.now();

    @NonNull
    @ColumnInfo (name = "invoice_status")
    private InvoiceStatus invoice_status = InvoiceStatus.ABERTA;

    @Nullable
    @ColumnInfo (name = "invoice_payment_account_id")
    private Long invoice_payment_account_id;

    @ColumnInfo (name = "invoice_paid_cents")
    private long invoice_paid_cents;

    @Nullable
    @ColumnInfo (name = "invoice_paid_date")
    private LocalDate invoice_paid_date;

    public long getInvoice_id() { return invoice_id; }
    public void setInvoice_id(long invoice_id) { this.invoice_id = invoice_id; }

    public long getCard_id() { return card_id; }
    public void setCard_id(long card_id) { this.card_id = card_id; }

    @NonNull
    public YearMonth getInvoice_reference_month() { return invoice_reference_month; }
    public void setInvoice_reference_month(@NonNull YearMonth invoice_reference_month) { this.invoice_reference_month = invoice_reference_month; }

    @NonNull
    public LocalDate getInvoice_closing_date() { return invoice_closing_date; }
    public void setInvoice_closing_date(@NonNull LocalDate invoice_closing_date) { this.invoice_closing_date = invoice_closing_date; }

    @NonNull
    public LocalDate getInvoice_due_date() { return invoice_due_date; }
    public void setInvoice_due_date(@NonNull LocalDate invoice_due_date) { this.invoice_due_date = invoice_due_date; }

    @NonNull
    public InvoiceStatus getInvoice_status() { return invoice_status; }
    public void setInvoice_status(@NonNull InvoiceStatus invoice_status) { this.invoice_status = invoice_status; }

    @Nullable
    public Long getInvoice_payment_account_id() { return invoice_payment_account_id; }
    public void setInvoice_payment_account_id(@Nullable Long invoice_payment_account_id) { this.invoice_payment_account_id = invoice_payment_account_id; }

    public long getInvoice_paid_cents() { return invoice_paid_cents; }
    public void setInvoice_paid_cents(long invoice_paid_cents) { this.invoice_paid_cents = invoice_paid_cents; }

    @Nullable
    public LocalDate getInvoice_paid_date() { return invoice_paid_date; }
    public void setInvoice_paid_date(@Nullable LocalDate invoice_paid_date) { this.invoice_paid_date = invoice_paid_date; }
}
