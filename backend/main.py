"""
main.py
Flask app entry point for Household Platform backend.

Endpoints:
  GET  /health                         → app status
  POST /household/create               → initialize household
  GET  /household/{id}                 → get household info
  GET  /expenses/transactions          → list transactions (filters by household_id)
  POST /expenses/import                → import CSV/statements
  POST /backup/export                  → create backup JSON
  POST /backup/restore                 → restore from JSON
"""

import sys
import os
import json
import uuid
from pathlib import Path
from datetime import datetime
from urllib import request as urlrequest
from urllib import error as urlerror

from flask import Flask, request, jsonify
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# Add backend dir to path for local imports
BACKEND_DIR = Path(__file__).parent
sys.path.insert(0, str(BACKEND_DIR))

from household_models import Base, HouseholdProfile, HouseholdMember, HouseholdExpense, HouseholdBackup
from household_models import HouseholdEvent, AutomationLog
from ai_pipeline import (
    extract_structured_data,
    normalize_payload,
    build_event_graph,
    apply_automation_rules,
)

# ---------------------------------------------------------------------------
# Flask Setup
# ---------------------------------------------------------------------------

app = Flask(__name__, static_folder=None)

# Database
DB_PATH = BACKEND_DIR / 'household_platform.db'
DATABASE_URL = f'sqlite:///{DB_PATH}'
engine = create_engine(DATABASE_URL, echo=False)
SessionLocal = sessionmaker(bind=engine)

# Create tables on startup
Base.metadata.create_all(engine)

PORT = 5000
OCR_BASE_URL = os.getenv('HOUSEHOLD_OCR_BASE_URL', 'http://127.0.0.1:5000').rstrip('/')


def get_db():
    """Get database session."""
    return SessionLocal()


def _persist_pipeline_result(db, household_id: int, source: str, raw_text: str):
    """Run extraction/graph/automation and persist event + queued actions."""
    extracted = extract_structured_data(raw_text)
    normalized = normalize_payload(extracted, source=source)
    graph = build_event_graph(normalized)
    actions = apply_automation_rules(graph)

    event = HouseholdEvent(
        household_id=household_id,
        title=normalized['title'],
        event_date=normalized.get('event_date'),
        event_time=normalized.get('event_time'),
        event_type=normalized.get('event_type', 'event'),
        source=source,
        raw_text=raw_text,
        confidence=normalized.get('confidence', 0.0),
    )
    db.add(event)
    db.commit()
    db.refresh(event)

    logs = []
    for act in actions:
        log = AutomationLog(
            household_id=household_id,
            event_id=event.id,
            action=act.get('action', 'unknown'),
            payload=json.dumps(act),
            status='queued',
        )
        db.add(log)
        logs.append(log)

    db.commit()

    return {
        'event': event.to_dict(),
        'graph': graph,
        'automation_actions': [json.loads(l.payload) if l.payload else {} for l in logs],
    }


def _extract_text_via_existing_ocr(file_name: str, content_type: str, file_bytes: bytes) -> dict:
    """Forward the uploaded file to the existing Grocery OCR backend (/ocr)."""
    boundary = f'----HouseholdOCRBoundary{uuid.uuid4().hex}'
    safe_name = file_name or 'capture.jpg'
    safe_type = content_type or 'application/octet-stream'

    body_start = (
        f'--{boundary}\r\n'
        f'Content-Disposition: form-data; name="file"; filename="{safe_name}"\r\n'
        f'Content-Type: {safe_type}\r\n\r\n'
    ).encode('utf-8')
    body_end = f'\r\n--{boundary}--\r\n'.encode('utf-8')
    payload = body_start + file_bytes + body_end

    req = urlrequest.Request(
        f'{OCR_BASE_URL}/ocr',
        data=payload,
        headers={
            'Content-Type': f'multipart/form-data; boundary={boundary}',
            'Content-Length': str(len(payload)),
        },
        method='POST',
    )

    try:
        with urlrequest.urlopen(req, timeout=25) as resp:
            raw = resp.read().decode('utf-8')
            return json.loads(raw)
    except urlerror.HTTPError as e:
        err_text = e.read().decode('utf-8', errors='ignore') if e.fp else str(e)
        raise RuntimeError(f'OCR HTTP {e.code}: {err_text}') from e
    except urlerror.URLError as e:
        raise RuntimeError(f'Cannot reach OCR service at {OCR_BASE_URL}: {e.reason}') from e


# ---------------------------------------------------------------------------
# Health Check
# ---------------------------------------------------------------------------

@app.route('/health', methods=['GET'])
def health():
    """
    GET /health
    Returns app status.
    """
    return jsonify({
        'status': 'ok',
        'service': 'household-platform',
        'timestamp': datetime.utcnow().isoformat(),
    }), 200


