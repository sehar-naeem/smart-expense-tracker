package com.mentortech.expensetracker.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.mentortech.expensetracker.R;
import com.mentortech.expensetracker.database.AppDatabase;
import com.mentortech.expensetracker.database.Expense;
import com.mentortech.expensetracker.utils.SessionManager;
import com.mentortech.expensetracker.worker.BudgetAlertWorker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private TextView tvUserGreeting, tvTotalSpending, tvBudgetStatus, tvEmptyState;
    private TextView tvBudgetStatusBadge, tvRemainingBudget, tvBudgetPercentage;
    private ProgressBar pbBudgetProgress;
    private MaterialButton btnChangeCurrency, btnRewards, btnSetBudget;
    private RecyclerView rvExpenses;
    private ExpenseAdapter adapter;
    private AppDatabase db;
    private SessionManager sessionManager;

    private SharedPreferences prefs;
    private float monthlyBudgetLimit = 500.0f;
    private double currentTotalSpending = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);
        // Safety check: redirect to Login if not logged in
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        db = AppDatabase.getInstance(this);
        prefs = getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE);
        monthlyBudgetLimit = prefs.getFloat("monthly_budget", 500.0f);

        initViews();
        setupRecyclerView();
        setupFilterChips();
        observeDatabase();
        scheduleBudgetWorker();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBudgetUI();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void initViews() {
        tvUserGreeting = findViewById(R.id.tvUserGreeting);
        tvTotalSpending = findViewById(R.id.tvTotalSpending);
        tvBudgetStatus = findViewById(R.id.tvBudgetStatus);
        pbBudgetProgress = findViewById(R.id.pbBudgetProgress);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        rvExpenses = findViewById(R.id.rvExpenses);
        btnChangeCurrency = findViewById(R.id.btnChangeCurrency);
        btnRewards = findViewById(R.id.btnRewards);
        btnSetBudget = findViewById(R.id.btnSetBudget);
        tvBudgetStatusBadge = findViewById(R.id.tvBudgetStatusBadge);
        tvRemainingBudget = findViewById(R.id.tvRemainingBudget);
        tvBudgetPercentage = findViewById(R.id.tvBudgetPercentage);

        // Personalized user greeting
        tvUserGreeting.setText("Hello, " + sessionManager.getUserName() + " 👋");

        if (btnChangeCurrency != null) {
            btnChangeCurrency.setOnClickListener(v -> showCurrencySelectionDialog());
        }

        if (btnSetBudget != null) {
            btnSetBudget.setOnClickListener(v -> showSetBudgetDialog());
        }

        if (btnRewards != null) {
            btnRewards.setOnClickListener(v -> showMonthlySavingsCelebrationDialog());
        }

        if (tvBudgetStatusBadge != null) {
            tvBudgetStatusBadge.setOnClickListener(v -> showMonthlySavingsCelebrationDialog());
        }

        findViewById(R.id.btnScanReceipt).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ScanReceiptActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnViewAnalytics).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AnalyticsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> handleLogout());
        findViewById(R.id.fabAddManual).setOnClickListener(v -> showManualAddDialog());
    }

    private void showCurrencySelectionDialog() {
        String[] currencies = new String[] {
            "Pakistani Rupee (Rs.)",
            "US Dollar ($)",
            "Euro (€)",
            "British Pound (£)",
            "Indian Rupee (₹)",
            "UAE Dirham (AED)",
            "Saudi Riyal (SAR)"
        };
        String[] symbols = new String[] {
            "Rs. ",
            "$",
            "€",
            "£",
            "₹",
            "AED ",
            "SAR "
        };

        int checkedItem = 0;
        String current = sessionManager.getCurrencySymbol();
        for (int i = 0; i < symbols.length; i++) {
            if (symbols[i].trim().equalsIgnoreCase(current.trim())) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Currency")
                .setSingleChoiceItems(currencies, checkedItem, (dialog, which) -> {
                    sessionManager.setCurrencySymbol(symbols[which]);
                    btnChangeCurrency.setText(symbols[which].trim());
                    updateBudgetUI();
                    if (adapter != null) adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Currency set to " + symbols[which].trim(), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of your account?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    sessionManager.logout();
                    Toast.makeText(MainActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupRecyclerView() {
        rvExpenses.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExpenseAdapter(
                expense -> showEditExpenseDialog(expense),
                expense -> showDeleteDialog(expense)
        );
        rvExpenses.setAdapter(adapter);
    }

    private void setupFilterChips() {
        ChipGroup chipGroup = findViewById(R.id.chipGroupFilter);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipFood) {
                adapter.filterByCategory("Food");
            } else if (id == R.id.chipTravel) {
                adapter.filterByCategory("Travel");
            } else if (id == R.id.chipBills) {
                adapter.filterByCategory("Bills");
            } else if (id == R.id.chipShopping) {
                adapter.filterByCategory("Shopping");
            } else {
                adapter.filterByCategory("All");
            }
        });
    }

    private void observeDatabase() {
        // 1. Observe all expenses live
        db.expenseDao().getAllExpensesLive().observe(this, expenses -> {
            if (expenses == null || expenses.isEmpty()) {
                tvEmptyState.setVisibility(View.VISIBLE);
                rvExpenses.setVisibility(View.GONE);
            } else {
                tvEmptyState.setVisibility(View.GONE);
                rvExpenses.setVisibility(View.VISIBLE);
                adapter.setExpenses(expenses);
            }
        });

        // 2. Observe total spending live & update budget progress
        db.expenseDao().getTotalSpendingLive().observe(this, total -> {
            currentTotalSpending = total != null ? total : 0.0;
            updateBudgetUI();
        });
    }

    private void updateBudgetUI() {
        String curr = sessionManager.getCurrencySymbol();
        tvTotalSpending.setText(String.format(Locale.US, "%s%.2f", curr, currentTotalSpending));

        int percentage = monthlyBudgetLimit > 0 ? (int) Math.round((currentTotalSpending / monthlyBudgetLimit) * 100) : 0;
        pbBudgetProgress.setProgress(Math.min(100, percentage));

        double remaining = monthlyBudgetLimit - currentTotalSpending;
        if (tvRemainingBudget != null) {
            if (remaining >= 0) {
                tvRemainingBudget.setText(String.format(Locale.US, "💰 Remaining: %s%.2f", curr, remaining));
                tvRemainingBudget.setTextColor(0xFF34D399);
            } else {
                tvRemainingBudget.setText(String.format(Locale.US, "🚨 Exceeded by: %s%.2f", curr, Math.abs(remaining)));
                tvRemainingBudget.setTextColor(0xFFF87171);
            }
        }

        if (tvBudgetPercentage != null) {
            tvBudgetPercentage.setText(String.format(Locale.US, "%d%% Spent", percentage));
        }

        if (tvBudgetStatusBadge != null) {
            if (percentage < 75) {
                tvBudgetStatusBadge.setText("🟢 Safe (Under Budget)");
                tvBudgetStatusBadge.setTextColor(0xFF34D399);
            } else if (percentage <= 100) {
                tvBudgetStatusBadge.setText("⚠️ Caution (Nearing Limit)");
                tvBudgetStatusBadge.setTextColor(0xFFFBBF24);
            } else {
                tvBudgetStatusBadge.setText("🚨 Limit Exceeded!");
                tvBudgetStatusBadge.setTextColor(0xFFF87171);
            }
        }

        if (btnChangeCurrency != null) {
            btnChangeCurrency.setText("💱 " + curr.trim());
        }

        if (btnSetBudget != null) {
            btnSetBudget.setText(String.format(Locale.US, "🎯 Limit: %s%.0f ✎", curr, monthlyBudgetLimit));
        }

        tvBudgetStatus.setText(String.format(Locale.US,
                "Spent %s%.2f of %s%.2f monthly budget",
                curr, currentTotalSpending, curr, monthlyBudgetLimit));
    }

    private void showMonthlySavingsCelebrationDialog() {
        String curr = sessionManager.getCurrencySymbol();
        double remaining = monthlyBudgetLimit - currentTotalSpending;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        if (currentTotalSpending <= monthlyBudgetLimit) {
            builder.setTitle("🏆 Monthly Savings Champion!")
                    .setMessage(
                            "🎉 Congratulations " + sessionManager.getUserName() + "!\n\n" +
                            "You have successfully stayed within your monthly budget!\n\n" +
                            "📊 Current Summary:\n" +
                            "• Total Spent: " + String.format(Locale.US, "%s%.2f", curr, currentTotalSpending) + "\n" +
                            "• Monthly Target: " + String.format(Locale.US, "%s%.2f", curr, monthlyBudgetLimit) + "\n" +
                            "• Money Saved / Remaining: " + String.format(Locale.US, "%s%.2f", curr, remaining) + "\n\n" +
                            "🎁 Unlocked Prizes & Badges:\n" +
                            "🥇 Golden Saver Trophy (Top 5% Discipline)\n" +
                            "🛡️ Budget Guardian Badge (Finances Protected)\n" +
                            "⚡ AI Receipt Scanner Pro VIP Status (Active)\n" +
                            "⭐ +500 Financial IQ Points Awarded!\n\n" +
                            "Keep up the brilliant financial discipline!"
                    )
                    .setIcon(android.R.drawable.star_big_on)
                    .setPositiveButton("Claim Rewards 🌟", (dialog, which) -> {
                        Toast.makeText(this, "🎉 Rewards Claimed! You are a Verified Master Saver!", Toast.LENGTH_LONG).show();
                    })
                    .setNeutralButton("View Analytics", (dialog, which) -> {
                        startActivity(new Intent(MainActivity.this, AnalyticsActivity.class));
                    })
                    .show();
        } else {
            builder.setTitle("📊 Budget Health & Tips")
                    .setMessage(
                            "⚠️ Monthly Limit Exceeded\n\n" +
                            "You have spent " + String.format(Locale.US, "%s%.2f", curr, currentTotalSpending) + 
                            ", which is " + String.format(Locale.US, "%s%.2f", curr, Math.abs(remaining)) + 
                            " over your " + String.format(Locale.US, "%s%.2f", curr, monthlyBudgetLimit) + " limit.\n\n" +
                            "💡 How to win next month's Trophy:\n" +
                            "1. Check the Analytics tab to identify high-spending categories.\n" +
                            "2. If prices or utility bills rose, adjust your monthly limit accordingly.\n" +
                            "3. Scan every receipt right at checkout to track real-time totals!"
                    )
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton("Adjust Budget Limit", (dialog, which) -> showSetBudgetDialog())
                    .setNegativeButton("Close", null)
                    .show();
        }
    }

    private void showSetBudgetDialog() {
        String curr = sessionManager.getCurrencySymbol();
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Monthly Budget (" + curr.trim() + ")");
        input.setText(String.format(Locale.US, "%.0f", monthlyBudgetLimit));
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(this)
                .setTitle("Set Monthly Budget Limit")
                .setMessage("Enter your target budget. You will receive alerts if spending exceeds this limit.")
                .setView(input)
                .setPositiveButton("Set Limit", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        monthlyBudgetLimit = Float.parseFloat(value);
                        prefs.edit().putFloat("monthly_budget", monthlyBudgetLimit).apply();
                        updateBudgetUI();
                        Toast.makeText(this, "Budget updated to " + curr + value, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showManualAddDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null);
        EditText etTitle = dialogView.findViewById(R.id.dialogEtTitle);
        EditText etAmount = dialogView.findViewById(R.id.dialogEtAmount);
        EditText etDate = dialogView.findViewById(R.id.dialogEtDate);
        Spinner spinner = dialogView.findViewById(R.id.dialogSpinnerCategory);

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        etDate.setText(today);

        String[] categories = new String[]{"Food", "Travel", "Bills", "Shopping"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, categories);
        spinner.setAdapter(spinnerAdapter);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Save", (d, which) -> {
                    String title = etTitle.getText().toString().trim();
                    String amountStr = etAmount.getText().toString().trim();
                    String date = etDate.getText().toString().trim();
                    String category = spinner.getSelectedItem().toString();

                    if (title.isEmpty() || amountStr.isEmpty()) {
                        Toast.makeText(this, "Title and Amount are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double amount = Double.parseDouble(amountStr);
                    Expense expense = new Expense(title, amount, date, category, "Manual Entry");

                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        db.expenseDao().insert(expense);
                        BudgetAlertWorker.checkAndNotifyBudget(MainActivity.this);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditExpenseDialog(Expense expense) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null);
        TextView titleLabel = dialogView.findViewById(R.id.dialogTitle);
        if (titleLabel != null) titleLabel.setText("Edit Expense");

        EditText etTitle = dialogView.findViewById(R.id.dialogEtTitle);
        EditText etAmount = dialogView.findViewById(R.id.dialogEtAmount);
        EditText etDate = dialogView.findViewById(R.id.dialogEtDate);
        Spinner spinner = dialogView.findViewById(R.id.dialogSpinnerCategory);

        etTitle.setText(expense.getTitle());
        etAmount.setText(String.format(Locale.US, "%.2f", expense.getAmount()));
        etDate.setText(expense.getDate());

        String[] categories = new String[]{"Food", "Travel", "Bills", "Shopping"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, categories);
        spinner.setAdapter(spinnerAdapter);

        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equalsIgnoreCase(expense.getCategory())) {
                spinner.setSelection(i);
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Update", (d, which) -> {
                    String title = etTitle.getText().toString().trim();
                    String amountStr = etAmount.getText().toString().trim();
                    String date = etDate.getText().toString().trim();
                    String category = spinner.getSelectedItem().toString();

                    if (title.isEmpty() || amountStr.isEmpty()) {
                        Toast.makeText(this, "Title and Amount are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    expense.setTitle(title);
                    expense.setAmount(Double.parseDouble(amountStr));
                    expense.setDate(date);
                    expense.setCategory(category);

                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        db.expenseDao().update(expense);
                        BudgetAlertWorker.checkAndNotifyBudget(MainActivity.this);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteDialog(Expense expense) {
        String curr = sessionManager.getCurrencySymbol();
        new AlertDialog.Builder(this)
                .setTitle("Delete Expense")
                .setMessage("Are you sure you want to delete '" + expense.getTitle() + "' (" + String.format(Locale.US, "%s%.2f", curr, expense.getAmount()) + ")?")
                .setPositiveButton("Delete", (d, which) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> db.expenseDao().delete(expense));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void scheduleBudgetWorker() {
        PeriodicWorkRequest budgetCheckRequest =
                new PeriodicWorkRequest.Builder(BudgetAlertWorker.class, 24, TimeUnit.HOURS)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "DailyBudgetCheck",
                ExistingPeriodicWorkPolicy.KEEP,
                budgetCheckRequest
        );
    }
}
