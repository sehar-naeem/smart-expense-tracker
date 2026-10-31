"""
=============================================================================
Smart Expense Tracker - Receipt Text Parser
-----------------------------------------------------------------------------
Extracts key structured data from raw OCR receipt text:
 1. Total Amount (supports multi-currency: Rs, PKR, $, €, £, ₹, AED, SAR)
 2. Clean Merchant / Store Title (filters out headers like TAX INVOICE)
 3. Date of transaction
 4. Currency Symbol Detection
=============================================================================
"""

import re
from typing import Optional, Dict, Any, List


class ReceiptParser:
    """Parser to extract Total, Date, and Merchant from raw OCR text."""

    # Regex patterns for finding total amounts across global and South Asian receipts
    TOTAL_PATTERNS = [
        r'(?i)(?:grand\s+total|total\s+amount|total\s+due|amount\s+due|net\s+payable|amount\s+payable|balance\s+due|bill\s+amount|net\s+total|final\s+total|total)[\s\:\=\-]*'
        r'(?:rs\.?|pkr|inr|usd|eur|gbp|aed|sar|[\$€£₹])?[\s]*'
        r'([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:\/\-)?',

        r'(?i)(?:total|payable|due)[\s\:\=\-]*([0-9]+(?:\.[0-9]{1,2})?|\d{1,3}(?:,\d{3})+)',

        r'(?i)(?:subtotal|sub\s+total)[\s\:\=\-]*(?:rs\.?|pkr|[\$€£₹])?[\s]*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)'
    ]

    DATE_PATTERNS = [
        r'\b([0-9]{1,2}[-\/\.][0-9]{1,2}[-\/\.][0-9]{2,4})\b',
        r'\b([0-9]{4}[-\/\.][0-9]{1,2}[-\/\.][0-9]{1,2})\b',
        r'(?i)\b([0-9]{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\s,]+[0-9]{2,4})\b'
    ]

    CURRENCY_PATTERNS = [
        (r'(?i)\b(?:rs\.?|pkr)\b', 'Rs. '),
        (r'(?i)\b(?:₹|inr)\b', '₹'),
        (r'(?i)\b(?:€|eur)\b', '€'),
        (r'(?i)\b(?:£|gbp)\b', '£'),
        (r'(?i)\b(?:aed)\b', 'AED '),
        (r'(?i)\b(?:sar)\b', 'SAR '),
        (r'(?i)(?:\$|\busd\b)', '$'),
    ]

    GENERIC_NOISE_WORDS = {
        'tax', 'invoice', 'sales', 'receipt', 'cash', 'memo', 'bill', 'order',
        'table', 'welcome', 'thank', 'thanks', 'you', 'customer', 'copy', 'merchant',
        'original', 'duplicate', 'token', 'terminal', 'pos', 'slip', 'gst', 'ntn',
        'strn', 'tin', 'trn', 'vat', 'retail', 'payment', 'counter', 'date', 'time',
        'subtotal', 'total', 'item', 'items', 'qty', 'rate', 'amount', 'change'
    }

    @classmethod
    def extract_currency(cls, text: str) -> Optional[str]:
        """Detects currency symbol mentioned in receipt text."""
        for pattern, symbol in cls.CURRENCY_PATTERNS:
            if re.search(pattern, text):
                return symbol
        return None

    @classmethod
    def extract_total(cls, text: str) -> Optional[float]:
        """
        Extracts the total amount from receipt text.
        Supports decimal numbers (430.50), integers (430), comma separators (1,500), and suffixes (1500/-).
        """
        for pattern in cls.TOTAL_PATTERNS:
            matches = list(re.finditer(pattern, text))
            if matches:
                val_str = matches[-1].group(1).replace(',', '')
                try:
                    val = float(val_str)
                    if val > 0:
                        return val
                except ValueError:
                    continue

        # Fallback 1: Currency-prefixed numbers (e.g. Rs. 1500 or $45)
        curr_amounts = re.findall(
            r'(?i)(?:rs\.?|pkr|[\$€£₹])\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)',
            text
        )
        if curr_amounts:
            parsed = []
            for a in curr_amounts:
                try:
                    parsed.append(float(a.replace(',', '')))
                except ValueError:
                    pass
            if parsed:
                return max(parsed)

        # Fallback 2: Largest decimal number
        decimals = re.findall(r'\b([0-9]+\.[0-9]{2})\b', text)
        if decimals:
            try:
                floats = [float(d) for d in decimals]
                return max(floats)
            except ValueError:
                pass

        return None

    @classmethod
    def extract_merchant(cls, text: str) -> str:
        """
        Intelligently identifies the clean merchant/store name from receipt header,
        ignoring noise like 'TAX INVOICE', 'WELCOME', dates, and phone numbers.
        """
        lines = [line.strip() for line in text.split('\n') if line.strip()]
        candidates = []

        for i, line in enumerate(lines[:10]):
            cleaned = re.sub(r'^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$', '', line).strip()
            letters = re.findall(r'[a-zA-Z]', cleaned)

            if len(letters) < 3 or len(cleaned) < 3 or len(cleaned) > 45:
                continue

            words = [w.lower() for w in re.findall(r'[a-zA-Z]+', cleaned)]
            if not words:
                continue

            noise_count = sum(1 for w in words if w in cls.GENERIC_NOISE_WORDS)
            if noise_count == len(words) or (len(words) > 1 and noise_count / len(words) >= 0.6):
                continue

            if re.search(r'(?i)\b(?:tel|phone|ph|cell|mob|email|www|http|\.com|\.pk|strn|ntn|gst)\b', cleaned):
                continue

            score = 100 - (i * 10)
            if cleaned.isupper():
                score += 30
            if re.search(r"(?i)\b(mart|store|supermarket|bakers|restaurant|cafe|pharmacy|station|shop|mall|express)\b", cleaned):
                score += 40

            candidates.append((score, cleaned))

        if candidates:
            candidates.sort(key=lambda x: x[0], reverse=True)
            return candidates[0][1]

        for line in lines[:5]:
            clean = re.sub(r'[^a-zA-Z0-9\s\-\&]', '', line).strip()
            if len(re.findall(r'[a-zA-Z]', clean)) >= 3:
                return clean

        return "Receipt Expense"

    @classmethod
    def extract_date(cls, text: str) -> Optional[str]:
        """Extracts the first valid date string found in the receipt text."""
        for pattern in cls.DATE_PATTERNS:
            match = re.search(pattern, text)
            if match:
                return match.group(1).strip()
        return None

    @classmethod
    def parse(cls, raw_receipt_text: str) -> Dict[str, Any]:
        """Parses raw text and returns extracted fields in a clean dictionary."""
        total = cls.extract_total(raw_receipt_text)
        date = cls.extract_date(raw_receipt_text)
        merchant = cls.extract_merchant(raw_receipt_text)
        currency = cls.extract_currency(raw_receipt_text)

        return {
            "total_amount": total,
            "date": date,
            "merchant_title": merchant,
            "detected_currency": currency,
            "raw_text_length": len(raw_receipt_text)
        }


