package com.mentortech.expensetracker.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receipt Text Parser (Java Port).
 * 
 * Extracts Total Amount and Transaction Date from raw OCR text using regex patterns.
 */
public class ReceiptParser {

    public static class ParseResult {
        public final Double totalAmount;
        public final String date;
        public final String merchantTitle;
        public final String detectedCurrency;
        public final boolean isValidReceipt;

        public ParseResult(Double totalAmount, String date, String merchantTitle, String detectedCurrency, boolean isValidReceipt) {
            this.totalAmount = totalAmount;
            this.date = date;
            this.merchantTitle = merchantTitle;
            this.detectedCurrency = detectedCurrency;
            this.isValidReceipt = isValidReceipt;
        }
    }

    // Anchor regex patterns for total amount supporting multi-currencies, tax variations, integers, and decimals
    private static final Pattern[] TOTAL_PATTERNS = new Pattern[] {
        Pattern.compile("(?i)(?:grand\\s+total|total\\s+amount|total\\s+due|amount\\s+due|net\\s+payable|amount\\s+payable|amount\\s+inc[l\\.]*\\s*sales\\s*tax|amount\\s+inc[l\\.]*\\s*tax|net\\s+amount|gross\\s+amount|bill\\s+amount|net\\s+total|final\\s+total|subtotal|sub\\s+total|payment|total\\s+price|total)[\\s\\:\\=\\-]*"
                + "(?:rs\\.?|pkr|inr|usd|eur|gbp|aed|sar|[\\$€£₹])?[\\s]*"
                + "([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:\\/\\-)?"),

        Pattern.compile("(?i)(?:total|payable|due|payment)[\\s\\:\\=\\-]*([0-9]+(?:\\.[0-9]{1,2})?|[0-9]{1,3}(?:,[0-9]{3})+)"),

        Pattern.compile("(?i)(?:subtotal|sub\\s+total)[\\s\\:\\=\\-]*(?:rs\\.?|pkr|[\\$€£₹])?[\\s]*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)")
    };

    // Regex patterns for transaction date
    private static final Pattern[] DATE_PATTERNS = new Pattern[] {
        Pattern.compile("\\b([0-9]{1,2}[-/\\.][0-9]{1,2}[-/\\.][0-9]{2,4})\\b"),
        Pattern.compile("\\b([0-9]{4}[-/\\.][0-9]{1,2}[-/\\.][0-9]{1,2})\\b"),
        Pattern.compile("(?i)\\b([0-9]{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s,]+[0-9]{2,4})\\b")
    };

    // Fallback regex for currency-prefixed numbers
    private static final Pattern CURRENCY_ATTACHED_AMOUNT =
            Pattern.compile("(?i)(?:rs\\.?|pkr|[\\$€£₹])\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)");

    private static final Pattern ALL_DECIMAL_AMOUNTS = Pattern.compile("(?i)[\\$€£₹]?\\s*([0-9]+\\.[0-9]{2})\\b");

    // Patterns that identify lines that MUST NOT be merchant titles
    private static final Pattern[] REJECT_TITLE_PATTERNS = new Pattern[] {
        Pattern.compile("(?i)\\b(?:order|ordr|grder|ord)\\s*(?:number|nuhber|no|#|\\:).*"),
        Pattern.compile("(?i)\\b(?:store|shop|reg|pos|counter|terminal)\\s*(?:id|no|#|\\:).*"),
        Pattern.compile("(?i)\\b(?:invoice|inv)\\s*(?:no|#|\\:).*"),
        Pattern.compile("(?i)\\b(?:bill|bil|token|table|check|chk)\\s*(?:no|#|\\:).*"),
        Pattern.compile("(?i)\\b(?:ntn|sntn|strn|gst|tin|trn|vat)\\b.*"),
        Pattern.compile("(?i)\\b(?:cashier|server|operator|waiter|attendant)\\b.*"),
        Pattern.compile("(?i)\\b(?:date|time|pm|am)\\b.*"),
        Pattern.compile("(?i)\\b(?:tel|phone|ph|mob|cell|fax|email|www|http|\\.com|\\.pk)\\b.*"),
        Pattern.compile("(?i)\\b(?:customer|merchant|duplicate|original)\\s+copy\\b.*"),
        Pattern.compile("(?i)\\b(?:eat\\s+out|take\\s+away|dine\\s+in|delivery)\\b.*"),
        Pattern.compile("(?i)^(?:tax\\s+invoice|cash\\s+receipt|sales\\s+receipt|cash\\s+memo|receipt|bill)$"),
        Pattern.compile("^\\d+$")
    };

