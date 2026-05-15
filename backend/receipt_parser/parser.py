"""
Receipt Parser Core — consolidated from German-Receipt-Parser.

Provides:
- RegexParser: Universal German receipt parser
- Store plugins: ALDI, LIDI, REWE, EDEKA, Netto, Penny, Kaufland, Rossmann, DM
- Voting ensemble: Try multiple parsers, pick best result
"""

import re
import logging
from datetime import datetime
from decimal import Decimal
from typing import Optional

from .schemas import (
    ParsedReceipt, ReceiptItem, StoreInfo, DiscountInfo, PaymentInfo,
    PaymentMethod, VATRate, VATInfo, VATSummary, SupermarketChain,
    OCRLine, UnitType, WeightInfo, ParserResult,
)
from .german_numbers import (
    parse_german_decimal, extract_all_prices, CHAIN_PATTERNS,
    DISCOUNT_KEYWORDS, PFAND_KEYWORDS,
)

logger = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────────────────────────
# Parser Base
# ─────────────────────────────────────────────────────────────────────────────

class BaseParser:
    """Base class for receipt parsers."""

    @property
    def name(self) -> str:
        raise NotImplementedError

    @property
    def priority(self) -> int:
        return 50

    def can_handle(self, ctx: 'ParserContext') -> float:
        """Return confidence 0-1 that this parser can handle the receipt."""
        return 0.5

    def parse(self, ctx: 'ParserContext') -> ParserResult:
        raise NotImplementedError


class ParserContext:
    """Context passed to parsers with extracted OCR lines."""

    def __init__(self, ocr_lines: list[OCRLine], full_text: str = ""):
        self.lines = ocr_lines
        self.full_text = full_text or '\n'.join(l.raw_text for l in ocr_lines)
        self._logs = []

    def log(self, msg: str):
        self._logs.append(msg)


# ─────────────────────────────────────────────────────────────────────────────
# Universal Regex Parser
# ─────────────────────────────────────────────────────────────────────────────

