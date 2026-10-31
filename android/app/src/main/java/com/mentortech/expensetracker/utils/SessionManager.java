package com.mentortech.expensetracker.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Session Manager to manage user login state using SharedPreferences.
 */
public class SessionManager {

    private static final String PREF_NAME = "UserSessionPrefs";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_CURRENCY = "currency_symbol";

    public static final String DEFAULT_CURRENCY = "Rs. ";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.editor = prefs.edit();
    }

    public void createLoginSession(long userId, String name, String email) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putLong(KEY_USER_ID, userId);
        editor.putString(KEY_USER_NAME, name);
        editor.putString(KEY_USER_EMAIL, email);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "User");
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public void setCurrencySymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            symbol = DEFAULT_CURRENCY;
        }
        editor.putString(KEY_CURRENCY, symbol);
        editor.apply();
    }

    public String getCurrencySymbol() {
        return prefs.getString(KEY_CURRENCY, DEFAULT_CURRENCY);
    }

    public String formatAmount(double amount) {
        return String.format(java.util.Locale.US, "%s%.2f", getCurrencySymbol(), amount);
    }

    public void logout() {
        editor.clear();
        editor.apply();
    }
}
