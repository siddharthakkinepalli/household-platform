"""
Receipt Parser Module for Household Platform

Consolidated receipt scanning solution combining:
- PaddleOCR (with fallback to Tesseract/simple OCR)
- Comprehensive German receipt parsing with store plugins
- Optional AI cleanup (Anthropic/OpenAI)
- User-selectable parser mode (local/AI)

Usage:
    from receipt_parser import ReceiptParser, parse_receipt

    # Local parsing (free)
    result = parse_receipt(ocr_text, mode='local')

    # AI-enhanced parsing
    result = parse_receipt(ocr_text, mode='anthropic', api_key='sk-...')
"""

from .parser import ReceiptParser
from .service import ReceiptService
from .routes import register_receipt_routes

__all__ = ['ReceiptParser', 'ReceiptService', 'register_receipt_routes']