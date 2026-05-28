"""
plaid_routes.py
Plaid Open Banking integration — fetches bank transactions for Commerzbank and N26.

Endpoints (all under /plaid/* prefix set in main.py):
  GET  /plaid/link_token               → create Plaid Link token
  POST /plaid/exchange_token           → exchange public token → store access token
  GET  /plaid/connections              → list active bank connections
  POST /plaid/sync                     → fetch transactions → insert into household_expenses
  DELETE /plaid/connections/<id>       → soft-delete a connection

Duplicate detection:
  Plaid transaction IDs are stable, so each transaction is hashed as MD5("plaid:<transaction_id>").
  This is stored in the same household_expenses.hash column used by CSV imports, preventing
  re-imports across syncs. Cross-source duplicates (same txn imported via CSV and Plaid) use
  different hashes — users can manually exclude duplicates via the excluded flag.
"""

import os
import json
import hashlib
from datetime import datetime, date, timedelta
from pathlib import Path

from dotenv import load_dotenv
from flask import Blueprint, request, jsonify
import requests as http_requests
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# Load credentials (fallback for when the process didn't load them via bat script)
load_dotenv(r'C:\Projects\credentials.env')

PLAID_CLIENT_ID = os.getenv('PLAID_CLIENT_ID', '')
PLAID_SECRET = os.getenv('PLAID_SANDBOX_SECRET', '')
PLAID_ENV = os.getenv('PLAID_ENV', 'sandbox')
PLAID_BASE_URL = f'https://{PLAID_ENV}.plaid.com'

# Database (same file as main.py — SQLite allows multiple connections)
BACKEND_DIR = Path(__file__).parent
_engine = create_engine(f'sqlite:///{BACKEND_DIR / "household_platform.db"}', echo=False)
_Session = sessionmaker(bind=_engine)


def get_db():
    return _Session()


plaid_bp = Blueprint('plaid', __name__)


def _plaid_post(endpoint: str, payload: dict) -> dict:
    """POST to Plaid API with credentials injected."""
    payload = {**payload, 'client_id': PLAID_CLIENT_ID, 'secret': PLAID_SECRET}
    resp = http_requests.post(
        f'{PLAID_BASE_URL}{endpoint}',
        json=payload,
        headers={'Content-Type': 'application/json'},
        timeout=30,
    )
    data = resp.json()
    if resp.status_code != 200:
        error_msg = data.get('error_message') or data.get('display_message') or str(data)
        raise RuntimeError(f'Plaid {resp.status_code}: {error_msg}')
    return data


def _plaid_hash(plaid_transaction_id: str) -> str:
    return hashlib.md5(f'plaid:{plaid_transaction_id}'.encode()).hexdigest()


# ---------------------------------------------------------------------------
# Lazy import to avoid circular import (BankConnection is in household_models
# which is also imported by main.py — importing here avoids module-load order issues)
# ---------------------------------------------------------------------------

def _models():
    from household_models import BankConnection, HouseholdExpense, HouseholdProfile
    return BankConnection, HouseholdExpense, HouseholdProfile


# ---------------------------------------------------------------------------
# GET /plaid/link_token
# ---------------------------------------------------------------------------