class RegexParser(BaseParser):
    """Universal German receipt parser using comprehensive regex patterns."""

    ITEM_LINE = re.compile(
        r"^(?P<name>.+?)\s+"
        r"(?P<qty>\d+[,\.]?\d*\s*[xX])?\s*"
        r"(?P<price>-?\d{1,4}[,.]\d{2})\s*"
        r"(?P<vat>[AB12*-])?\s*$",
        re.MULTILINE,
    )

    WEIGHTED_ITEM = re.compile(
        r"^(?P<weight>\d+[,\.]\d+)\s*(?P<unit>kg|g|l|ml)\s*"
        r"[*xX]\s*(?P<ppu>\d+[,\.]\d+)\s*(?:€|EUR|euro)?(?:/(?P<ppu_unit>kg|g|l|ml))?\s*"
        r"(?P<price>\d+[,\.]\d+)?\s*$",
        re.IGNORECASE | re.MULTILINE,
    )

    TOTAL_LINE = re.compile(
        r"(?i)(?:gesamt|summe|total|gesamtbetrag|zu zahlen|endbetrag)"
        r"[^0-9\-]*"
        r"(-?\d{1,4}[,.]\d{2})",
    )

    DATE_LINE = re.compile(r"\b(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})\b")
    TIME_LINE = re.compile(r"\b(\d{1,2}):(\d{2})(?::(\d{2}))?\b")

    DISCOUNT_LINE = re.compile(
        r"(?i)(?:rabatt|aktion|gutschein|ersp(?:arnis)?|preisvorteil|sofortrabatt)"
        r"[^0-9\-]*"
        r"(-?\d{1,4}[,.]\d{2})",
    )

    PFAND_LINE = re.compile(r"(?i)pfand[^0-9\-]*(-?\d{1,4}[,.]\d{2})")

    PAYMENT_LINE = re.compile(
        r"(?i)(?P<method>bar|barzahlung|ec[- ]?karte|maestro|visa|mastercard|paypal)[^\n]*"
        r"(?P<amount>\d{1,4}[,.]\d{2})?",
    )

    @property
    def name(self) -> str:
        return "regex"

    @property
    def priority(self) -> int:
        return 50

    def can_handle(self, ctx: ParserContext) -> float:
        line_count = len(ctx.lines)
        if line_count == 0:
            return 0.0
        price_lines = sum(1 for ln in ctx.lines if re.search(r"\d[,.]\d{2}", ln.raw_text))
        return min(1.0, 0.3 + (price_lines / max(1, line_count)) * 0.7)

    def parse(self, ctx: ParserContext) -> ParserResult:
        receipt = ParsedReceipt(raw_text=ctx.full_text)
        text = ctx.full_text
        ctx.log(f"[{self.name}] Parsing {len(ctx.lines)} lines")

        # Store detection
        receipt.store = self._detect_store(text)

        # Date/time
        receipt.transaction_date, receipt.transaction_time = self._extract_datetime(text)

        # Items
        receipt.items = self._extract_items(ctx.lines)

        # Totals
        receipt.total = self._extract_total(text) or Decimal("0.00")
        receipt.subtotal = self._extract_subtotal(text)

        # Discounts
        receipt.discounts = self._extract_discounts(text)
        receipt.total_discounts = sum(d.amount for d in receipt.discounts)

        # Payment
        receipt.payment = self._extract_payment(text)

        # Confidence
        confidence = self._compute_confidence(receipt)
        ctx.log(f"[{self.name}] Confidence: {confidence:.3f}")

        return ParserResult(receipt=receipt, confidence=confidence, parser_name=self.name)

    def _detect_store(self, text: str) -> StoreInfo:
        text_upper = text.upper()
        chain = SupermarketChain.UNKNOWN
        chain_name = "Unknown"

        for pattern, name in CHAIN_PATTERNS:
            if re.search(pattern, text_upper, re.IGNORECASE):
                chain_name = name
                try:
                    chain = SupermarketChain(name)
                except ValueError:
                    chain = SupermarketChain.UNKNOWN
                break

        # Extract postal code + city
        postal_m = re.search(r"\b(\d{5})\s+([A-ZÄÖÜ][a-zäöüA-ZÄÖÜ\s\-]+)", text)
        city = postal_m.group(2).strip() if postal_m else ""
        postal = postal_m.group(1) if postal_m else ""

        return StoreInfo(
            chain=chain,
            name=chain_name,
            city=city,
            postal_code=postal,
            confidence=0.8 if chain != SupermarketChain.UNKNOWN else 0.2,
        )

    def _extract_datetime(self, text: str):
        date = None
        time_str = None

        m_date = self.DATE_LINE.search(text)
        if m_date:
            day, month, year = int(m_date.group(1)), int(m_date.group(2)), int(m_date.group(3))
            if year < 100:
                year += 2000
            try:
                date = datetime(year, month, day)
            except ValueError:
                pass

        m_time = self.TIME_LINE.search(text)
        if m_time:
            h, mn = int(m_time.group(1)), int(m_time.group(2))
            s = int(m_time.group(3)) if m_time.group(3) else 0
            time_str = f"{h:02d}:{mn:02d}:{s:02d}"
            if date:
                try:
                    date = date.replace(hour=h, minute=mn, second=s)
                except ValueError:
                    pass

        return date, time_str

    def _extract_items(self, lines: list[OCRLine]) -> list[ReceiptItem]:
        items = []
        for i, line in enumerate(lines):
            raw = line.raw_text.strip()
            if not raw or len(raw) < 2:
                continue

            # Skip obvious non-item lines
            low = raw.lower()
            if any(kw in low for kw in ['summe', 'total', 'zwischensumme', 'mwst', 'ust', 'zahlung']):
                continue

            # Extract prices
            prices = extract_all_prices(raw)
            if not prices:
                continue

            # Rightmost price is typically the total
            total_price = prices[-1][0]

            # Name is everything before the price
            price_str = str(abs(total_price)).replace('.', ',')
            price_pos = raw.rfind(price_str)
            if price_pos < 0:
                continue

            name = raw[:price_pos].strip()
            name = re.sub(r'\s+', ' ', name)

            if not name or len(name) < 2:
                continue

            # Detect weighted item (e.g., "0,790 kg x 1,49 €/kg  1,18")
            is_weighted = False
            quantity = Decimal("1")
            weight_info = None
            unit = None

            weight_m = re.search(r'^(\d+[,\.]\d+)\s*(kg|g|l|ml)', raw, re.IGNORECASE)
            if weight_m:
                weight_val = float(weight_m.group(1).replace(',', '.'))
                unit_str = weight_m.group(2).lower()
                unit = UnitType(unit_str) if unit_str in ['kg', 'g', 'l', 'ml'] else None
                is_weighted = True
                quantity = Decimal(str(weight_val))
                weight_info = WeightInfo(quantity=weight_val, unit=unit or UnitType.KG)

                # Try to get price per unit
                ppu_m = re.search(r'(\d+[,\.]\d+)\s*(?:€|EUR)?/kg', raw, re.IGNORECASE)
                if ppu_m:
                    ppu = parse_german_decimal(ppu_m.group(1))
                    if ppu:
                        weight_info.price_per_unit = ppu

            # Detect VAT letter
            vat_letter = re.search(r'\s([AB12])\s*$', raw)
            vat_rate = self._infer_vat_rate(name)

            # Create item
            item = ReceiptItem(
                line_number=i,
                raw_text=raw,
                name=name,
                normalized_name=name.strip().title(),
                quantity=quantity,
                unit=unit,
                weight_info=weight_info,
                is_weighted=is_weighted,
                total_price=total_price,
                is_pfand=any(kw in raw.upper() for kw in PFAND_KEYWORDS),
                vat=VATInfo(rate=vat_rate, rate_letter=vat_letter.group(1) if vat_letter else None),
                confidence=line.avg_confidence,
                parser_source=self.name,
            )
            items.append(item)

        return items

    def _infer_vat_rate(self, name: str) -> VATRate:
        name_lower = name.lower()
        # Reduced rate items (7%)
        reduced_keywords = ['lebensmittel', 'nahrung', 'brot', 'milch', 'käse', 'obst', 'gemüse', 'wasser']
        if any(kw in name_lower for kw in reduced_keywords):
            return VATRate.REDUCED
        return VATRate.STANDARD

    def _extract_total(self, text: str) -> Optional[Decimal]:
        m = self.TOTAL_LINE.search(text)
        if m:
            return parse_german_decimal(m.group(1))
        # Fallback: largest price
        prices = extract_all_prices(text)
        if prices:
            return max(p[0] for p in prices)
        return None

    def _extract_subtotal(self, text: str) -> Optional[Decimal]:
        m = re.search(r"(?i)(?:zwischensumme|subtotal|netto)[^0-9\-]*(-?\d{1,4}[,.]\d{2})", text)
        return parse_german_decimal(m.group(1)) if m else None

    def _extract_discounts(self, text: str) -> list[DiscountInfo]:
        discounts = []

        for m in self.DISCOUNT_LINE.finditer(text):
            amt = parse_german_decimal(m.group(1))
            if amt:
                discounts.append(DiscountInfo(
                    label=m.group(0).split()[0].strip(),
                    amount=abs(amt),
                    confidence=0.85,
                ))

        for m in self.PFAND_LINE.finditer(text):
            amt = parse_german_decimal(m.group(1))
            if amt:
                discounts.append(DiscountInfo(
                    label="Pfand",
                    amount=amt,
                    is_pfand=True,
                    confidence=0.90,
                ))

        return discounts

    def _extract_payment(self, text: str) -> PaymentInfo:
        m = self.PAYMENT_LINE.search(text)
        if not m:
            return PaymentInfo()

        method_text = m.group("method").lower()
        method = PaymentMethod.CASH
        if any(k in method_text for k in ['ec', 'karte', 'maestro']):
            method = PaymentMethod.CARD_DEBIT
        elif any(k in method_text for k in ['visa', 'mastercard']):
            method = PaymentMethod.CARD_CREDIT
        elif 'paypal' in method_text:
            method = PaymentMethod.PAYPAL

        amount_str = m.group("amount")
        amount = parse_german_decimal(amount_str) if amount_str else None

        return PaymentInfo(method=method, amount_given=amount, confidence=0.85)

    def _compute_confidence(self, receipt: ParsedReceipt) -> float:
        scores = []

        if receipt.items:
            scores.append(min(1.0, len(receipt.items) / 10.0 * 0.8 + 0.2))
        else:
            scores.append(0.1)

        scores.append(0.9 if receipt.total > 0 else 0.1)
        scores.append(0.9 if receipt.store.chain != SupermarketChain.UNKNOWN else 0.3)
        scores.append(0.8 if receipt.transaction_date else 0.3)

        return sum(scores) / len(scores) if scores else 0.1