# ---------------------------------------------------------------------------
# Household CRUD
# ---------------------------------------------------------------------------

@app.route('/household/create', methods=['POST'])
def create_household():
    """
    POST /household/create
    Body: {
      "name": "My Household",
      "description": "Family home",
      "currency": "USD"
    }
    Returns: household object with id
    """
    try:
        data = request.get_json() or {}
        name = data.get('name')
        description = data.get('description', '')
        currency = data.get('currency', 'USD')

        if not name:
            return jsonify({'error': 'name is required'}), 400

        db = get_db()
        household = HouseholdProfile(
            name=name,
            description=description,
            currency=currency
        )
        db.add(household)
        db.commit()
        db.refresh(household)

        result = household.to_dict()
        db.close()
        return jsonify(result), 201

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/household/<int:household_id>', methods=['GET'])
def get_household(household_id):
    """
    GET /household/{id}
    Returns household info with members and summary stats.
    """
    try:
        db = get_db()
        household = db.query(HouseholdProfile).filter_by(id=household_id).first()

        if not household:
            db.close()
            return jsonify({'error': 'Household not found'}), 404

        # Get members
        members = db.query(HouseholdMember).filter_by(household_id=household_id).all()

        # Get expense summary
        expenses = db.query(HouseholdExpense).filter_by(household_id=household_id, excluded=False).all()
        total_expenses = sum(e.amount for e in expenses if e.amount < 0)
        total_income = sum(e.amount for e in expenses if e.amount > 0)

        result = household.to_dict()
        result['members'] = [m.to_dict() for m in members]
        result['summary'] = {
            'total_expenses': total_expenses,
            'total_income': total_income,
            'transaction_count': len(expenses),
        }

        db.close()
        return jsonify(result), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# Expenses
# ---------------------------------------------------------------------------

@app.route('/expenses/transactions', methods=['GET'])
def get_transactions():
    """
    GET /expenses/transactions?household_id=1&start_date=2026-01-01&end_date=2026-12-31&category=Food
    Returns list of transactions.
    """
    try:
        household_id = request.args.get('household_id', type=int)
        start_date = request.args.get('start_date')
        end_date = request.args.get('end_date')
        category = request.args.get('category')
        excluded = request.args.get('excluded', 'false').lower() == 'true'

        if not household_id:
            return jsonify({'error': 'household_id is required'}), 400

        db = get_db()
        query = db.query(HouseholdExpense).filter_by(household_id=household_id)

        if not excluded:
            query = query.filter_by(excluded=False)

        if start_date:
            query = query.filter(HouseholdExpense.date >= start_date)

        if end_date:
            query = query.filter(HouseholdExpense.date <= end_date)

        if category:
            query = query.filter_by(category=category)

        transactions = query.order_by(HouseholdExpense.date.desc()).all()
        result = [t.to_dict() for t in transactions]

        db.close()
        return jsonify(result), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/expenses/import', methods=['POST'])
def import_expenses():
    """
    POST /expenses/import
    Body: {
      "household_id": 1,
      "transactions": [
        {
          "date": "2026-04-27",
          "description": "Grocery store",
          "amount": -45.50,
          "category": "Food & Dining",
          "bank": "N26"
        },
        ...
      ]
    }
    Returns: { "imported": 5, "skipped": 2 }
    """
    try:
        data = request.get_json() or {}
        household_id = data.get('household_id')
        transactions = data.get('transactions', [])

        if not household_id:
            return jsonify({'error': 'household_id is required'}), 400

        db = get_db()

        # Verify household exists
        household = db.query(HouseholdProfile).filter_by(id=household_id).first()
        if not household:
            db.close()
            return jsonify({'error': 'Household not found'}), 404

        import hashlib

        imported = 0
        skipped = 0

        for t in transactions:
            # Generate hash to prevent duplicates
            hash_str = f"{household_id}_{t.get('date')}_{t.get('description')}_{t.get('amount')}"
            tx_hash = hashlib.md5(hash_str.encode()).hexdigest()

            # Check if already exists
            existing = db.query(HouseholdExpense).filter_by(hash=tx_hash).first()
            if existing:
                skipped += 1
                continue

            expense = HouseholdExpense(
                household_id=household_id,
                date=t.get('date'),
                description=t.get('description'),
                amount=t.get('amount'),
                category=t.get('category'),
                budget_category=t.get('budget_category'),
                bank=t.get('bank'),
                member_id=t.get('member_id'),
                hash=tx_hash,
            )
            db.add(expense)
            imported += 1

        db.commit()
        db.close()

        return jsonify({
            'imported': imported,
            'skipped': skipped,
        }), 201

    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# Backup & Restore
