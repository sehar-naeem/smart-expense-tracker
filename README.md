# Smart Expense Tracker with AI Receipt Scanning

An end-to-end AI-powered mobile application that automatically extracts amounts and dates from photographed receipts, classifies them into smart spending categories (*Food*, *Travel*, *Bills*, *Shopping*) using an on-device Machine Learning model, tracks monthly budgets, and generates visual analytics.

---

## 🌟 Key Features

1. **Receipt Scanning & OCR**:
   - On-device optical character recognition using **Google ML Kit Text Recognition** (100% offline, private, and instantaneous).
2. **Regex Parsing Engine**:
   - Targeted anchor pattern matching for total amounts (`TOTAL DUE`, `GRAND TOTAL`, `AMOUNT DUE`) and flexible date formats (`YYYY-MM-DD`, `DD/MM/YYYY`, `MM/DD/YYYY`).
3. **On-Device AI Categorization**:
   - Trained in Python using **TF-IDF + Logistic Regression** on real-world receipt descriptions, achieving **89.66% accuracy**.
   - Exported to a lightweight JSON model running in pure Java with **sub-millisecond inference time**.
4. **Local SQLite Storage (Room Database)**:
   - Built with Android Architecture Components (`@Entity`, `@Dao`, `RoomDatabase`) with reactive `LiveData` observers.
5. **Interactive Spending Analytics**:
   - Animated donut pie chart powered by **MPAndroidChart** displaying percentage breakdowns by category.
6. **Automated Budget Alerts (WorkManager)**:
   - Configurable monthly budget limit with periodic background checks firing high-priority Android notifications when spending thresholds are breached.
7. **Full Authentication System**:
   - User registration & sign-in with full name, email validation, password encryption, and local session management.
8. **Modern Fintech UI**:
   - Indigo-Violet gradients, geometric watermark wallpaper, category filter chips, and click-to-edit expense cards.

---

## 🏗️ Architecture & Technology Stack

```
mentortech/
├── ai/                              # Machine Learning Module (Python)
│   ├── dataset.csv                  # 140+ labeled receipt training samples
│   ├── train_classifier.py          # TF-IDF + Logistic Regression training pipeline
│   ├── expense_classifier.joblib    # Serialized scikit-learn model
│   ├── export_for_android.py        # Exporter to convert weights into JSON
│   └── model_weights.json           # Compact model deployed into Android
│
├── parser/                          # Text Parsing Module (Python prototype)
│   └── receipt_parser.py            # Regex anchor matcher for totals and dates
│
└── android/                         # Android Mobile Application (Java)
    ├── app/src/main/
    │   ├── assets/
    │   │   └── model_weights.json   # On-device AI model weights
    │   ├── java/.../
    │   │   ├── ai/
    │   │   │   └── ExpenseClassifier.java   # Pure Java inference engine
    │   │   ├── parser/
    │   │   │   └── ReceiptParser.java       # Java regex extractor
    │   │   ├── database/
    │   │   │   ├── Expense.java             # Expense Room entity
    │   │   │   ├── User.java                # User Room entity
    │   │   │   ├── ExpenseDao.java          # Expense SQL DAO
    │   │   │   ├── UserDao.java             # User SQL DAO
    │   │   │   └── AppDatabase.java         # Room Database Singleton
    │   │   ├── worker/
    │   │   │   └── BudgetAlertWorker.java   # WorkManager background alert
    │   │   ├── utils/
    │   │   │   └── SessionManager.java      # SharedPreferences session
    │   │   └── ui/
    │   │       ├── SplashActivity.java      # Splash & onboarding screen
    │   │       ├── LoginActivity.java       # User authentication sign-in
    │   │       ├── SignUpActivity.java      # New user registration
    │   │       ├── MainActivity.java        # Dashboard & recent expenses
    │   │       ├── ScanReceiptActivity.java # Camera/Gallery OCR scanner
    │   │       ├── AnalyticsActivity.java   # MPAndroidChart pie chart
    │   │       └── ExpenseAdapter.java      # RecyclerView card adapter
    │   └── res/                             # Layouts, colors, drawables, icons
```

---

## 🚀 How to Build & Deploy the App (.APK)

### 1. Build an Installable APK:
In Android Studio:
1. Go to the top menu: **Build $\rightarrow$ Build Bundle(s) / APK(s) $\rightarrow$ Build APK(s)**.
2. Android Studio will compile the app and show a popup at the bottom right:
   > *"APK(s) generated successfully: locate"*
3. Click **"locate"** to open the folder containing:
   `app-debug.apk`
4. Send this `.apk` file to any Android phone via USB, Google Drive, or WhatsApp to install it directly!

### 2. Run Directly on Phone / Emulator:
1. Plug your Android phone into your PC with a USB cable and enable **USB Debugging** (or use the built-in Android Studio Emulator).
2. Select your device in the top toolbar dropdown.
3. Click the green **Run ▶** button (or press `Shift + F10`).
