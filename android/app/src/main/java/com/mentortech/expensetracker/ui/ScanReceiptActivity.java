package com.mentortech.expensetracker.ui;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.mentortech.expensetracker.R;
import com.mentortech.expensetracker.ai.ExpenseClassifier;
import com.mentortech.expensetracker.database.AppDatabase;
import com.mentortech.expensetracker.database.Expense;
import com.mentortech.expensetracker.parser.ReceiptParser;
import com.mentortech.expensetracker.utils.SessionManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScanReceiptActivity extends AppCompatActivity {

    private ImageView ivReceiptPreview;
    private ProgressBar pbScanning;
    private EditText etMerchantTitle, etTotalAmount, etDate;
    private RadioGroup rgCategories;
    private RadioButton rbFood, rbTravel, rbBills, rbShopping;

    private ExpenseClassifier classifier;
    private AppDatabase db;
    private SessionManager sessionManager;
    private String lastRawOcrText = "";

    private Uri cameraImageUri;

    // High-resolution camera and gallery launchers
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraImageUri != null) {
                    processReceiptUri(cameraImageUri);
                }
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    processReceiptUri(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_receipt);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        // Initialize and load our on-device AI model from assets
        classifier = new ExpenseClassifier();
        classifier.loadModel(this, "model_weights.json");

        initViews();
    }

    private void initViews() {
        ivReceiptPreview = findViewById(R.id.ivReceiptPreview);
        pbScanning = findViewById(R.id.pbScanning);
        etMerchantTitle = findViewById(R.id.etMerchantTitle);
        etTotalAmount = findViewById(R.id.etTotalAmount);
        etDate = findViewById(R.id.etDate);

        etTotalAmount.setHint("Total Amount (" + sessionManager.getCurrencySymbol().trim() + ")");

        rgCategories = findViewById(R.id.rgCategories);
        rbFood = findViewById(R.id.rbFood);
        rbTravel = findViewById(R.id.rbTravel);
        rbBills = findViewById(R.id.rbBills);
        rbShopping = findViewById(R.id.rbShopping);

        findViewById(R.id.btnTakePhoto).setOnClickListener(v -> launchFullResolutionCamera());
        findViewById(R.id.btnPickGallery).setOnClickListener(v -> galleryLauncher.launch("image/*"));
        findViewById(R.id.btnSaveExpense).setOnClickListener(v -> saveExpenseToDatabase());
    }

    private void launchFullResolutionCamera() {
        try {
            java.io.File photoFile = java.io.File.createTempFile("receipt_full_", ".jpg", getCacheDir());
            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    photoFile
            );
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to start camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processReceiptUri(Uri uri) {
        ivReceiptPreview.setImageURI(uri);
        ivReceiptPreview.setAlpha(1.0f);
        pbScanning.setVisibility(View.VISIBLE);

        InputImage image;
        try {
            image = InputImage.fromFilePath(this, uri);
        } catch (IOException e) {
            pbScanning.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        // Run Google ML Kit Text Recognition (On-device OCR on full-resolution image)
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    pbScanning.setVisibility(View.GONE);
                    String rawText = visionText.getText();
                    lastRawOcrText = rawText;

                    if (rawText.trim().isEmpty()) {
                        showNotReceiptDialog(
                                "⚠️ No Text Found",
                                "We couldn't detect any text in this photo.\n\nPlease ensure your receipt is placed on a flat surface with good lighting, or choose another photo from your gallery."
                        );
                        return;
                    }

                    // 1. Enhanced Regex & Heuristic Extraction: Amount, Date, Merchant Title, Currency, Validity
                    ReceiptParser.ParseResult parsed = ReceiptParser.parse(rawText);

                    // SMART RECEIPT VALIDITY CHECK (GATEKEEPER)
                    if (!parsed.isValidReceipt) {
                        showNotReceiptDialog(
                                "⚠️ Photo is Not a Receipt",
                                "This image does not appear to be a genuine receipt or bill.\n\nNo prices, totals, or retail store details were detected. You can retake the photo, choose a different image, or enter the expense manually below."
                        );
                        return;
                    }

                    if (parsed.totalAmount != null) {
                        etTotalAmount.setText(String.format(Locale.US, "%.2f", parsed.totalAmount));
                    }
                    if (parsed.date != null) {
                        etDate.setText(parsed.date);
                    } else {
                        // Fallback to today's date
                        etDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
                    }

                    // Pre-fill clean merchant title
                    if (parsed.merchantTitle != null && !parsed.merchantTitle.isEmpty()) {
                        etMerchantTitle.setText(parsed.merchantTitle);
                    }

                    // If bill specifies currency, update active symbol & hint
                    if (parsed.detectedCurrency != null) {
                        sessionManager.setCurrencySymbol(parsed.detectedCurrency);
                        etTotalAmount.setHint("Total Amount (" + parsed.detectedCurrency.trim() + ")");
                    }

                    // 2. AI Classification: Predict Category
                    String predictedCategory = classifier.predictCategory(rawText);
                    selectCategoryRadioButton(predictedCategory);

                    Toast.makeText(this, "Parsed! " + (parsed.merchantTitle != null ? parsed.merchantTitle : ""), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    pbScanning.setVisibility(View.GONE);
                    Toast.makeText(this, "OCR Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void selectCategoryRadioButton(String category) {
        if (category == null) return;
        switch (category.toLowerCase()) {
            case "travel":
                rbTravel.setChecked(true);
                break;
            case "bills":
                rbBills.setChecked(true);
                break;
            case "shopping":
                rbShopping.setChecked(true);
                break;
            case "food":
            default:
                rbFood.setChecked(true);
                break;
        }
    }

    private String getSelectedCategory() {
        int id = rgCategories.getCheckedRadioButtonId();
        if (id == R.id.rbTravel) return "Travel";
        if (id == R.id.rbBills) return "Bills";
        if (id == R.id.rbShopping) return "Shopping";
        if (id == R.id.rbFood) return "Food";
        return null;
    }

    private void saveExpenseToDatabase() {
        String title = etMerchantTitle.getText().toString().trim();
        String amountStr = etTotalAmount.getText().toString().trim();
        String date = etDate.getText().toString().trim();
        String category = getSelectedCategory();

        if (title.isEmpty() || amountStr.isEmpty()) {
            Toast.makeText(this, "Please enter Title and Amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (category == null) {
            Toast.makeText(this, "Please select an Expense Category", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount = Double.parseDouble(amountStr);
        Expense expense = new Expense(title, amount, date, category, lastRawOcrText);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.expenseDao().insert(expense);
            com.mentortech.expensetracker.worker.BudgetAlertWorker.checkAndNotifyBudget(ScanReceiptActivity.this);
            runOnUiThread(() -> {
                Toast.makeText(ScanReceiptActivity.this, "Expense Saved!", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void showNotReceiptDialog(String title, String message) {
        pbScanning.setVisibility(View.GONE);
        etMerchantTitle.setText("");
        etTotalAmount.setText("");
        rgCategories.clearCheck();

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setCancelable(false)
                .setPositiveButton("📷 Retake Photo", (dialog, which) -> launchFullResolutionCamera())
                .setNeutralButton("🖼️ Choose Gallery", (dialog, which) -> galleryLauncher.launch("image/*"))
                .setNegativeButton("✏️ Enter Manually", null)
                .show();
    }
}
