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

@Entity (tableName = "goals",
        foreignKeys = @ForeignKey(entity = User.class,
                parentColumns = "user_id",
                childColumns = "user_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("user_id")})
@TypeConverters(Converters.class)
public class Goal {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "goal_id")
    private long goal_id;

    @ColumnInfo (name = "user_id")
    private long user_id;

    @NonNull
    @ColumnInfo (name = "goal_name")
    private String goal_name = "";

    @ColumnInfo (name = "goal_target_cents")
    private long goal_target_cents;

    @ColumnInfo (name = "goal_saved_cents")
    private long goal_saved_cents;

    @Nullable
    @ColumnInfo (name = "goal_deadline")
    private LocalDate goal_deadline;

    public long getGoal_id() { return goal_id; }
    public void setGoal_id(long goal_id) { this.goal_id = goal_id; }

    public long getUser_id() { return user_id; }
    public void setUser_id(long user_id) { this.user_id = user_id; }

    @NonNull
    public String getGoal_name() { return goal_name; }
    public void setGoal_name(@NonNull String goal_name) { this.goal_name = goal_name; }

    public long getGoal_target_cents() { return goal_target_cents; }
    public void setGoal_target_cents(long goal_target_cents) { this.goal_target_cents = goal_target_cents; }

    public long getGoal_saved_cents() { return goal_saved_cents; }
    public void setGoal_saved_cents(long goal_saved_cents) { this.goal_saved_cents = goal_saved_cents; }

    @Nullable
    public LocalDate getGoal_deadline() { return goal_deadline; }
    public void setGoal_deadline(@Nullable LocalDate goal_deadline) { this.goal_deadline = goal_deadline; }
}
