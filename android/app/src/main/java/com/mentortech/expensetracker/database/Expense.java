package com.mentortech.expensetracker.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity representing an expense record in the SQLite database.
 */
@Entity(tableName = "expenses")
public class Expense {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String title;       // e.g. "Starbucks Coffee", "Uber to Airport"
    private double amount;      // e.g. 15.50
    private String date;        // e.g. "2026-10-16"
    private String category;    // Food, Travel, Bills, Shopping
    private String rawOcrText;  // Original raw OCR text scanned from receipt

    public Expense(String title, double amount, String date, String category, String rawOcrText) {
        this.title = title;
        this.amount = amount;
        this.date = date;
        this.category = category;
        this.rawOcrText = rawOcrText;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getRawOcrText() { return rawOcrText; }
    public void setRawOcrText(String rawOcrText) { this.rawOcrText = rawOcrText; }
}
