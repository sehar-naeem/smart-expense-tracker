"""
=============================================================================
Smart Expense Tracker - AI Category Classifier Training Pipeline
-----------------------------------------------------------------------------
This script:
 1. Loads training data (receipt text -> category).
 2. Transforms raw text into numerical features using TF-IDF.
 3. Trains a Logistic Regression model to classify receipts into:
    - Food
    - Travel
    - Bills
    - Shopping
 4. Evaluates accuracy on unseen test data.
 5. Saves the trained model pipeline for use in our app.
=============================================================================
"""

import os
import joblib
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.metrics import classification_report, accuracy_score


def load_dataset(csv_path: str):
    """Loads the dataset and verifies it contains the required columns."""
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"Dataset not found at {csv_path}")
    
    df = pd.read_csv(csv_path)
    print(f"[*] Loaded dataset with {len(df)} samples.")
    print("[*] Category distribution:")
    print(df['category'].value_counts())
    return df


def train_model(df: pd.DataFrame):
    """
    Splits the data, builds the TF-IDF + Logistic Regression pipeline,
    and trains the model.
    """
    X = df['text']
    y = df['category']

    # Step 1: Split into Train (80%) and Test (20%) sets.
    # We use stratify=y so each category has equal representation in train and test.
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    print(f"\n[*] Training set size: {len(X_train)} samples")
    print(f"[*] Test set size: {len(X_test)} samples")

    # Step 2: Build the Machine Learning Pipeline.
    # - TfidfVectorizer: Converts raw words into numeric TF-IDF scores.
    #   * lowercase=True: "Burger" and "burger" are treated the same.
    #   * ngram_range=(1, 2): Captures single words ("coffee") AND pairs ("gas station").
    # - LogisticRegression: Multi-class linear classifier.
    pipeline = Pipeline([
        ('tfidf', TfidfVectorizer(
            lowercase=True,
            ngram_range=(1, 2),
            max_features=1000
        )),
        ('classifier', LogisticRegression(
            C=1.0,
            max_iter=200,
            random_state=42
        ))
    ])

    # Step 3: Train (fit) the pipeline
    print("\n[*] Training TF-IDF + Logistic Regression model...")
    pipeline.fit(X_train, y_train)

    # Step 4: Evaluate on unseen test data
    y_pred = pipeline.predict(X_test)
    accuracy = accuracy_score(y_test, y_pred)
    print(f"\n[+] Test Accuracy: {accuracy * 100:.2f}%\n")
    print("[+] Detailed Classification Report:")
    print(classification_report(y_test, y_pred))

    return pipeline


def test_predictions(pipeline):
    """Test the trained model on fresh, realistic receipt text snippets."""
    sample_receipts = [
        "1x Caramel Latte and Blueberry Muffin",
        "UBER TRIP receipt for airport ride",
        "Monthly electricity power bill October",
        "Nike running shoes and gym bag",
        "Shell petrol pump fuel $45.00",
        "Netflix 4K subscription invoice",
        "Zara cotton denim jacket",
        "Subway Italian BMT sandwich lunch"
    ]

    print("\n" + "=" * 60)
    print("LIVE RECEIPT PREDICTION DEMO")
    print("=" * 60)

    for item in sample_receipts:
        predicted_category = pipeline.predict([item])[0]
        # Get probability confidence score
        probabilities = pipeline.predict_proba([item])[0]
        confidence = max(probabilities) * 100
        print(f"Receipt Text : \"{item}\"")
        print(f"-> Predicted : {predicted_category.upper()} ({confidence:.1f}% confidence)\n")


def save_model(pipeline, output_path: str):
    """Saves the trained pipeline using joblib."""
    joblib.dump(pipeline, output_path)
    print(f"[+] Model successfully saved to: {output_path}")


if __name__ == "__main__":
    current_dir = os.path.dirname(os.path.abspath(__file__))
    dataset_file = os.path.join(current_dir, "dataset.csv")
    model_output_file = os.path.join(current_dir, "expense_classifier.joblib")

    # 1. Load data
    data = load_dataset(dataset_file)

    # 2. Train and evaluate
    model_pipeline = train_model(data)

    # 3. Test on realistic samples
    test_predictions(model_pipeline)

    # 4. Save model
    save_model(model_pipeline, model_output_file)
