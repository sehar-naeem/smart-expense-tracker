"""
=============================================================================
Export Model for Android Integration
-----------------------------------------------------------------------------
This script exports the trained TF-IDF + Logistic Regression model into a 
clean, lightweight JSON file (`model_weights.json`).

Why?
 - In Android Java, you can simply load this JSON from the `assets/` folder.
 - Inference takes < 1 ms on any phone with 0 external heavy dependencies.
=============================================================================
"""

import os
import json
import joblib


def export_model_for_android(joblib_path: str, output_json_path: str):
    """Extracts weights, IDF values, and vocabulary into JSON for Android."""
    if not os.path.exists(joblib_path):
        raise FileNotFoundError(f"Model file not found: {joblib_path}")

    # Load the trained scikit-learn pipeline
    pipeline = joblib.load(joblib_path)
    tfidf = pipeline.named_steps['tfidf']
    clf = pipeline.named_steps['classifier']

    # 1. Categories
    classes = list(clf.classes_)

    # 2. Vocabulary and IDF values
    # tfidf.vocabulary_ maps word -> feature_index
    # tfidf.idf_ is an array of IDF weights corresponding to each feature_index
    vocab = tfidf.vocabulary_
    idf = tfidf.idf_

    # 3. Logistic Regression Coefficients and Intercepts
    # clf.coef_ shape: (n_classes, n_features)
    # clf.intercept_ shape: (n_classes,)
    coefs = clf.coef_
    intercepts = clf.intercept_

    # Create a compact model dictionary
    # For fast lookup in Android Java:
    # word -> { "idf": float, "weights": [weight_for_class_0, weight_for_class_1, ...] }
    word_weights = {}
    for word, idx in vocab.items():
        word_weights[word] = {
            "idf": float(idf[idx]),
            "weights": [float(coefs[c_idx][idx]) for c_idx in range(len(classes))]
        }

    android_model = {
        "classes": classes,
        "intercepts": [float(b) for b in intercepts],
        "word_weights": word_weights
    }

    with open(output_json_path, 'w', encoding='utf-8') as f:
        json.dump(android_model, f, indent=2)

    print(f"[+] Successfully exported Android model to: {output_json_path}")
    print(f"[+] Total Vocabulary Words: {len(word_weights)}")
    print(f"[+] Supported Classes: {classes}")


if __name__ == "__main__":
    current_dir = os.path.dirname(os.path.abspath(__file__))
    model_path = os.path.join(current_dir, "expense_classifier.joblib")
    json_path = os.path.join(current_dir, "model_weights.json")

    export_model_for_android(model_path, json_path)