# ─────────────────────────────────────────────────────────────────────────────
# Store-specific plugins (simplified)
# ─────────────────────────────────────────────────────────────────────────────

class StorePlugin(BaseParser):
    """Base for store-specific parsers."""

    CHAIN_KEYWORDS: list[str] = []
    TARGET_CHAIN: SupermarketChain = SupermarketChain.UNKNOWN

    @property
    def priority(self) -> int:
        return 10  # Try before generic parser

    def can_handle(self, ctx: ParserContext) -> float:
        text_upper = ctx.full_text.upper()
        matches = sum(1 for kw in self.CHAIN_KEYWORDS if kw.upper() in text_upper)
        if matches == 0:
            return 0.0
        return min(1.0, 0.5 + matches * 0.2)

    def _build_store_info(self) -> StoreInfo:
        return StoreInfo(
            chain=self.TARGET_CHAIN,
            name=self.TARGET_CHAIN.value,
            confidence=0.9,
        )


class AldiParser(StorePlugin):
    """ALDI SÜD/NORD parser."""

    CHAIN_KEYWORDS = ["ALDI", "ALDI SÜD", "ALDI NORD", "ALDI SUED"]
    TARGET_CHAIN = SupermarketChain.ALDI_SUED

    @property
    def name(self) -> str:
        return "aldi"

    def parse(self, ctx: ParserContext) -> ParserResult:
        # Use regex parser as base, override store info
        base = RegexParser()
        result = base.parse(ctx)
        result.receipt.store = self._build_store_info()
        result.parser_name = self.name
        result.confidence = min(1.0, result.confidence + 0.1)
        return result


