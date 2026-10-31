package com.mentortech.expensetracker.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.mentortech.expensetracker.R;
import com.mentortech.expensetracker.database.Expense;
import com.mentortech.expensetracker.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder> {

    public interface OnExpenseClickListener {
        void onClick(Expense expense);
    }

    public interface OnExpenseLongClickListener {
        void onLongClick(Expense expense);
    }

    private List<Expense> allExpenses = new ArrayList<>();
    private List<Expense> displayedExpenses = new ArrayList<>();
    private String currentCategoryFilter = "All";

    private final OnExpenseClickListener clickListener;
    private final OnExpenseLongClickListener longClickListener;

    public ExpenseAdapter(OnExpenseClickListener clickListener, OnExpenseLongClickListener longClickListener) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void setExpenses(List<Expense> newExpenses) {
        this.allExpenses = newExpenses != null ? newExpenses : new ArrayList<>();
        applyFilter();
    }

    public void filterByCategory(String category) {
        this.currentCategoryFilter = category;
        applyFilter();
    }

    private void applyFilter() {
        displayedExpenses.clear();
        if (currentCategoryFilter == null || currentCategoryFilter.equalsIgnoreCase("All")) {
            displayedExpenses.addAll(allExpenses);
        } else {
            for (Expense exp : allExpenses) {
                if (exp.getCategory().equalsIgnoreCase(currentCategoryFilter)) {
                    displayedExpenses.add(exp);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ExpenseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_expense, parent, false);
        return new ExpenseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
        Expense item = displayedExpenses.get(position);
        Context context = holder.itemView.getContext();

        holder.tvTitle.setText(item.getTitle());
        holder.tvDate.setText(item.getDate());
        SessionManager sessionManager = new SessionManager(context);
        holder.tvAmount.setText(String.format(Locale.US, "%s%.2f", sessionManager.getCurrencySymbol(), item.getAmount()));
        holder.tvBadge.setText(item.getCategory().toUpperCase());

        int badgeColor;
        switch (item.getCategory().toLowerCase()) {
            case "food":
                badgeColor = ContextCompat.getColor(context, R.color.category_food);
                break;
            case "travel":
                badgeColor = ContextCompat.getColor(context, R.color.category_travel);
                break;
            case "bills":
                badgeColor = ContextCompat.getColor(context, R.color.category_bills);
                break;
            case "shopping":
                badgeColor = ContextCompat.getColor(context, R.color.category_shopping);
                break;
            default:
                badgeColor = ContextCompat.getColor(context, R.color.primary);
                break;
        }
        holder.tvBadge.getBackground().setTint(badgeColor);

        // Click to Edit
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(item);
        });

        // Long click to Delete
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(item);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return displayedExpenses.size();
    }

    static class ExpenseViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvAmount, tvBadge;

        ExpenseViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvExpenseTitle);
            tvDate = itemView.findViewById(R.id.tvExpenseDate);
            tvAmount = itemView.findViewById(R.id.tvExpenseAmount);
            tvBadge = itemView.findViewById(R.id.tvCategoryBadge);
        }
    }
}
