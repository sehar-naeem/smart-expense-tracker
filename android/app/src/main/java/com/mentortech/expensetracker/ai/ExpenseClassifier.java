package com.mentortech.expensetracker.ai;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * On-Device AI Expense Classifier (Java).
 * 
 * Executes the trained TF-IDF + Logistic Regression model directly on the phone.
 * - Reads model_weights.json from app/src/main/assets/
 * - Performs inference in < 1ms with 0 network calls and 0 heavy external libraries.
 */
public class ExpenseClassifier {

    private String[] classes;
    private double[] intercepts;
    private final Map<String, WordFeature> wordWeights = new HashMap<>();
    private boolean isLoaded = false;

    private static class WordFeature {
        double idf;
        double[] weights;

        WordFeature(double idf, double[] weights) {
            this.idf = idf;
            this.weights = weights;
        }
    }

    /**
     * Loads the exported model weights JSON from Android assets.
     */
    public synchronized void loadModel(Context context, String assetFilename) {
        if (isLoaded) return;

        try {
            InputStream is = context.getAssets().open(assetFilename);
            int size = is.available();
            byte[] buffer = new byte[size];
            int readBytes = is.read(buffer);
            is.close();

            String jsonString = new String(buffer, 0, readBytes, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(jsonString);

            // 1. Load Classes (e.g. ["Bills", "Food", "Shopping", "Travel"])
            JSONArray classesArray = root.getJSONArray("classes");
            classes = new String[classesArray.length()];
            for (int i = 0; i < classesArray.length(); i++) {
                classes[i] = classesArray.getString(i);
            }

            // 2. Load Intercepts (biases for each class)
            JSONArray interceptsArray = root.getJSONArray("intercepts");
            intercepts = new double[interceptsArray.length()];
            for (int i = 0; i < interceptsArray.length(); i++) {
                intercepts[i] = interceptsArray.getDouble(i);
            }

            // 3. Load Word Weights and IDF
            JSONObject weightsObj = root.getJSONObject("word_weights");
            for (var it = weightsObj.keys(); it.hasNext(); ) {
                String word = it.next();
                JSONObject item = weightsObj.getJSONObject(word);
                double idf = item.getDouble("idf");

                JSONArray wArray = item.getJSONArray("weights");
                double[] w = new double[wArray.length()];
                for (int j = 0; j < wArray.length(); j++) {
                    w[j] = wArray.getDouble(j);
                }

                wordWeights.put(word, new WordFeature(idf, w));
            }

            isLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Classifies raw receipt text into one of the 4 categories.
     * 
     * @param receiptText e.g. "Starbucks caramel macchiato coffee $5.50"
     * @return Category name: "Food", "Travel", "Bills", or "Shopping"
     */
    public String predictCategory(String receiptText) {
        if (!isLoaded || receiptText == null || receiptText.trim().isEmpty()) {
            return "Food"; // Safe fallback
        }

        // Initialize scores with the learned class intercepts (biases)
        double[] scores = new double[classes.length];
        System.arraycopy(intercepts, 0, scores, 0, intercepts.length);

        // Normalize text to lowercase and remove punctuation
        String cleanText = receiptText.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
        String[] tokens = cleanText.split("\\s+");

        // 1. Process Unigrams (single words)
        for (String token : tokens) {
            applyWordToScores(token, scores);
        }

        // 2. Process Bigrams (word pairs, e.g. "gas station", "ice cream")
        for (int i = 0; i < tokens.length - 1; i++) {
            String bigram = tokens[i] + " " + tokens[i + 1];
            applyWordToScores(bigram, scores);
        }

        // Find the class with the highest score (Argmax)
        int bestIndex = 0;
        double maxScore = scores[0];
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > maxScore) {
                maxScore = scores[i];
                bestIndex = i;
            }
        }

        return classes[bestIndex];
    }

    private void applyWordToScores(String term, double[] scores) {
        WordFeature feature = wordWeights.get(term);
        if (feature != null) {
            // TF-IDF weight = TF (count) * IDF
            // In linear models: score_class += idf * weight_class
            for (int c = 0; c < scores.length; c++) {
                scores[c] += feature.idf * feature.weights[c];
            }
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }
}
