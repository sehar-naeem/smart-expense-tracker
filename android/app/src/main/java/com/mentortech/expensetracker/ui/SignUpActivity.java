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

public class SignUpActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private AppDatabase db;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        etName = findViewById(R.id.etSignUpName);
        etEmail = findViewById(R.id.etSignUpEmail);
        etPassword = findViewById(R.id.etSignUpPassword);
        etConfirmPassword = findViewById(R.id.etSignUpConfirmPassword);

        findViewById(R.id.btnSignUp).setOnClickListener(v -> handleSignUp());
        findViewById(R.id.tvGoToLogin).setOnClickListener(v -> finish());
    }

    private void handleSignUp() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError("Full Name is required");
            etName.requestFocus();
            return;
        }

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

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            User existing = db.userDao().findByEmail(email);

            if (existing != null) {
                runOnUiThread(() -> {
                    Toast.makeText(SignUpActivity.this, "This email is already registered. Please sign in.", Toast.LENGTH_LONG).show();
                    etEmail.requestFocus();
                });
                return;
            }

            User newUser = new User(name, email, password);
            long newUserId = db.userDao().insert(newUser);

            runOnUiThread(() -> {
                sessionManager.createLoginSession(newUserId, name, email);
                Toast.makeText(SignUpActivity.this, "Account created successfully! Welcome, " + name, Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });
    }
}