    private static final Pattern[] PREFIX_STRIP_PATTERNS = new Pattern[] {
        Pattern.compile("(?i)^welcome\\s+(?:to\\s+)?"),
        Pattern.compile("(?i)^thanks?\\s+(?:for\\s+)?(?:visiting|shopping\\s+at)?\\s*"),
        Pattern.compile("(?i)^have\\s+a\\s+nice\\s+day\\s*")
    };

    /**
     * Extracts total monetary amount from receipt text (supports PKR, Rs, $, €, £, ₹, etc.).
     */
    public static Double extractTotal(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        List<Double> foundAmounts = new ArrayList<>();

        // Strategy 1: Targeted anchor keywords
        for (Pattern pattern : TOTAL_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String match = matcher.group(1);
                if (match != null) {
                    try {
                        double val = Double.parseDouble(match.replace(",", ""));
                        if (val > 0) foundAmounts.add(val);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Strategy 2: Currency-prefixed amounts (e.g. "Rs 680.00" or "$45.50")
        Matcher currMatcher = CURRENCY_ATTACHED_AMOUNT.matcher(text);
        while (currMatcher.find()) {
            try {
                double val = Double.parseDouble(currMatcher.group(1).replace(",", ""));
                if (val > 0) foundAmounts.add(val);
            } catch (NumberFormatException ignored) {}
        }

        if (!foundAmounts.isEmpty()) {
            return Collections.max(foundAmounts);
        }

        // Strategy 3: Fallback to largest monetary decimal
        Matcher decMatcher = ALL_DECIMAL_AMOUNTS.matcher(text);
        while (decMatcher.find()) {
            try {
                double val = Double.parseDouble(decMatcher.group(1));
                if (val > 0) foundAmounts.add(val);
            } catch (NumberFormatException ignored) {}
        }

        if (!foundAmounts.isEmpty()) {
            return Collections.max(foundAmounts);
        }

        return null;
    }

    /**
     * Detects currency symbol from receipt text (defaults to null if not clearly specified).
     */
    public static String extractCurrency(String text) {
        if (text == null) return null;
        if (Pattern.compile("(?i)\\b(?:rs\\.?|pkr)\\b").matcher(text).find()) {
            return "Rs. ";
        }
        if (Pattern.compile("(?i)\\b(?:₹|inr)\\b").matcher(text).find()) {
            return "₹";
        }
        if (Pattern.compile("(?i)\\b(?:€|eur)\\b").matcher(text).find()) {
            return "€";
        }
        if (Pattern.compile("(?i)\\b(?:£|gbp)\\b").matcher(text).find()) {
            return "£";
        }
        if (Pattern.compile("(?i)\\b(?:aed)\\b").matcher(text).find()) {
            return "AED ";
        }
        if (Pattern.compile("(?i)\\b(?:sar)\\b").matcher(text).find()) {
            return "SAR ";
        }
        if (Pattern.compile("(?i)(?:\\$|\\busd\\b)").matcher(text).find()) {
            return "$";
        }
        return null;
    }

    /**
     * Extracts date string from receipt text.
     */
    public static String extractDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    /**
     * Extracts clean merchant / store title from receipt text instead of random headers.
     */
    public static String extractMerchantTitle(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "Receipt Expense";
        }

        String[] rawLines = text.split("\\r?\\n");
        List<ScoredCandidate> candidates = new ArrayList<>();

        int lineLimit = Math.min(rawLines.length, 12);
        for (int i = 0; i < lineLimit; i++) {
            String line = rawLines[i].trim();
            if (line.isEmpty()) continue;

            // Strip prefixes like "Welcome To"
            String cleaned = line;
            for (Pattern prefixPattern : PREFIX_STRIP_PATTERNS) {
                cleaned = prefixPattern.matcher(cleaned).replaceFirst("").trim();
            }

            // Remove leading/trailing non-alphanumeric symbols
            cleaned = cleaned.replaceAll("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$", "").trim();
            int letterCount = countLetters(cleaned);

            if (letterCount < 3 || cleaned.length() < 3 || cleaned.length() > 45) {
                continue;
            }

            // Check rejection rules (Order number, Store ID, Invoice number, Phone, etc.)
            boolean rejected = false;
            for (Pattern rejectPattern : REJECT_TITLE_PATTERNS) {
                if (rejectPattern.matcher(cleaned).find()) {
                    rejected = true;
                    break;
                }
            }
            if (rejected) {
                continue;
            }

            // Score candidate
            int score = 100 - (i * 10);
            if (cleaned.equals(cleaned.toUpperCase())) {
                score += 25; // Store names are often in ALL CAPS
            }
            if (Pattern.compile("(?i)\\b(kfc|mcdonald|subway|hardees|pizza|burger|cafe|bakery|bakers|mart|store|supermarket|pharmacy|express|station|foods|restaurant)\\b").matcher(cleaned).find()) {
                score += 50; // Known store descriptor keywords
            }

            candidates.add(new ScoredCandidate(cleaned, score));
        }

        if (!candidates.isEmpty()) {
            Collections.sort(candidates, (a, b) -> Integer.compare(b.score, a.score));
            return candidates.get(0).title;
        }

        // Fallback: pick first alphanumeric line that has at least 3 letters
        for (String raw : rawLines) {
            String clean = raw.replaceAll("[^a-zA-Z0-9\\s\\-\\']", "").trim();
            if (countLetters(clean) >= 3 && !clean.toLowerCase().contains("order") && !clean.toLowerCase().contains("tax")) {
                return clean;
            }
        }

        return "Receipt Expense";
    }

    private static int countLetters(String str) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (Character.isLetter(c)) count++;
        }
        return count;
    }

