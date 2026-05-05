"""
expenses_routes.py
Adapter that wraps Expenses functionality from c:/Projects/apps/Expenses/

This module provides utilities to:
- Parse CSV statements using combined_statement_parser
- Categorize transactions using simple_categorizer
- Query expense database
"""

import sys
import hashlib
from pathlib import Path
from datetime import datetime
from typing import List, Dict

# Expenses app path
EXPENSES_APP_PATH = Path(__file__).parent.parent.parent / 'apps' / 'Expenses'

# Add to path so we can import from Expenses app
sys.path.insert(0, str(EXPENSES_APP_PATH))


class ExpensesAdapter:
    """Adapter to use Expenses app logic."""

    def __init__(self):
        """Initialize adapter and load Expenses modules."""
        try:
            self.combined_parser = __import__('combined_statement_parser')
            self.simple_categorizer = __import__('simple_categorizer')
            self.budget_config = __import__('budget_config')
        except ImportError as e:
            print(f"Warning: Could not import Expenses modules: {e}")

    def parse_csv_statement(self, csv_file: str, bank: str = None) -> List[Dict]:
        """
        Parse a CSV bank statement file.
        Uses combined_statement_parser to auto-detect bank format.

        Args:
            csv_file: Path to CSV file
            bank: Optional bank name override ('commerzbank', 'ing', 'n26', 'revolut')

        Returns:
            List of transaction dicts with: date, description, amount, bank, category
        """
        try:
            parser = self.combined_parser.CombinedStatementParser()
            transactions = parser.parse_file(str(csv_file), bank=bank)
            return transactions
        except Exception as e:
            print(f"Error parsing {csv_file}: {e}")
            return []

    def categorize_transactions(self, transactions: List[Dict]) -> List[Dict]:
        """
        Categorize transactions using simple keyword-based rules.

        Args:
            transactions: List of transaction dicts

        Returns:
            List of transactions with 'category' and 'budget_category' fields added
        """
        try:
            categorizer = self.simple_categorizer.SimpleExpenseCategorizer()
            categorized = categorizer.categorize_transactions(transactions)
            return categorized
        except Exception as e:
            print(f"Error categorizing transactions: {e}")
            return transactions

    def map_to_budget_category(self, category: str) -> str:
        """
        Map transaction category to budget category using budget_config rules.

        Args:
            category: Transaction category from categorizer

        Returns:
            Budget category (e.g. 'Grocery', 'Travel') or empty string
        """
        try:
            mapped = self.budget_config.map_transaction_to_budget_category(category)
            return mapped or ''
        except Exception as e:
            print(f"Error mapping category '{category}': {e}")
            return ''


def make_transaction_hash(date: str, description: str, amount: float, bank: str) -> str:
    """
    Generate stable hash for transaction (prevents duplicates).

    Args:
        date: Transaction date (ISO format)
        description: Transaction description
        amount: Amount
        bank: Bank name

    Returns:
        MD5 hex hash
    """
    key = f"{date}|{description}|{amount}|{bank}"
    return hashlib.md5(key.encode()).hexdigest()


def normalize_date(raw: str) -> str:
    """
    Normalize various date formats to YYYY-MM-DD.

    Supported formats:
    - YYYY-MM-DD
    - DD.MM.YYYY
    - MM/DD/YYYY
    - DD/MM/YYYY
    - YYYY/MM/DD
    """
    if not raw:
        return ''

    raw = raw.strip().split(' ')[0]  # drop time portion

    for fmt in ('%Y-%m-%d', '%d.%m.%Y', '%m/%d/%Y', '%d/%m/%Y', '%Y/%m/%d'):
        try:
            return datetime.strptime(raw, fmt).strftime('%Y-%m-%d')
        except ValueError:
            continue

    return raw  # return as-is if unparseable


# Module-level adapter instance
_adapter = None


def get_adapter() -> ExpensesAdapter:
    """Get or create the Expenses adapter."""
    global _adapter
    if _adapter is None:
        _adapter = ExpensesAdapter()
    return _adapter
