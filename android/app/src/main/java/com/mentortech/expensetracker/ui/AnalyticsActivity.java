package com.mentortech.expensetracker.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.mentortech.expensetracker.R;
import com.mentortech.expensetracker.database.AppDatabase;
import com.mentortech.expensetracker.database.ExpenseDao;

import com.mentortech.expensetracker.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnalyticsActivity extends AppCompatActivity {

    private PieChart pieChart;
    private LinearLayout layoutCategoryBreakdown;
    private AppDatabase db;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        pieChart = findViewById(R.id.pieChart);
        layoutCategoryBreakdown = findViewById(R.id.layoutCategoryBreakdown);

        setupPieChartStyle();
        loadChartData();
    }

    private void setupPieChartStyle() {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setExtraOffsets(10, 10, 10, 10);

        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleRadius(60f);
        pieChart.setHoleRadius(52f);

        pieChart.setCenterText("Monthly\nSpending");
        pieChart.setCenterTextSize(14f);

        // Legend configuration
        Legend legend = pieChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setTextSize(12f);

        pieChart.animateY(1200, Easing.EaseInOutQuad);
    }

    private void loadChartData() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<ExpenseDao.CategoryTotal> categoryTotals = 
                    db.expenseDao().getCategoryBreakdownSync();
            Double grandTotal = db.expenseDao().getTotalSpendingSync();

            runOnUiThread(() -> {
                if (categoryTotals == null || categoryTotals.isEmpty()) {
                    pieChart.setNoDataText("No expense data recorded yet to display charts.");
                    pieChart.invalidate();
                    return;
                }

                if (grandTotal != null && grandTotal > 0) {
                    pieChart.setCenterText(String.format(Locale.US, "Total\n%s%.2f", sessionManager.getCurrencySymbol(), grandTotal));
                }

                ArrayList<PieEntry> entries = new ArrayList<>();
                ArrayList<Integer> colors = new ArrayList<>();

                layoutCategoryBreakdown.removeAllViews();

                for (ExpenseDao.CategoryTotal item : categoryTotals) {
                    entries.add(new PieEntry((float) item.total, item.category));

                    int color;
                    switch (item.category.toLowerCase()) {
                        case "food":
                            color = Color.parseColor("#F43F5E");
                            break;
                        case "travel":
                            color = Color.parseColor("#3B82F6");
                            break;
                        case "bills":
                            color = Color.parseColor("#F59E0B");
                            break;
                        case "shopping":
                            color = Color.parseColor("#10B981");
                            break;
                        default:
                            color = Color.parseColor("#4338CA");
                            break;
                    }
                    colors.add(color);

                    double pct = grandTotal != null && grandTotal > 0 ? (item.total / grandTotal) * 100 : 0;
                    addCategorySummaryRow(item.category, item.total, pct, color);
                }

                PieDataSet dataSet = new PieDataSet(entries, "");
                dataSet.setSliceSpace(4f);
                dataSet.setSelectionShift(6f);
                dataSet.setColors(colors);

                PieData data = new PieData(dataSet);
                data.setValueFormatter(new PercentFormatter(pieChart));
                data.setValueTextSize(12f);
                data.setValueTextColor(Color.WHITE);

                pieChart.setData(data);
                pieChart.invalidate();
            });
        });
    }

    private void addCategorySummaryRow(String category, double total, double pct, int color) {
        TextView row = new TextView(this);
        row.setText(String.format(Locale.US, "• %s: %s%.2f (%.1f%%)", category, sessionManager.getCurrencySymbol(), total, pct));
        row.setTextSize(15f);
        row.setTextColor(color);
        row.setPadding(0, 10, 0, 10);
        layoutCategoryBreakdown.addView(row);
    }
}
