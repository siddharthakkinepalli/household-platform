"""
Receipt data schemas — mirrors German-Receipt-Parser structures.
"""

from __future__ import annotations
from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Optional


class SupermarketChain(str, Enum):
    ALDI_SUED = "ALDI SÜD"
    ALDI_NORD = "ALDI NORD"
    LIDL = "LIDL"
    REWE = "REWE"
    EDEKA = "EDEKA"
    NETTO = "NETTO"
    PENNY = "PENNY"
    KAUFLAND = "KAUFLAND"
    ROSSMANN = "ROSSMANN"
    DM = "DM"
    UNKNOWN = "UNKNOWN"


class PaymentMethod(str, Enum):
    CASH = "cash"
    CARD_DEBIT = "card_debit"
    CARD_CREDIT = "card_credit"
    PAYPAL = "paypal"
    APPLE_PAY = "apple_pay"
    GOOGLE_PAY = "google_pay"
    UNKNOWN = "unknown"


class VATRate(str, Enum):
    STANDARD = "19%"  # A
    REDUCED = "7%"    # B
    ZERO = "0%"       # 1
    UNKNOWN = "unknown"


class UnitType(str, Enum):
    KG = "kg"
    G = "g"
    L = "l"
    ML = "ml"
    PIECE = "pc"
    UNKNOWN = "unknown"


@dataclass
class BoundingBox:
    x_min: float = 0.0
    y_min: float = 0.0
    x_max: float = 0.0
    y_max: float = 0.0
    confidence: float = 0.0


@dataclass
class OCRToken:
    text: str
    confidence: float
    bbox: BoundingBox
    source: str = "unknown"


@dataclass
class OCRLine:
    line_number: int
    raw_text: str
    tokens: list[OCRToken]
    avg_confidence: float
    bbox: Optional[BoundingBox] = None
    classification: str = "item"  # item, total, subtotal, vat, payment, separator, meta, header


@dataclass
class WeightInfo:
    quantity: float
    unit: UnitType
    price_per_unit: Optional[Decimal] = None


@dataclass
class VATInfo:
    rate: VATRate = VATRate.UNKNOWN
    rate_letter: Optional[str] = None
    gross_amount: Decimal = Decimal("0.00")
    net_amount: Decimal = Decimal("0.00")
    vat_amount: Decimal = Decimal("0.00")
    confidence: float = 0.0


@dataclass
class VATSummary:
    buckets: list[VATInfo] = field(default_factory=list)
    total_vat: Decimal = Decimal("0.00")
    total_gross: Decimal = Decimal("0.00")
    total_net: Decimal = Decimal("0.00")


@dataclass
class DiscountInfo:
    label: str
    amount: Decimal = Decimal("0.00")
    is_pfand: bool = False
    is_coupon: bool = False
    confidence: float = 0.0


@dataclass
class PaymentInfo:
    method: PaymentMethod = PaymentMethod.UNKNOWN
    amount_given: Optional[Decimal] = None
    amount_change: Optional[Decimal] = None
    confidence: float = 0.0


@dataclass
class StoreInfo:
    chain: SupermarketChain = SupermarketChain.UNKNOWN
    name: str = "Unknown"
    store_id: Optional[str] = None
    address: str = ""
    city: str = ""
    postal_code: str = ""
    tax_id: Optional[str] = None
    confidence: float = 0.0


@dataclass
class ReceiptItem:
    line_number: int
    raw_text: str
    name: str = ""
    normalized_name: str = ""
    quantity: Decimal = Decimal("1")
    unit: Optional[UnitType] = None
    weight_info: Optional[WeightInfo] = None
    is_weighted: bool = False
    total_price: Decimal = Decimal("0.00")
    is_pfand: bool = False
    is_coupon: bool = False
    vat: Optional[VATInfo] = None
    confidence: float = 0.0
    parser_source: str = "unknown"


@dataclass
class ParsedReceipt:
    """Complete parsed receipt structure."""
    store: StoreInfo = field(default_factory=StoreInfo)
    transaction_date: Optional[datetime] = None
    transaction_time: Optional[str] = None
    items: list[ReceiptItem] = field(default_factory=list)
    total: Decimal = Decimal("0.00")
    subtotal: Optional[Decimal] = None
    vat_summary: Optional[VATSummary] = None
    discounts: list[DiscountInfo] = field(default_factory=list)
    total_discounts: Decimal = Decimal("0.00")
    payment: Optional[PaymentInfo] = None
    raw_text: str = ""


@dataclass
class ParserResult:
    """Result from a parser run."""
    receipt: ParsedReceipt
    confidence: float
    parser_name: str


@dataclass
class ReceiptParseResponse:
    """API response structure."""
    success: bool
    parser: str = ""
    confidence: float = 0.0
    receipt: Optional[dict] = None
    error: Optional[str] = None
    mode: str = "local"  # local, anthropic, openai

    def to_dict(self):
        return {
            'success': self.success,
            'receipt': self.receipt,
            'parser': self.parser,
            'confidence': self.confidence,
            'error': self.error,
            'mode': self.mode,
        }