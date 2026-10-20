package com.opablosantanaa.granalivre.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity (tableName = "cards",
        foreignKeys = {
                @ForeignKey(entity = User.class,
                        parentColumns = "user_id",
                        childColumns = "user_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Account.class,
                        parentColumns = "account_id",
                        childColumns = "card_payment_account_id",
                        onDelete = ForeignKey.SET_NULL)},
        indices = {@Index("user_id"), @Index("card_payment_account_id")})
public class Card {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "card_id")
    private long card_id;

    @ColumnInfo (name = "user_id")
    private long user_id;

    @Nullable
    @ColumnInfo (name = "card_payment_account_id")
    private Long card_payment_account_id;

    @NonNull
    @ColumnInfo (name = "card_nickname")
    private String card_nickname = "";

    @NonNull
    @ColumnInfo (name = "card_brand")
    private CardBrand card_brand = CardBrand.OUTRA;

    @NonNull
    @ColumnInfo (name = "card_last_digits")
    private String card_last_digits = "";

    @ColumnInfo (name = "card_limit_cents")
    private long card_limit_cents;

    @ColumnInfo (name = "card_closing_day")
    private int card_closing_day;

    @ColumnInfo (name = "card_due_day")
    private int card_due_day;

    public long getCard_id() { return card_id; }
    public void setCard_id(long card_id) { this.card_id = card_id; }

    public long getUser_id() { return user_id; }
    public void setUser_id(long user_id) { this.user_id = user_id; }

    @Nullable
    public Long getCard_payment_account_id() { return card_payment_account_id; }
    public void setCard_payment_account_id(@Nullable Long card_payment_account_id) { this.card_payment_account_id = card_payment_account_id; }

    @NonNull
    public String getCard_nickname() { return card_nickname; }
    public void setCard_nickname(@NonNull String card_nickname) { this.card_nickname = card_nickname; }

    @NonNull
    public CardBrand getCard_brand() { return card_brand; }
    public void setCard_brand(@NonNull CardBrand card_brand) { this.card_brand = card_brand; }

    @NonNull
    public String getCard_last_digits() { return card_last_digits; }
    public void setCard_last_digits(@NonNull String card_last_digits) { this.card_last_digits = card_last_digits; }

    public long getCard_limit_cents() { return card_limit_cents; }
    public void setCard_limit_cents(long card_limit_cents) { this.card_limit_cents = card_limit_cents; }

    public int getCard_closing_day() { return card_closing_day; }
    public void setCard_closing_day(int card_closing_day) { this.card_closing_day = card_closing_day; }

    public int getCard_due_day() { return card_due_day; }
    public void setCard_due_day(int card_due_day) { this.card_due_day = card_due_day; }
}