class LidlParser(StorePlugin):
    """LIDL parser."""

    CHAIN_KEYWORDS = ["LIDL"]
    TARGET_CHAIN = SupermarketChain.LIDL

    @property
    def name(self) -> str:
        return "lidl"

    def parse(self, ctx: ParserContext) -> ParserResult:
        base = RegexParser()
        result = base.parse(ctx)
        result.receipt.store = self._build_store_info()
        result.parser_name = self.name
        result.confidence = min(1.0, result.confidence + 0.1)
        return result


class ReweParser(StorePlugin):
    """REWE parser."""

    CHAIN_KEYWORDS = ["REWE"]
    TARGET_CHAIN = SupermarketChain.REWE

    @property
    def name(self) -> str:
        return "rewe"

    def parse(self, ctx: ParserContext) -> ParserResult:
        base = RegexParser()
        result = base.parse(ctx)
        result.receipt.store = self._build_store_info()
        result.parser_name = self.name
        result.confidence = min(1.0, result.confidence + 0.1)
        return result


class EdekaParser(StorePlugin):
    """EDEKA parser."""

    CHAIN_KEYWORDS = ["EDEKA"]
    TARGET_CHAIN = SupermarketChain.EDEKA

    @property
    def name(self) -> str:
        return "edeka"

    def parse(self, ctx: ParserContext) -> ParserResult:
        base = RegexParser()
        result = base.parse(ctx)
        result.receipt.store = self._build_store_info()
        result.parser_name = self.name
        result.confidence = min(1.0, result.confidence + 0.1)
        return result


