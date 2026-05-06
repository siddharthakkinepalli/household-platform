"""
sync_models.py
SQLAlchemy models for multi-device data sync.

Tables:
  - device_registrations   : track devices per household
  - sync_changelog         : log of all changes from all devices
  - sync_acks              : acknowledge which devices have synced which changes
"""

from datetime import datetime
from sqlalchemy import Column, Integer, String, DateTime, Text, Boolean, ForeignKey, JSON
from sqlalchemy.orm import relationship
from household_models import Base


class DeviceRegistration(Base):
    """Represents a device (phone) connected to a household."""
    __tablename__ = 'device_registrations'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    device_id = Column(String(255), nullable=False, unique=True)  # UUID, stable per phone
    device_name = Column(String(255))  # "Alice's iPhone", "Bob's Pixel"
    device_type = Column(String(50))  # "android", "ios"
    last_seen = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'device_id': self.device_id,
            'device_name': self.device_name,
            'device_type': self.device_type,
            'last_seen': self.last_seen.isoformat() if self.last_seen else None,
            'is_active': self.is_active,
            'created_at': self.created_at.isoformat() if self.created_at else None,
        }


class SyncChangelog(Base):
    """Master log of all changes that need to be synced across devices."""
    __tablename__ = 'sync_changelog'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    device_id = Column(String(255), nullable=False)  # device that made the change
    
    # Change metadata
    entity_type = Column(String(50), nullable=False)  # "transaction", "exclusion", "override", etc.
    entity_id = Column(String(255), nullable=False)  # ID of the changed entity
    operation = Column(String(20), nullable=False)  # "create", "update", "delete"
    
    # Change payload
    old_values = Column(JSON)  # before state (for updates/deletes)
    new_values = Column(JSON)  # after state (for creates/updates)
    
    # Sync tracking
    timestamp = Column(DateTime, default=datetime.utcnow, index=True)
    version = Column(Integer, default=1)  # for conflict resolution (last-write-wins)
    
    created_at = Column(DateTime, default=datetime.utcnow)

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'device_id': self.device_id,
            'entity_type': self.entity_type,
            'entity_id': self.entity_id,
            'operation': self.operation,
            'old_values': self.old_values,
            'new_values': self.new_values,
            'timestamp': self.timestamp.isoformat() if self.timestamp else None,
            'version': self.version,
            'created_at': self.created_at.isoformat() if self.created_at else None,
        }


class SyncAck(Base):
    """Track which devices have synced which changes."""
    __tablename__ = 'sync_acks'

    id = Column(Integer, primary_key=True, autoincrement=True)
    changelog_id = Column(Integer, ForeignKey('sync_changelog.id'), nullable=False)
    device_id = Column(String(255), nullable=False)  # device that received/acked the change
    synced_at = Column(DateTime, default=datetime.utcnow)

    def to_dict(self):
        return {
            'id': self.id,
            'changelog_id': self.changelog_id,
            'device_id': self.device_id,
            'synced_at': self.synced_at.isoformat() if self.synced_at else None,
        }
