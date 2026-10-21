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

/**
 * Dinheiro reservado de uma conta para uma meta (valor positivo) ou devolvido da meta para a conta (negativo).
 * Entra no saldo da conta: reservar diminui o saldo disponível; retirar devolve.
 */
@Entity (tableName = "goal_movements",
        foreignKeys = {
                @ForeignKey(entity = Goal.class,
                        parentColumns = "goal_id",
                        childColumns = "goal_id",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Account.class,
                        parentColumns = "account_id",
                        childColumns = "account_id",
                        onDelete = ForeignKey.CASCADE)},
        indices = {@Index("goal_id"), @Index("account_id")})
@TypeConverters(Converters.class)
public class GoalMovement {
    @PrimaryKey (autoGenerate = true)
    @ColumnInfo (name = "movement_id")
    private long movement_id;

    @ColumnInfo (name = "goal_id")
    private long goal_id;

    @ColumnInfo (name = "account_id")
    private long account_id;

    @ColumnInfo (name = "movement_amount_cents")
    private long movement_amount_cents;

    @NonNull
    @ColumnInfo (name = "movement_date")
    private LocalDate movement_date = LocalDate.now();

    public long getMovement_id() { return movement_id; }
    public void setMovement_id(long movement_id) { this.movement_id = movement_id; }

    public long getGoal_id() { return goal_id; }
    public void setGoal_id(long goal_id) { this.goal_id = goal_id; }

    public long getAccount_id() { return account_id; }
    public void setAccount_id(long account_id) { this.account_id = account_id; }

    public long getMovement_amount_cents() { return movement_amount_cents; }
    public void setMovement_amount_cents(long movement_amount_cents) { this.movement_amount_cents = movement_amount_cents; }

    @NonNull
    public LocalDate getMovement_date() { return movement_date; }
    public void setMovement_date(@NonNull LocalDate movement_date) { this.movement_date = movement_date; }
}