# =============================================================================
# Quick Test Demo
# =============================================================================
if __name__ == "__main__":
    sample_receipt_pakistan = """
    TAX INVOICE
    CASH RECEIPT
    AL-FATAH DEPARTMENT STORE
    Gulberg III, Lahore
    Tel: 042-35751234
    STRN: 123456789
    Date: 18/10/2026 Time: 14:30
    ----------------------------
    Milk 1L              250
    Bread Large          180
    ----------------------------
    TOTAL:               Rs. 430
    CASH:                500
    CHANGE:               70
    THANK YOU FOR SHOPPING!
    """

    sample_receipt_us = """
    SHELL OIL 44210
    DATE: 2026-10-14
    PUMP #04 REGULAR UNLEADED
    GALLONS: 10.5
    PRICE/GAL: $3.89
    TOTAL DUE:            $40.85
    CASH TENDERED:        $50.00
    CHANGE:                $9.15
    """

    for idx, receipt in enumerate([sample_receipt_pakistan, sample_receipt_us], 1):
        print("=" * 50)
        print(f"TEST RECEIPT #{idx}")
        print("=" * 50)
        result = ReceiptParser.parse(receipt)
        print(f"Merchant : {result['merchant_title']}")
        print(f"Currency : {result['detected_currency']}")
        print(f"Total    : {result['total_amount']}")
        print(f"Date     : {result['date']}")
        print()