    private static class ScoredCandidate {
        final String title;
        final int score;

        ScoredCandidate(String title, int score) {
            this.title = title;
            this.score = score;
        }
    }

    private static final Pattern RECEIPT_KEYWORD_PATTERN =
            Pattern.compile("(?i)\\b(total|subtotal|amount|bill|invoice|receipt|tax|gst|vat|cash|card|payment|change|balance|due|price|qty|discount|store|mart|bakery|restaurant|cafe|order|ntn|strn|cashier|pos)\\b");

    /**
     * Checks if the scanned text actually represents a genuine receipt or bill.
     * Prevents random images (selfies, scenery, documents) from being processed as expenses.
     */
    public static boolean isValidReceipt(String text, Double extractedTotal) {
        if (text == null || text.trim().length() < 10) {
            return false;
        }

        // Strong signal 1: A valid price/amount was parsed
        if (extractedTotal != null && extractedTotal > 0) {
            return true;
        }

        // Strong signal 2: Contains at least two retail transaction keywords AND has digits
        Matcher matcher = RECEIPT_KEYWORD_PATTERN.matcher(text);
        java.util.Set<String> matchedWords = new java.util.HashSet<>();
        while (matcher.find()) {
            matchedWords.add(matcher.group().toLowerCase());
            if (matchedWords.size() >= 2) break;
        }

        boolean hasDigits = Pattern.compile("\\d+").matcher(text).find();
        return matchedWords.size() >= 2 && hasDigits;
    }

    /**
     * Convenience method to extract total, date, merchant, and currency in one call.
     */
    public static ParseResult parse(String rawReceiptText) {
        Double total = extractTotal(rawReceiptText);
        String date = extractDate(rawReceiptText);
        String merchant = extractMerchantTitle(rawReceiptText);
        String currency = extractCurrency(rawReceiptText);
        boolean valid = isValidReceipt(rawReceiptText, total);
        return new ParseResult(total, date, merchant, currency, valid);
    }
}