@plaid_bp.route('/link_token', methods=['GET'])
def create_link_token():
    """
    GET /plaid/link_token?household_id=1
    Creates a short-lived Plaid Link token. The frontend passes this to Plaid Link JS
    to open the bank authentication flow.

    Note for production: European banks (N26, Commerzbank) use OAuth. Add redirect_uri
    matching your frontend URL and handle received_redirect_uri on return.
    """
    household_id = request.args.get('household_id', type=int)
    if not household_id:
        return jsonify({'error': 'household_id is required'}), 400

    if not PLAID_CLIENT_ID or not PLAID_SECRET:
        return jsonify({'error': 'Plaid credentials not configured in credentials.env'}), 500

    try:
        data = _plaid_post('/link/token/create', {
            'user': {'client_user_id': f'household_{household_id}'},
            'client_name': 'Household Platform',
            'products': ['transactions'],
            'country_codes': ['DE'],
            'language': 'en',
        })
        return jsonify({'link_token': data['link_token']}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# POST /plaid/exchange_token
# ---------------------------------------------------------------------------

@plaid_bp.route('/exchange_token', methods=['POST'])
def exchange_token():
    """
    POST /plaid/exchange_token
    Body: {
      "household_id": 1,
      "public_token": "public-sandbox-...",
      "institution_name": "N26",
      "institution_id": "ins_12345"
    }
    Exchanges the short-lived public_token for a permanent access_token and
    stores it in bank_connections. Reconnecting the same item_id updates the record.
    """
    body = request.get_json() or {}
    household_id = body.get('household_id')
    public_token = body.get('public_token')

    if not household_id or not public_token:
        return jsonify({'error': 'household_id and public_token are required'}), 400

    try:
        exchange = _plaid_post('/item/public_token/exchange', {'public_token': public_token})
        access_token = exchange['access_token']
        item_id = exchange['item_id']

        accounts_data = _plaid_post('/accounts/get', {'access_token': access_token})
        account_ids = json.dumps([a['account_id'] for a in accounts_data.get('accounts', [])])

        BankConnection, _, _ = _models()
        db = get_db()

        existing = db.query(BankConnection).filter_by(item_id=item_id).first()
        if existing:
            existing.access_token = access_token
            existing.bank_name = body.get('institution_name') or existing.bank_name
            existing.institution_id = body.get('institution_id') or existing.institution_id
            existing.account_ids = account_ids
            existing.is_active = True
            db.commit()
            result = existing.to_dict()
        else:
            conn = BankConnection(
                household_id=household_id,
                item_id=item_id,
                access_token=access_token,
                bank_name=body.get('institution_name', 'Unknown Bank'),
                institution_id=body.get('institution_id'),
                account_ids=account_ids,
            )
            db.add(conn)
            db.commit()
            db.refresh(conn)
            result = conn.to_dict()

        db.close()
        return jsonify({'connection': result}), 201

    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# GET /plaid/connections
# ---------------------------------------------------------------------------

@plaid_bp.route('/connections', methods=['GET'])
def list_connections():
    """
    GET /plaid/connections?household_id=1
    Returns all active Plaid bank connections (without access tokens).
    """
    household_id = request.args.get('household_id', type=int)
    if not household_id:
        return jsonify({'error': 'household_id is required'}), 400

    BankConnection, _, _ = _models()
    db = get_db()
    connections = db.query(BankConnection).filter_by(household_id=household_id, is_active=True).all()
    result = [c.to_dict() for c in connections]
    db.close()
    return jsonify({'connections': result}), 200


# ---------------------------------------------------------------------------
# POST /plaid/sync
# ---------------------------------------------------------------------------

@plaid_bp.route('/sync', methods=['POST'])
def sync_transactions():
    """
    POST /plaid/sync
    Body: { "household_id": 1, "connection_id": 1 }  (connection_id optional — syncs all if omitted)

    Fetches up to 90 days of transactions from Plaid and inserts new ones into
    household_expenses. Uses MD5("plaid:<plaid_transaction_id>") as the dedup hash.

    Returns: { "imported": 12, "skipped": 3 }
    """
    body = request.get_json() or {}
    household_id = body.get('household_id')
    connection_id = body.get('connection_id')

    if not household_id:
        return jsonify({'error': 'household_id is required'}), 400

    BankConnection, HouseholdExpense, _ = _models()
    db = get_db()

    q = db.query(BankConnection).filter_by(household_id=household_id, is_active=True)
    if connection_id:
        q = q.filter_by(id=connection_id)
    connections = q.all()

    if not connections:
        db.close()
        return jsonify({'error': 'No active bank connections found'}), 404

    total_imported = 0
    total_skipped = 0

    for conn in connections:
        try:
            end_date = date.today().isoformat()
            start_date = (date.today() - timedelta(days=90)).isoformat()

            txn_data = _plaid_post('/transactions/get', {
                'access_token': conn.access_token,
                'start_date': start_date,
                'end_date': end_date,
                'options': {'count': 500, 'offset': 0},
            })

            imported = 0
            skipped = 0

            for txn in txn_data.get('transactions', []):
                plaid_id = txn.get('transaction_id', '')
                tx_hash = _plaid_hash(plaid_id)

                if db.query(HouseholdExpense).filter_by(hash=tx_hash).first():
                    skipped += 1
                    continue

                # Plaid: positive amount = money out (expense); we store as negative
                amount = -txn['amount']

                # Category: prefer personal_finance_category (v2) then legacy category array
                pfc = txn.get('personal_finance_category')
                if pfc:
                    category = pfc.get('primary', '').replace('_', ' ').title()
                elif txn.get('category'):
                    category = txn['category'][0]
                else:
                    category = 'Other'

                description = (
                    txn.get('merchant_name')
                    or txn.get('name')
                    or 'Unknown'
                )

                expense = HouseholdExpense(
                    household_id=household_id,
                    date=txn['date'],
                    description=description,
                    amount=amount,
                    category=category,
                    bank=conn.bank_name,
                    hash=tx_hash,
                )
                db.add(expense)
                imported += 1

            db.commit()
            conn.last_sync_at = datetime.utcnow()
            db.commit()

            total_imported += imported
            total_skipped += skipped

        except Exception as e:
            db.rollback()
            db.close()
            return jsonify({'error': f'Sync failed for {conn.bank_name}: {str(e)}'}), 500

    db.close()
    return jsonify({'imported': total_imported, 'skipped': total_skipped}), 200


# ---------------------------------------------------------------------------
# DELETE /plaid/connections/<id>
# ---------------------------------------------------------------------------

@plaid_bp.route('/connections/<int:connection_id>', methods=['DELETE'])
def remove_connection(connection_id):
    """
    DELETE /plaid/connections/<id>?household_id=1
    Soft-deletes a bank connection (sets is_active=False).
    Existing imported transactions are not deleted.
    """
    household_id = request.args.get('household_id', type=int)
    if not household_id:
        return jsonify({'error': 'household_id is required'}), 400

    BankConnection, _, _ = _models()
    db = get_db()
    conn = db.query(BankConnection).filter_by(id=connection_id, household_id=household_id).first()
    if not conn:
        db.close()
        return jsonify({'error': 'Connection not found'}), 404

    conn.is_active = False
    db.commit()
    db.close()
    return jsonify({'deleted': True}), 200
