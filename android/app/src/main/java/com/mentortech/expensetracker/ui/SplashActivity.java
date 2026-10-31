package com.mentortech.expensetracker.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

import com.mentortech.expensetracker.R;
import com.mentortech.expensetracker.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable launchRunnable;
    private boolean isNavigated = false;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        sessionManager = new SessionManager(this);

        ImageView logo = findViewById(R.id.ivSplashLogo);
        if (logo != null) {
            Animation fadeIn = new AlphaAnimation(0.2f, 1.0f);
            fadeIn.setDuration(1000);
            logo.startAnimation(fadeIn);
        }

        findViewById(R.id.btnGetStarted).setOnClickListener(v -> navigateNext());

        // Auto-navigate after 3.5 seconds if user doesn't tap "Get Started"
        launchRunnable = this::navigateNext;
        handler.postDelayed(launchRunnable, 3500);
    }

    private void navigateNext() {
        if (isNavigated) return;
        isNavigated = true;

        if (launchRunnable != null) {
            handler.removeCallbacks(launchRunnable);
        }

        Intent intent;
        if (sessionManager.isLoggedIn()) {
            intent = new Intent(SplashActivity.this, MainActivity.class);
        } else {
            intent = new Intent(SplashActivity.this, LoginActivity.class);
        }
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (launchRunnable != null) {
            handler.removeCallbacks(launchRunnable);
        }
    }
}
