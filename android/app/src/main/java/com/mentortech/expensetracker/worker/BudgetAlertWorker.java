package com.mentortech.expensetracker.worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mentortech.expensetracker.R;
import com.mentortech.expensetracker.database.AppDatabase;

/**
 * Background WorkManager worker that monitors monthly spending.
 * Fires a system notification if the user's configured budget limit is exceeded.
 */
public class BudgetAlertWorker extends Worker {

    private static final String CHANNEL_ID = "budget_alerts_channel";

    public BudgetAlertWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        checkAndNotifyBudget(getApplicationContext());
        return Result.success();
    }

    /**
     * Checks total spending against budget limit and sends notification if exceeded.
     * Can be called periodically or immediately when an expense is recorded.
     */
    public static void checkAndNotifyBudget(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("ExpensePrefs", Context.MODE_PRIVATE);
        float budgetLimit = prefs.getFloat("monthly_budget", 500.0f);

        AppDatabase db = AppDatabase.getInstance(context);
        Double totalSpending = db.expenseDao().getTotalSpendingSync();

        if (totalSpending != null && totalSpending > budgetLimit) {
            sendBudgetExceededNotification(context, totalSpending, budgetLimit);
        }
    }

    private static void sendBudgetExceededNotification(Context context, double currentSpend, float limit) {
        NotificationManager manager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Budget Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts when monthly expense budget is exceeded");
            manager.createNotificationChannel(channel);
        }

        com.mentortech.expensetracker.utils.SessionManager sessionManager = 
                new com.mentortech.expensetracker.utils.SessionManager(context);
        String curr = sessionManager.getCurrencySymbol();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("⚠️ Budget Limit Exceeded!")
                .setContentText(String.format("You spent %s%.2f which exceeds your %s%.2f monthly budget!", curr, currentSpend, curr, limit))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        manager.notify(101, builder.build());
    }
}