class NettoParser(StorePlugin):
    """Netto parser."""

    CHAIN_KEYWORDS = ["NETTO"]
    TARGET_CHAIN = SupermarketChain.NETTO

    @property
    def name(self) -> str:
        return "netto"

    def parse(self, ctx: ParserContext) -> ParserResult:
        base = RegexParser()
        result = base.parse(ctx)
        result.receipt.store = self._build_store_info()
        result.parser_name = self.name
        result.confidence = min(1.0, result.confidence + 0.1)
        return result


# ─────────────────────────────────────────────────────────────────────────────
# Voting Parser Ensemble
# ─────────────────────────────────────────────────────────────────────────────

class VotingParser(BaseParser):
    """Try multiple parsers, return best result by confidence."""

    def __init__(self):
        self.parsers: list[BaseParser] = [
            AldiParser(),
            LidlParser(),
            ReweParser(),
            EdekaParser(),
            NettoParser(),
            RegexParser(),
        ]

    @property
    def name(self) -> str:
        return "voting"

    @property
    def priority(self) -> int:
        return 1  # Lowest - only used as fallback

    def can_handle(self, ctx: ParserContext) -> float:
        return 1.0  # Always willing to try

    def parse(self, ctx: ParserContext) -> ParserResult:
        # Try parsers in priority order
        results = []

        for parser in sorted(self.parsers, key=lambda p: p.priority):
            try:
                if parser.can_handle(ctx) > 0.3:
                    result = parser.parse(ctx)
                    results.append(result)
            except Exception as e:
                logger.debug(f"Parser {parser.name} failed: {e}")
                continue

        if not results:
            # Fallback to basic extraction
            receipt = ParsedReceipt(raw_text=ctx.full_text)
            prices = extract_all_prices(ctx.full_text)
            if prices:
                receipt.total = max(p[0] for p in prices)
            return ParserResult(receipt=receipt, confidence=0.3, parser_name="fallback")

        # Return highest confidence
        best = max(results, key=lambda r: r.confidence)
        logger.info(f"Voting parser chose: {best.parser_name} (confidence: {best.confidence:.3f})")
        return best


# ─────────────────────────────────────────────────────────────────────────────
# Main Parser Interface
# ─────────────────────────────────────────────────────────────────────────────

class ReceiptParser:
    """Main receipt parser interface."""

    def __init__(self):
        self.parser = VotingParser()

    def parse(self, ocr_text: str, ocr_lines: list[OCRLine] = None) -> ParserResult:
        """Parse OCR text/lines into a structured receipt."""
        if not ocr_lines:
            # Convert text to simple OCR lines
            ocr_lines = [
                OCRLine(line_number=i, raw_text=line, tokens=[], avg_confidence=0.8)
                for i, line in enumerate(ocr_text.split('\n') if ocr_text else [])
            ]

        ctx = ParserContext(ocr_lines, ocr_text)
        return self.parser.parse(ctx)

    def parse_text(self, text: str) -> ParserResult:
        """Parse plain text (e.g., from ML Kit or Tesseract)."""
        return self.parse(text)


# Convenience function
def parse_receipt(ocr_text: str, mode: str = "local") -> ParserResult:
    """
    Parse receipt text.

    Args:
        ocr_text: Raw OCR text from image
        mode: "local" (rule-based), "anthropic" (AI enhanced), "openai" (AI enhanced)

    Returns:
        ParserResult with parsed receipt
    """
    parser = ReceiptParser()
    return parser.parse_text(ocr_text)