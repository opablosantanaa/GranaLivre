package com.opablosantanaa.granalivre.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.opablosantanaa.granalivre.converter.Converters;
import com.opablosantanaa.granalivre.dao.AccountDao;
import com.opablosantanaa.granalivre.dao.BudgetDao;
import com.opablosantanaa.granalivre.dao.CardDao;
import com.opablosantanaa.granalivre.dao.CategoryDao;
import com.opablosantanaa.granalivre.dao.FinancialTransactionDao;
import com.opablosantanaa.granalivre.dao.GoalDao;
import com.opablosantanaa.granalivre.dao.InstallmentDao;
import com.opablosantanaa.granalivre.dao.InvoiceDao;
import com.opablosantanaa.granalivre.dao.RecurrenceDao;
import com.opablosantanaa.granalivre.dao.TransferDao;
import com.opablosantanaa.granalivre.dao.UserDao;
import com.opablosantanaa.granalivre.entity.Account;
import com.opablosantanaa.granalivre.entity.Budget;
import com.opablosantanaa.granalivre.entity.Card;
import com.opablosantanaa.granalivre.entity.Category;
import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Goal;
import com.opablosantanaa.granalivre.entity.Installment;
import com.opablosantanaa.granalivre.entity.Invoice;
import com.opablosantanaa.granalivre.entity.Recurrence;
import com.opablosantanaa.granalivre.entity.Transfer;
import com.opablosantanaa.granalivre.entity.User;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {
        User.class,
        Account.class,
        Card.class,
        Category.class,
        Invoice.class,
        Transfer.class,
        Installment.class,
        Recurrence.class,
        FinancialTransaction.class,
        Budget.class,
        Goal.class},
        version = 1,
        exportSchema = true)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "grana_livre.db";
    private static final int NUMBER_OF_THREADS = 4;

    private static volatile AppDatabase INSTANCE;

    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public abstract UserDao userDao();
    public abstract AccountDao accountDao();
    public abstract CardDao cardDao();
    public abstract CategoryDao categoryDao();
    public abstract InvoiceDao invoiceDao();
    public abstract TransferDao transferDao();
    public abstract InstallmentDao installmentDao();
    public abstract RecurrenceDao recurrenceDao();
    public abstract FinancialTransactionDao financialTransactionDao();
    public abstract BudgetDao budgetDao();
    public abstract GoalDao goalDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, DATABASE_NAME)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
