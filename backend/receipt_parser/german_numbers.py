"""
German number parsing helpers — extracted and simplified from German-Receipt-Parser.
"""

import re
from decimal import Decimal
from typing import Optional


def parse_german_decimal(text: str) -> Optional[Decimal]:
    """Parse German-format decimal (comma as separator)."""
    if not text:
        return None

    # Remove currency symbols and whitespace
    cleaned = re.sub(r'[€EUR\s]', '', text.strip())

    # German format: 1.234,56 → 1234.56
    # First, handle the comma as decimal separator
    if ',' in cleaned and '.' in cleaned:
        # Both present: assume last separator is decimal
        if cleaned.rfind(',') > cleaned.rfind('.'):
            # Format: 1.234,56
            cleaned = cleaned.replace('.', '').replace(',', '.')
        else:
            # Format: 1,234.56 (unlikely in German)
            cleaned = cleaned.replace(',', '')
    elif ',' in cleaned:
        # Only comma - could be decimal or thousand separator
        parts = cleaned.rsplit(',', 1)
        if len(parts[1]) == 2:
            # 1,99 → decimal
            cleaned = parts[0].replace('.', '') + '.' + parts[1]
        else:
            # 1,234 → thousand separator
            cleaned = cleaned.replace(',', '')
    else:
        # No comma - just remove any thousand dots
        cleaned = cleaned.replace('.', '')

    try:
        return Decimal(cleaned)
    except (ValueError, Exception):
        return None


def extract_all_prices(text: str) -> list[tuple[Decimal, int, int]]:
    """
    Extract all prices from text, returning (value, start_pos, end_pos).
    """
    prices = []
    pattern = r'-?\d{1,4}[.,]\d{2}'

    for m in re.finditer(pattern, text):
        val = parse_german_decimal(m.group())
        if val is not None:
            prices.append((val, m.start(), m.end()))

    # Sort by position
    prices.sort(key=lambda x: x[1])
    return prices


# German supermarket chain patterns
CHAIN_PATTERNS = [
    (r'ALDI\s*S[ÜU]D', 'ALDI SÜD'),
    (r'ALDI\s*NORD', 'ALDI NORD'),
    (r'LIDL', 'LIDL'),
    (r'REWE\s*(MARKT|KG)?', 'REWE'),
    (r'EDEKA', 'EDEKA'),
    (r'NETTO\s*(MARKT)?', 'NETTO'),
    (r'PENNY', 'PENNY'),
    (r'KAUFLAND', 'KAUFLAND'),
    (r'ROSSMANN', 'ROSSMANN'),
    (r'DM\s*(DROGERIE)?', 'DM'),
]

DISCOUNT_KEYWORDS = [
    'RABATT', 'AKTION', 'GUTSCHEIN', 'ERSPARNIS', 'PREISVORTEIL',
    'SOFORTRABATT', 'SPAREN', 'ANGEBOT', 'EXTRA',
]

PFAND_KEYWORDS = ['PFAND', 'EINWEG', 'DOSE', 'FLASCHE']