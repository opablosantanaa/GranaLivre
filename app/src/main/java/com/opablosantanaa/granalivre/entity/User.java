package com.opablosantanaa.granalivre.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.opablosantanaa.granalivre.converter.Converters;

import java.time.LocalDate;
import java.time.Period;

@Entity (tableName = "users",
        indices = {@Index(value = "user_email", unique = true)})
@TypeConverters(Converters.class)
public class User {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "user_id")
    private long user_id;

    @NonNull
    @ColumnInfo (name = "user_name")
    private String user_name = "";

    @NonNull
    @ColumnInfo (name = "user_birth_date")
    private LocalDate user_birth_date = LocalDate.now();

    @NonNull
    @ColumnInfo (name = "user_email")
    private String user_email = "";

    @NonNull
    @ColumnInfo (name = "user_password_hash")
    private String user_password_hash = "";


    public long getUser_id() { return user_id; }
    public void setUser_id(long user_id) { this.user_id = user_id; }

    @NonNull
    public String getUser_name() { return user_name; }
    public void setUser_name(@NonNull String user_name) { this.user_name = user_name; }

    @NonNull
    public LocalDate getUser_birth_date() { return user_birth_date; }
    public void setUser_birth_date(@NonNull LocalDate user_birth_date) { this.user_birth_date = user_birth_date; }

    public int getAge() { return Period.between(user_birth_date, LocalDate.now()).getYears(); }

    @NonNull
    public String getUser_email() { return user_email; }
    public void setUser_email(@NonNull String user_email) { this.user_email = user_email; }

    @NonNull
    public String getUser_password_hash() { return user_password_hash; }
    public void setUser_password_hash(@NonNull String user_password_hash) { this.user_password_hash = user_password_hash; }
}
