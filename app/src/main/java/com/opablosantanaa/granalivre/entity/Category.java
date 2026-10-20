package com.opablosantanaa.granalivre.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity (tableName = "categories",
        foreignKeys = {
                @ForeignKey(entity = User.class,
                        parentColumns = "user_id",
                        childColumns = "user_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Category.class,
                        parentColumns = "category_id",
                        childColumns = "category_parent_id",
                        onDelete = ForeignKey.CASCADE)},
        indices = {@Index("user_id"), @Index("category_parent_id")})
public class Category {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "category_id")
    private long category_id;

    @ColumnInfo (name = "user_id")
    private long user_id;

    @Nullable
    @ColumnInfo (name = "category_parent_id")
    private Long category_parent_id;

    @NonNull
    @ColumnInfo (name = "category_name")
    private String category_name = "";

    @NonNull
    @ColumnInfo (name = "category_type")
    private TransactionType category_type = TransactionType.DESPESA;

    @Nullable
    @ColumnInfo (name = "category_icon")
    private String category_icon;

    @ColumnInfo (name = "category_color")
    private int category_color;

    public long getCategory_id() { return category_id; }
    public void setCategory_id(long category_id) { this.category_id = category_id; }

    public long getUser_id() { return user_id; }
    public void setUser_id(long user_id) { this.user_id = user_id; }

    @Nullable
    public Long getCategory_parent_id() { return category_parent_id; }
    public void setCategory_parent_id(@Nullable Long category_parent_id) { this.category_parent_id = category_parent_id; }

    @NonNull
    public String getCategory_name() { return category_name; }
    public void setCategory_name(@NonNull String category_name) { this.category_name = category_name; }

    @NonNull
    public TransactionType getCategory_type() { return category_type; }
    public void setCategory_type(@NonNull TransactionType category_type) { this.category_type = category_type; }

    @Nullable
    public String getCategory_icon() { return category_icon; }
    public void setCategory_icon(@Nullable String category_icon) { this.category_icon = category_icon; }

    public int getCategory_color() { return category_color; }
    public void setCategory_color(int category_color) { this.category_color = category_color; }
}
