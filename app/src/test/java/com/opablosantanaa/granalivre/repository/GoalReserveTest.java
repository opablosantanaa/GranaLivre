package com.opablosantanaa.granalivre.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.opablosantanaa.granalivre.entity.Goal;

import org.junit.Before;
import org.junit.Test;

public class GoalReserveTest extends DatabaseTest {
    private GoalRepository goals;
    private long userId;
    private long accountId;
    private long goalId;

    @Before
    public void setUp() {
        goals = new GoalRepository(database, DIRECT, DIRECT);
        userId = createUser("goal@email.com");
        accountId = createAccount(userId, 100_000);
        Goal goal = new Goal();
        goal.setUser_id(userId);
        goal.setGoal_name("Viagem");
        goal.setGoal_target_cents(500_000);
        goalId = success(cb -> goals.insert(goal, cb));
    }

    private long saved() {
        return database.goalDao().findById(goalId).getGoal_saved_cents();
    }

    @Test
    public void savingReservesMoneyFromTheAccount() {
        DatabaseTest.<Void>success(cb -> goals.move(goalId, accountId, 30_000, cb));
        assertEquals(30_000, saved());
        assertEquals(70_000, database.accountDao().findBalance(accountId));
    }

    @Test
    public void withdrawingBeforeTheGoalReturnsMoneyToTheAccount() {
        DatabaseTest.<Void>success(cb -> goals.move(goalId, accountId, 30_000, cb));
        DatabaseTest.<Void>success(cb -> goals.move(goalId, accountId, -10_000, cb));
        assertEquals(20_000, saved());
        assertEquals(80_000, database.accountDao().findBalance(accountId));
    }

    @Test
    public void cannotWithdrawMoreThanSaved() {
        DatabaseTest.<Void>success(cb -> goals.move(goalId, accountId, 5_000, cb));
        ValidationException error = DatabaseTest.<Void>failure(cb -> goals.move(goalId, accountId, -6_000, cb));
        assertTrue(error.getMessage().contains("guardado"));
        assertEquals(5_000, saved());
    }

    @Test
    public void cannotReserveMoreThanTheAccountHas() {
        ValidationException error = DatabaseTest.<Void>failure(cb -> goals.move(goalId, accountId, 150_000, cb));
        assertTrue(error.getMessage().contains("Saldo insuficiente"));
        assertEquals(0, saved());
        assertEquals(100_000, database.accountDao().findBalance(accountId));
    }

    @Test
    public void deletingTheGoalReturnsReservedMoney() {
        DatabaseTest.<Void>success(cb -> goals.move(goalId, accountId, 40_000, cb));
        Goal goal = database.goalDao().findById(goalId);
        DatabaseTest.<Void>success(cb -> goals.delete(goal, cb));
        assertEquals(100_000, database.accountDao().findBalance(accountId));
    }
}
