package com.opablosantanaa.granalivre.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.opablosantanaa.granalivre.converter.Converters;

import java.time.YearMonth;

@Entity (tableName = "budgets",
        foreignKeys = {
                @ForeignKey(entity = User.class,
                        parentColumns = "user_id",
                        childColumns = "user_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Category.class,
                        parentColumns = "category_id",
                        childColumns = "category_id",
                        onDelete = ForeignKey.CASCADE)},
        indices = {@Index("user_id"),
                @Index(value = {"category_id", "budget_month"}, unique = true)})
@TypeConverters(Converters.class)
public class Budget {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "budget_id")
    private long budget_id;

    @ColumnInfo (name = "user_id")
    private long user_id;

    @ColumnInfo (name = "category_id")
    private long category_id;

    @NonNull
    @ColumnInfo (name = "budget_month")
    private YearMonth budget_month = YearMonth.now();

    @ColumnInfo (name = "budget_limit_cents")
    private long budget_limit_cents;

    public long getBudget_id() { return budget_id; }
    public void setBudget_id(long budget_id) { this.budget_id = budget_id; }

    public long getUser_id() { return user_id; }
    public void setUser_id(long user_id) { this.user_id = user_id; }

    public long getCategory_id() { return category_id; }
    public void setCategory_id(long category_id) { this.category_id = category_id; }

    @NonNull
    public YearMonth getBudget_month() { return budget_month; }
    public void setBudget_month(@NonNull YearMonth budget_month) { this.budget_month = budget_month; }

    public long getBudget_limit_cents() { return budget_limit_cents; }
    public void setBudget_limit_cents(long budget_limit_cents) { this.budget_limit_cents = budget_limit_cents; }
}
