"""
backup_routes.py
Local backup and restore utilities for household data.

Provides functions to:
- Export household data to JSON backup files
- Restore household data from JSON backup files
- List available backups
- Cleanup old backups
"""

import json
import shutil
from pathlib import Path
from datetime import datetime, timedelta
from typing import Dict, List, Optional


class BackupManager:
    """Manages local JSON backups for households."""

    def __init__(self, backups_dir: Path):
        """
        Initialize backup manager.

        Args:
            backups_dir: Directory where backup files are stored
        """
        self.backups_dir = Path(backups_dir)
        self.backups_dir.mkdir(parents=True, exist_ok=True)

    def export_to_json(self, household_data: Dict, backup_name: str) -> Dict:
        """
        Export household data to JSON backup file.

        Args:
            household_data: Dict with keys: household, members, expenses
            backup_name: Name for the backup (without extension)

        Returns:
            Dict with: backup_path, file_size, created_at
        """
        backup_data = {
            'metadata': {
                'created_at': datetime.utcnow().isoformat(),
                'backup_version': '1.0',
            },
            'data': household_data,
        }

        backup_file = self.backups_dir / f'{backup_name}.json'
        with open(backup_file, 'w') as f:
            json.dump(backup_data, f, indent=2)

        file_size = backup_file.stat().st_size

        return {
            'backup_path': str(backup_file),
            'file_size': file_size,
            'created_at': datetime.utcnow().isoformat(),
        }

    def restore_from_json(self, backup_file: Path) -> Dict:
        """
        Load household data from JSON backup file.

        Args:
            backup_file: Path to backup JSON file

        Returns:
            Dict with: household, members, expenses
        """
        if not backup_file.exists():
            raise FileNotFoundError(f"Backup file not found: {backup_file}")

        with open(backup_file, 'r') as f:
            backup_data = json.load(f)

        return backup_data.get('data', {})

    def list_backups(self, household_id: Optional[int] = None) -> List[Dict]:
        """
        List all available backups, optionally filtered by household_id.

        Args:
            household_id: Optional household ID to filter

        Returns:
            List of backup metadata dicts
        """
        backups = []

        for backup_file in self.backups_dir.glob('*.json'):
            try:
                with open(backup_file, 'r') as f:
                    backup_data = json.load(f)

                metadata = backup_data.get('metadata', {})
                data = backup_data.get('data', {})

                if household_id:
                    hh_id = data.get('household', {}).get('id')
                    if hh_id != household_id:
                        continue

                backups.append({
                    'filename': backup_file.name,
                    'path': str(backup_file),
                    'size': backup_file.stat().st_size,
                    'created_at': metadata.get('created_at'),
                    'household_id': data.get('household', {}).get('id'),
                    'household_name': data.get('household', {}).get('name'),
                    'member_count': len(data.get('members', [])),
                    'expense_count': len(data.get('expenses', [])),
                })
            except Exception as e:
                print(f"Error reading backup {backup_file}: {e}")

        # Sort by created_at descending (newest first)
        backups.sort(key=lambda x: x['created_at'], reverse=True)
        return backups

    def cleanup_old_backups(self, days_keep: int = 30) -> int:
        """
        Delete backup files older than specified days.

        Args:
            days_keep: Number of days to keep backups

        Returns:
            Number of files deleted
        """
        cutoff = datetime.utcnow() - timedelta(days=days_keep)
        deleted_count = 0

        for backup_file in self.backups_dir.glob('*.json'):
            try:
                file_mtime = datetime.utcfromtimestamp(backup_file.stat().st_mtime)
                if file_mtime < cutoff:
                    backup_file.unlink()
                    deleted_count += 1
            except Exception as e:
                print(f"Error deleting backup {backup_file}: {e}")

        return deleted_count

    def duplicate_backup(self, source_backup_file: Path, new_backup_name: str) -> Dict:
        """
        Create a copy of an existing backup.

        Args:
            source_backup_file: Path to source backup file
            new_backup_name: Name for the new copy (without extension)

        Returns:
            Dict with metadata of new backup
        """
        if not source_backup_file.exists():
            raise FileNotFoundError(f"Source backup not found: {source_backup_file}")

        new_backup_file = self.backups_dir / f'{new_backup_name}.json'
        shutil.copy2(source_backup_file, new_backup_file)

        file_size = new_backup_file.stat().st_size

        return {
            'backup_path': str(new_backup_file),
            'file_size': file_size,
            'created_at': datetime.utcnow().isoformat(),
        }

    def delete_backup(self, backup_file: Path) -> bool:
        """
        Delete a specific backup file.

        Args:
            backup_file: Path to backup file

        Returns:
            True if successful
        """
        if not backup_file.exists():
            raise FileNotFoundError(f"Backup file not found: {backup_file}")

        backup_file.unlink()
        return True
