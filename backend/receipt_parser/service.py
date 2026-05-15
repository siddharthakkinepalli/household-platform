"""
Receipt Service — OCR + Parsing + AI Enhancement.

Handles:
- Image preprocessing
- OCR (PaddleOCR with fallback to simple extraction)
- Rule-based parsing
- Optional AI enhancement (Anthropic/OpenAI)
- User-selectable mode
"""

import base64
import io
import json
import logging
import os
from typing import Optional

import numpy as np

from .parser import ReceiptParser, ParserResult
from .schemas import (
    OCRLine, ReceiptParseResponse, BoundingBox, OCRToken,
    ReceiptItem, StoreInfo, ParsedReceipt,
)

logger = logging.getLogger(__name__)


class ReceiptService:
    """
    Main receipt processing service.

    Supports three modes:
    - "local": Rule-based parsing only (free, offline)
    - "anthropic": Rule-based + AI correction via Claude
    - "openai": Rule-based + AI correction via GPT
    """

    def __init__(self):
        self.parser = ReceiptParser()
        self._ocr_engine = None
        self._ai_client = None

    # ─────────────────────────────────────────────────────────────────────────
    # OCR
    # ─────────────────────────────────────────────────────────────────────────

    def _load_paddleocr(self):
        """Lazy-load PaddleOCR."""
        if self._ocr_engine is not None:
            return self._ocr_engine

        try:
            from paddleocr import PaddleOCR
            self._ocr_engine = PaddleOCR(
                use_angle_cls=True,
                lang="german",
                use_gpu=False,
                show_log=False,
            )
            logger.info("PaddleOCR loaded successfully")
            return self._ocr_engine
        except ImportError:
            logger.warning("PaddleOCR not available, using fallback OCR")
            return None
        except Exception as e:
            logger.warning(f"Failed to load PaddleOCR: {e}")
            return None

    def _simple_ocr(self, image_bytes: bytes) -> list[OCRLine]:
        """
        Fallback simple OCR - tries to extract text from image.
        This is a placeholder - in production you'd use Tesseract or similar.
        """
        # For now, return empty - user should provide OCR text
        # Or use a simple image-to-text service
        logger.info("Using simple OCR fallback - no text extraction available")
        return []

    def process_image(self, image_bytes: bytes) -> list[OCRLine]:
        """Process image to extract text lines."""
        ocr = self._load_paddleocr()

        if ocr is None:
            return self._simple_ocr(image_bytes)

        try:
            # Load image
            import cv2
            nparr = np.frombuffer(image_bytes, np.uint8)
            img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

            if img is None:
                logger.error("Failed to decode image")
                return []

            # Run OCR
            result = ocr.ocr(img, cls=True)

            if result is None or len(result) == 0:
                return []

            lines = []
            raw_lines = result[0] if isinstance(result[0], list) else result

            for idx, item in enumerate(raw_lines):
                if item is None:
                    continue
                try:
                    bbox_pts, (text, conf) = item
                    if not text or not text.strip():
                        continue

                    # Parse bbox
                    xs = [p[0] for p in bbox_pts]
                    ys = [p[1] for p in bbox_pts]
                    bbox = BoundingBox(
                        x_min=float(min(xs)),
                        y_min=float(min(ys)),
                        x_max=float(max(xs)),
                        y_max=float(max(ys)),
                        confidence=float(conf),
                    )

                    token = OCRToken(
                        text=text.strip(),
                        confidence=float(conf),
                        bbox=bbox,
                        source="paddle",
                    )

                    line = OCRLine(
                        line_number=idx,
                        raw_text=text.strip(),
                        tokens=[token],
                        avg_confidence=float(conf),
                        bbox=bbox,
                    )
                    lines.append(line)
                except Exception as e:
                    logger.debug(f"Skipping malformed OCR item: {e}")
                    continue

            # Sort by vertical position
            lines.sort(key=lambda ln: ln.bbox.y_min if ln.bbox else 0.0)
            for i, ln in enumerate(lines):
                ln.line_number = i

            return lines

        except Exception as e:
            logger.error(f"OCR processing failed: {e}")
            return []

    # ─────────────────────────────────────────────────────────────────────────
    # AI Enhancement
    # ─────────────────────────────────────────────────────────────────────────

    def _enhance_with_anthropic(self, ocr_text: str, api_key: str) -> str:
        """Use Claude to clean up OCR errors."""
        try:
            import anthropic

            client = anthropic.Anthropic(api_key=api_key)

            system_prompt = """You are a specialized OCR correction assistant for German supermarket receipts.

YOUR ONLY TASK:
- Correct OCR character recognition errors in the provided receipt text
- Fix corrupted characters that clearly result from OCR scanning artifacts
- Normalize German-format decimal numbers (comma as decimal separator)

STRICT RULES:
1. DO NOT invent, add, or hallucinate any prices, amounts, items, or data
2. DO NOT change any numeric values that could be prices or amounts
3. DO NOT add items that are not in the original text
4. ONLY fix characters that are CLEARLY wrong due to OCR scanning

Return ONLY the corrected text, no explanation."""

            message = client.messages.create(
                model="claude-3-haiku-20240307",
                max_tokens=2000,
                system=system_prompt,
                messages=[{"role": "user", "content": ocr_text}]
            )

            return message.content[0].text

        except ImportError:
            logger.warning("anthropic package not installed")
            return ocr_text
        except Exception as e:
            logger.error(f"Anthropic API error: {e}")
            return ocr_text

    def _enhance_with_openai(self, ocr_text: str, api_key: str) -> str:
        """Use GPT to clean up OCR errors."""
        try:
            from openai import OpenAI

            client = OpenAI(api_key=api_key)

            response = client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[
                    {"role": "system", "content": """Correct OCR errors in German receipts.
Only fix obvious character mistakes.
DO NOT add or invent data.
Return corrected text only."""},
                    {"role": "user", "content": ocr_text}
                ],
                temperature=0,
            )

            return response.choices[0].message.content

        except ImportError:
            logger.warning("openai package not installed")
            return ocr_text
        except Exception as e:
            logger.error(f"OpenAI API error: {e}")
            return ocr_text

    # ─────────────────────────────────────────────────────────────────────────
    # Main Processing
    # ─────────────────────────────────────────────────────────────────────────

    def process(
        self,
        image_bytes: Optional[bytes] = None,
        ocr_text: Optional[str] = None,
        mode: str = "local",
        api_key: Optional[str] = None,
    ) -> ReceiptParseResponse:
        """
        Process receipt image or text.

        Args:
            image_bytes: Raw image data (optional if ocr_text provided)
            ocr_text: Pre-extracted OCR text (optional if image_bytes provided)
            mode: "local", "anthropic", or "openai"
            api_key: API key for AI modes

        Returns:
            ReceiptParseResponse with parsed result
        """
        try:
            # Step 1: Get OCR text
            if image_bytes and not ocr_text:
                ocr_lines = self.process_image(image_bytes)
                ocr_text = '\n'.join(line.raw_text for line in ocr_lines)
            elif not ocr_text:
                return ReceiptParseResponse(
                    success=False,
                    parser="none",
                    confidence=0.0,
                    error="Either image_bytes or ocr_text required",
                    mode=mode,
                )

            if not ocr_text.strip():
                return ReceiptParseResponse(
                    success=False,
                    parser="none",
                    confidence=0.0,
                    error="No text extracted from image",
                    mode=mode,
                )

            # Step 2: AI enhancement if requested
            original_text = ocr_text
            if mode in ("anthropic", "openai") and api_key:
                logger.info(f"Enhancing with {mode}...")
                if mode == "anthropic":
                    ocr_text = self._enhance_with_anthropic(ocr_text, api_key)
                else:
                    ocr_text = self._enhance_with_openai(ocr_text, api_key)
                logger.info(f"AI enhancement complete, text length: {len(ocr_text)}")

            # Step 3: Parse with rule-based parser
            result = self.parser.parse_text(ocr_text)

            # Step 4: Build response
            return ReceiptParseResponse(
                success=True,
                receipt=self._receipt_to_dict(result.receipt),
                parser=result.parser_name,
                confidence=result.confidence,
                mode=mode,
            )

        except Exception as e:
            logger.error(f"Receipt processing failed: {e}")
            return ReceiptParseResponse(
                success=False,
                parser="error",
                confidence=0.0,
                error=str(e),
                mode=mode,
            )

    def _receipt_to_dict(self, receipt: ParsedReceipt) -> dict:
        """Convert ParsedReceipt to dictionary for JSON response."""
        return {
            "store": {
                "chain": receipt.store.chain.value if receipt.store.chain else "UNKNOWN",
                "name": receipt.store.name,
                "city": receipt.store.city,
                "postal_code": receipt.store.postal_code,
                "confidence": receipt.store.confidence,
            },
            "transaction_date": receipt.transaction_date.isoformat() if receipt.transaction_date else None,
            "transaction_time": receipt.transaction_time,
            "items": [
                {
                    "name": item.name,
                    "normalized_name": item.normalized_name,
                    "quantity": float(item.quantity) if item.quantity else 1,
                    "unit": item.unit.value if item.unit else None,
                    "is_weighted": item.is_weighted,
                    "weight_info": {
                        "quantity": item.weight_info.quantity,
                        "unit": item.weight_info.unit.value,
                        "price_per_unit": float(item.weight_info.price_per_unit) if item.weight_info.price_per_unit else None,
                    } if item.weight_info else None,
                    "total_price": float(item.total_price),
                    "is_pfand": item.is_pfand,
                    "confidence": item.confidence,
                }
                for item in receipt.items
            ],
            "total": float(receipt.total),
            "subtotal": float(receipt.subtotal) if receipt.subtotal else None,
            "discounts": [
                {
                    "label": d.label,
                    "amount": float(d.amount),
                    "is_pfand": d.is_pfand,
                }
                for d in receipt.discounts
            ],
            "total_discounts": float(receipt.total_discounts),
            "payment": {
                "method": receipt.payment.method.value if receipt.payment else "unknown",
                "amount_given": float(receipt.payment.amount_given) if receipt.payment and receipt.payment.amount_given else None,
            } if receipt.payment else None,
        }


# Singleton instance
_service: Optional[ReceiptService] = None


def get_receipt_service() -> ReceiptService:
    global _service
    if _service is None:
        _service = ReceiptService()
    return _service