# ---------------------------------------------------------------------------


# ---------------------------------------------------------------------------
# AI Pipeline + Event Graph
# ---------------------------------------------------------------------------

@app.route('/pipeline/process_text', methods=['POST'])
def process_text_pipeline():
    """
    POST /pipeline/process_text
    Body: {
      "household_id": 1,
      "source": "scan|voice|email|manual",
      "raw_text": "School Annual Day on 3rd June at 3 PM"
    }
    """
    try:
        data = request.get_json() or {}
        household_id = data.get('household_id')
        source = data.get('source', 'manual')
        raw_text = data.get('raw_text', '').strip()

        if not household_id:
            return jsonify({'error': 'household_id is required'}), 400
        if not raw_text:
            return jsonify({'error': 'raw_text is required'}), 400

        db = get_db()
        household = db.query(HouseholdProfile).filter_by(id=household_id).first()
        if not household:
            db.close()
            return jsonify({'error': 'Household not found'}), 404

        response = _persist_pipeline_result(
            db=db,
            household_id=household_id,
            source=source,
            raw_text=raw_text,
        )
        db.close()
        return jsonify(response), 201

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/pipeline/process_scan_image', methods=['POST'])
def process_scan_image_pipeline():
    """
    POST /pipeline/process_scan_image
    Form fields:
      - household_id: int
      - source: optional, defaults to "scan"
      - file: uploaded image

    Reuses Grocery OCR backend (/ocr), then feeds extracted text into pipeline.
    """
    try:
        household_id_raw = request.form.get('household_id')
        source = request.form.get('source', 'scan')
        uploaded = request.files.get('file')

        if not household_id_raw:
            return jsonify({'error': 'household_id is required'}), 400
        if not uploaded:
            return jsonify({'error': 'file is required'}), 400

        try:
            household_id = int(household_id_raw)
        except ValueError:
            return jsonify({'error': 'household_id must be an integer'}), 400

        image_bytes = uploaded.read()
        if not image_bytes:
            return jsonify({'error': 'file is empty'}), 400

        db = get_db()
        household = db.query(HouseholdProfile).filter_by(id=household_id).first()
        if not household:
            db.close()
            return jsonify({'error': 'Household not found'}), 404

        ocr_result = _extract_text_via_existing_ocr(
            file_name=uploaded.filename or 'capture.jpg',
            content_type=uploaded.content_type or 'application/octet-stream',
            file_bytes=image_bytes,
        )

        raw_text = (ocr_result.get('text') or '').strip()
        if not raw_text:
            db.close()
            return jsonify({'error': 'No text extracted from image', 'ocr_result': ocr_result}), 422

        response = _persist_pipeline_result(
            db=db,
            household_id=household_id,
            source=source,
            raw_text=raw_text,
        )
        response['ocr'] = {
            'confidence': ocr_result.get('confidence'),
            'line_count': ocr_result.get('line_count'),
        }
        db.close()
        return jsonify(response), 201

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/timeline/today', methods=['GET'])
def timeline_today():
    """
    GET /timeline/today?household_id=1
    Returns today's event-like feed sorted by date/time.
    """
    try:
        household_id = request.args.get('household_id', type=int)
        if not household_id:
            return jsonify({'error': 'household_id is required'}), 400

        db = get_db()
        events = (
            db.query(HouseholdEvent)
            .filter_by(household_id=household_id)
            .order_by(HouseholdEvent.event_date.asc(), HouseholdEvent.event_time.asc())
            .limit(50)
            .all()
        )
        result = [e.to_dict() for e in events]
        db.close()

        return jsonify({'count': len(result), 'items': result}), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/automation/logs', methods=['GET'])
def automation_logs():
    """
    GET /automation/logs?household_id=1
    """
    try:
        household_id = request.args.get('household_id', type=int)
        if not household_id:
            return jsonify({'error': 'household_id is required'}), 400

        db = get_db()
        logs = (
            db.query(AutomationLog)
            .filter_by(household_id=household_id)
            .order_by(AutomationLog.created_at.desc())
            .limit(100)
            .all()
        )
        result = [l.to_dict() for l in logs]
        db.close()
        return jsonify({'count': len(result), 'items': result}), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/backup/export', methods=['POST'])
