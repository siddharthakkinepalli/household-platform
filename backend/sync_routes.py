"""
sync_routes.py
Multi-device sync endpoints.

Endpoints:
  POST /api/v1/sync/register-device         → register a phone/device
  POST /api/v1/sync/push                    → device pushes local changes
  GET  /api/v1/sync/pull                    → device pulls changes from others
  POST /api/v1/sync/ack                     → device acks that it synced a change
"""

import json
from datetime import datetime, timedelta
from flask import request, jsonify
from sqlalchemy import and_

from sync_models import DeviceRegistration, SyncChangelog, SyncAck


def register_sync_routes(app, db_session_func):
    """Register sync-related routes with Flask app."""

    @app.route('/api/v1/sync/register-device', methods=['POST'])
    def register_device():
        """
        Register or update a device for a household.
        
        Request body:
        {
          "household_id": 3,
          "device_id": "device-uuid-1234",
          "device_name": "Alice's Phone",
          "device_type": "android"
        }
        """
        try:
            data = request.get_json()
            household_id = data.get('household_id')
            device_id = data.get('device_id')
            device_name = data.get('device_name', 'Unnamed Device')
            device_type = data.get('device_type', 'android')

            if not household_id or not device_id:
                return jsonify({'error': 'household_id and device_id required'}), 400

            db = db_session_func()
            try:
                # Check if device already registered
                dev = db.query(DeviceRegistration).filter(
                    and_(
                        DeviceRegistration.household_id == household_id,
                        DeviceRegistration.device_id == device_id
                    )
                ).first()

                if dev:
                    # Update last_seen and is_active
                    dev.last_seen = datetime.utcnow()
                    dev.is_active = True
                    dev.device_name = device_name
                    dev.device_type = device_type
                else:
                    # Create new registration
                    dev = DeviceRegistration(
                        household_id=household_id,
                        device_id=device_id,
                        device_name=device_name,
                        device_type=device_type,
                        last_seen=datetime.utcnow(),
                        is_active=True
                    )
                    db.add(dev)

                db.commit()
                return jsonify({
                    'status': 'ok',
                    'device_id': device_id,
                    'device_registered': dev.to_dict()
                }), 200
            finally:
                db.close()
        except Exception as e:
            return jsonify({'error': str(e)}), 500


    @app.route('/api/v1/sync/push', methods=['POST'])
    def push_changes():
        """
        Device pushes a batch of local changes to backend.
        
        Request body:
        {
          "household_id": 3,
          "device_id": "device-uuid-1234",
          "changes": [
            {
              "entity_type": "transaction",
              "entity_id": "tx-123",
              "operation": "update",
              "old_values": {...},
              "new_values": {...},
              "timestamp": "2026-05-06T10:30:00Z"
            },
            ...
          ]
        }
        
        Returns: list of changelog IDs that were recorded.
        """
        try:
            data = request.get_json()
            household_id = data.get('household_id')
            device_id = data.get('device_id')
            changes = data.get('changes', [])

            if not household_id or not device_id:
                return jsonify({'error': 'household_id and device_id required'}), 400

            db = db_session_func()
            try:
                # Register/update device
                dev = db.query(DeviceRegistration).filter(
                    and_(
                        DeviceRegistration.household_id == household_id,
                        DeviceRegistration.device_id == device_id
                    )
                ).first()

                if not dev:
                    dev = DeviceRegistration(
                        household_id=household_id,
                        device_id=device_id,
                        last_seen=datetime.utcnow(),
                        is_active=True
                    )
                    db.add(dev)
                else:
                    dev.last_seen = datetime.utcnow()

                # Record each change
                recorded_ids = []
                for change in changes:
                    log_entry = SyncChangelog(
                        household_id=household_id,
                        device_id=device_id,
                        entity_type=change.get('entity_type'),
                        entity_id=change.get('entity_id'),
                        operation=change.get('operation'),
                        old_values=change.get('old_values'),
                        new_values=change.get('new_values'),
                        timestamp=datetime.fromisoformat(change.get('timestamp', datetime.utcnow().isoformat()))
                    )
                    db.add(log_entry)
                    db.flush()  # get the ID
                    recorded_ids.append(log_entry.id)

                db.commit()
                return jsonify({
                    'status': 'ok',
                    'recorded_count': len(recorded_ids),
                    'changelog_ids': recorded_ids
                }), 200
            finally:
                db.close()
        except Exception as e:
            return jsonify({'error': str(e)}), 500


    @app.route('/api/v1/sync/pull', methods=['GET'])
    def pull_changes():
        """
        Device pulls changes from other devices in the same household.
        
        Query params:
          household_id: required
          device_id: required (requesting device, to avoid sending its own changes back)
          since: optional ISO timestamp (only get changes after this time)
          limit: optional, default 500
        
        Returns: list of changelog entries that other devices created.
        """
        try:
            household_id = request.args.get('household_id', type=int)
            device_id = request.args.get('device_id')
            since = request.args.get('since')
            limit = request.args.get('limit', 500, type=int)

            if not household_id or not device_id:
                return jsonify({'error': 'household_id and device_id required'}), 400

            db = db_session_func()
            try:
                query = db.query(SyncChangelog).filter(
                    SyncChangelog.household_id == household_id
                )

                # Exclude changes from the requesting device (avoid echo)
                query = query.filter(SyncChangelog.device_id != device_id)

                # Optionally filter by timestamp
                if since:
                    since_dt = datetime.fromisoformat(since)
                    query = query.filter(SyncChangelog.timestamp > since_dt)

                # Get latest changes first, limit results
                changes = query.order_by(SyncChangelog.timestamp.desc()).limit(limit).all()

                # Check which changes this device has already acked
                change_ids = [c.id for c in changes]
                acks = db.query(SyncAck).filter(
                    and_(
                        SyncAck.changelog_id.in_(change_ids),
                        SyncAck.device_id == device_id
                    )
                ).all()
                acked_ids = {ack.changelog_id for ack in acks}

                # Filter to only unacked changes
                unacked = [c for c in changes if c.id not in acked_ids]

                return jsonify({
                    'status': 'ok',
                    'changes': [c.to_dict() for c in unacked],
                    'count': len(unacked)
                }), 200
            finally:
                db.close()
        except Exception as e:
            return jsonify({'error': str(e)}), 500


    @app.route('/api/v1/sync/ack', methods=['POST'])
    def ack_changes():
        """
        Device acknowledges that it has synced/applied a set of changes.
        
        Request body:
        {
          "device_id": "device-uuid-1234",
          "changelog_ids": [1, 2, 3, ...]
        }
        """
        try:
            data = request.get_json()
            device_id = data.get('device_id')
            changelog_ids = data.get('changelog_ids', [])

            if not device_id or not changelog_ids:
                return jsonify({'error': 'device_id and changelog_ids required'}), 400

            db = db_session_func()
            try:
                for clog_id in changelog_ids:
                    # Check if ack already exists
                    existing = db.query(SyncAck).filter(
                        and_(
                            SyncAck.changelog_id == clog_id,
                            SyncAck.device_id == device_id
                        )
                    ).first()

                    if not existing:
                        ack = SyncAck(
                            changelog_id=clog_id,
                            device_id=device_id,
                            synced_at=datetime.utcnow()
                        )
                        db.add(ack)

                db.commit()
                return jsonify({
                    'status': 'ok',
                    'acked_count': len(changelog_ids)
                }), 200
            finally:
                db.close()
        except Exception as e:
            return jsonify({'error': str(e)}), 500
