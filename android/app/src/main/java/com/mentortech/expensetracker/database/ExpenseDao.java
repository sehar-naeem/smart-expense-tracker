package com.mentortech.expensetracker.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data Access Object (DAO) providing SQL operations on the 'expenses' table.
 */
@Dao
public interface ExpenseDao {

    @Insert
    long insert(Expense expense);

    @Update
    void update(Expense expense);

    @Delete
    void delete(Expense expense);

    @Query("SELECT * FROM expenses ORDER BY id DESC")
    LiveData<List<Expense>> getAllExpensesLive();

    @Query("SELECT * FROM expenses ORDER BY id DESC")
    List<Expense> getAllExpensesSync();

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses")
    LiveData<Double> getTotalSpendingLive();

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses")
    Double getTotalSpendingSync();

    // Custom class for Category Breakdown in Charts
    class CategoryTotal {
        public String category;
        public double total;
    }

    @Query("SELECT category, SUM(amount) as total FROM expenses GROUP BY category")
    List<CategoryTotal> getCategoryBreakdownSync();

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    void deleteById(long expenseId);
}