def export_backup():
    """
    POST /backup/export
    Body: { "household_id": 1, "backup_name": "backup_2026-04-27" }
    Creates local JSON backup of household data.
    Returns: { "backup_id": 1, "path": "...", "size": ... }
    """
    try:
        data = request.get_json() or {}
        household_id = data.get('household_id')
        backup_name = data.get('backup_name', f'backup_{datetime.utcnow().isoformat()}')

        if not household_id:
            return jsonify({'error': 'household_id is required'}), 400

        db = get_db()

        # Verify household exists
        household = db.query(HouseholdProfile).filter_by(id=household_id).first()
        if not household:
            db.close()
            return jsonify({'error': 'Household not found'}), 404

        # Collect all data
        members = db.query(HouseholdMember).filter_by(household_id=household_id).all()
        expenses = db.query(HouseholdExpense).filter_by(household_id=household_id).all()

        backup_data = {
            'household': household.to_dict(),
            'members': [m.to_dict() for m in members],
            'expenses': [e.to_dict() for e in expenses],
            'exported_at': datetime.utcnow().isoformat(),
        }

        # Write to file
        backups_dir = BACKEND_DIR / 'backups'
        backups_dir.mkdir(exist_ok=True)

        backup_file = backups_dir / f'{backup_name}.json'
        with open(backup_file, 'w') as f:
            json.dump(backup_data, f, indent=2)

        file_size = backup_file.stat().st_size

        # Record in database
        backup_record = HouseholdBackup(
            household_id=household_id,
            backup_name=backup_name,
            backup_path=str(backup_file),
            data_size=file_size,
        )
        db.add(backup_record)
        db.commit()
        db.refresh(backup_record)

        result = backup_record.to_dict()
        db.close()

        return jsonify(result), 201

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/backup/restore', methods=['POST'])
def restore_backup():
    """
    POST /backup/restore
    Body: { "household_id": 1, "backup_id": 1 }
    Restores household data from local JSON backup (overwrites current data).
    Returns: { "restored": true, "members": 3, "expenses": 150 }
    """
    try:
        data = request.get_json() or {}
        household_id = data.get('household_id')
        backup_id = data.get('backup_id')

        if not household_id or not backup_id:
            return jsonify({'error': 'household_id and backup_id are required'}), 400

        db = get_db()

        # Get backup record
        backup = db.query(HouseholdBackup).filter_by(id=backup_id, household_id=household_id).first()
        if not backup:
            db.close()
            return jsonify({'error': 'Backup not found'}), 404

        # Read backup file
        backup_path = Path(backup.backup_path)
        if not backup_path.exists():
            db.close()
            return jsonify({'error': 'Backup file not found on disk'}), 404

        with open(backup_path, 'r') as f:
            backup_data = json.load(f)

        # Delete existing members and expenses (household profile stays)
        db.query(HouseholdMember).filter_by(household_id=household_id).delete()
        db.query(HouseholdExpense).filter_by(household_id=household_id).delete()
        db.commit()

        # Restore members
        member_count = 0
        for m_data in backup_data.get('members', []):
            member = HouseholdMember(
                household_id=household_id,
                name=m_data['name'],
                role=m_data['role'],
                email=m_data.get('email'),
            )
            db.add(member)
            member_count += 1

        db.commit()

        # Get ID mapping for members (old_id -> new_id)
        new_members = db.query(HouseholdMember).filter_by(household_id=household_id).all()
        old_to_new_member_id = {}
        for old_data, new_member in zip(backup_data.get('members', []), new_members):
            old_to_new_member_id[old_data['id']] = new_member.id

        # Restore expenses
        expense_count = 0
        for e_data in backup_data.get('expenses', []):
            expense = HouseholdExpense(
                household_id=household_id,
                date=e_data['date'],
                description=e_data['description'],
                amount=e_data['amount'],
                category=e_data['category'],
                budget_category=e_data['budget_category'],
                bank=e_data.get('bank'),
                member_id=old_to_new_member_id.get(e_data.get('member_id')),
                hash=e_data.get('hash'),
                excluded=e_data['excluded'],
            )
            db.add(expense)
            expense_count += 1

        db.commit()
        db.close()

        return jsonify({
            'restored': True,
            'members': member_count,
            'expenses': expense_count,
        }), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ---------------------------------------------------------------------------
# CLI and Entry Point
# ---------------------------------------------------------------------------

# Register routes for meals and recipes
if __name__ == '__main__':
    import argparse

    parser = argparse.ArgumentParser(description='Household Platform Backend')
    parser.add_argument('--check', action='store_true', help='Syntax check and exit')
    parser.add_argument('--host', default='127.0.0.1', help='Host to bind to')
    parser.add_argument('--port', type=int, default=PORT, help='Port to bind to')
    parser.add_argument('--debug', action='store_true', help='Enable debug mode')

    args = parser.parse_args()

    if args.check:
        print('✓ Syntax check passed')
        print('✓ All imports successful')
        print('✓ Database models configured')
        sys.exit(0)

    print(f'Starting Household Platform Backend on {args.host}:{args.port}')
    app.run(host=args.host, port=args.port, debug=args.debug)
