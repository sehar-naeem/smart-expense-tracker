package com.mentortech.expensetracker.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.mentortech.expensetracker.R;
import com.mentortech.expensetracker.database.AppDatabase;
import com.mentortech.expensetracker.database.User;
import com.mentortech.expensetracker.utils.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private AppDatabase db;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        // If already logged in, skip login screen
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        etEmail = findViewById(R.id.etLoginEmail);
        etPassword = findViewById(R.id.etLoginPassword);

        findViewById(R.id.btnLogin).setOnClickListener(v -> handleLogin());

        findViewById(R.id.tvGoToSignUp).setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
        });
    }

    private void handleLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            User user = db.userDao().login(email, password);

            runOnUiThread(() -> {
                if (user != null) {
                    sessionManager.createLoginSession(user.getId(), user.getFullName(), user.getEmail());
                    Toast.makeText(LoginActivity.this, "Welcome back, " + user.getFullName() + "!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else {
                    Toast.makeText(LoginActivity.this, "Invalid email or password", Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
