"""
meal_routes.py
Meal synchronization endpoints for IndianMealPlanner ↔ Household Platform integration.

Endpoints:
  GET  /api/v1/meals/plans             → fetch remote meal updates
  POST /api/v1/meals/plans/batch-sync  → upload pending local changes
  POST /api/v1/meals/{id}/resolve-conflict
  POST /api/v1/meals/shopping-list/generate
  GET  /api/v1/meals/nutrition/household
"""

from datetime import datetime, timedelta
from flask import request, jsonify
from sqlalchemy import Column, Integer, String, Float, DateTime, Boolean, ForeignKey, Text
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()


class HouseholdMeal(Base):
    """Meal plan synced from IndianMealPlanner app."""
    __tablename__ = 'household_meals'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    member_id = Column(Integer, ForeignKey('household_members.id'), nullable=False)
    device_id = Column(String(255))  # UUID of device that created this

    # Meal data
    remote_id = Column(String(255))  # corresponds to local id on device
    time = Column(String(10), nullable=False)  # "08:00"
    meal = Column(String(255), nullable=False)
    portion = Column(String(255))
    category = Column(String(100), nullable=False)  # breakfast, lunch, snack, dinner, detox
    day_of_week = Column(Integer, nullable=False)  # 0=Monday, 6=Sunday
    is_detox_day = Column(Boolean, default=False)
    substitutes = Column(Text)  # comma-separated

    # Sync metadata
    synced_at = Column(DateTime, default=datetime.utcnow)
    last_modified_local = Column(DateTime, nullable=False)
    last_modified_remote = Column(DateTime, default=datetime.utcnow)
    conflict_status = Column(String(50))  # 'resolved', 'manual_review', 'local_only'

    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    household = relationship('HouseholdProfile', backref='meals')
    member = relationship('HouseholdMember', backref='meals')

    def to_dict(self):
        return {
            'id': self.id,
            'remoteId': self.remote_id,
            'time': self.time,
            'meal': self.meal,
            'portion': self.portion,
            'category': self.category,
            'dayOfWeek': self.day_of_week,
            'isDetoxDay': self.is_detox_day,
            'substitutes': self.substitutes.split(',') if self.substitutes else [],
            'syncedAt': self.synced_at.isoformat() if self.synced_at else None,
            'lastModifiedLocal': self.last_modified_local.isoformat(),
            'lastModifiedRemote': self.last_modified_remote.isoformat() if self.last_modified_remote else None,
            'conflictStatus': self.conflict_status,
            'deviceId': self.device_id,
            'createdAt': self.created_at.isoformat(),
            'updatedAt': self.updated_at.isoformat(),
        }


class HouseholdRecipe(Base):
    """Recipe shared among household members."""
    __tablename__ = 'household_recipes'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    member_id = Column(Integer, ForeignKey('household_members.id'), nullable=False)

    name = Column(String(255), nullable=False)
    description = Column(Text)
    ingredients = Column(Text)  # comma-separated or JSON
    instructions = Column(Text)
    servings = Column(Integer)
    calories_per_serving = Column(Float)
    protein_per_serving = Column(Float)
    carbs_per_serving = Column(Float)
    fat_per_serving = Column(Float)
    tags = Column(Text)  # comma-separated: breakfast, lunch, snack, detox, etc.
    category = Column(String(100))  # Indian, Continental, etc.
    is_shared = Column(Boolean, default=False)

    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    household = relationship('HouseholdProfile', backref='recipes')
    member = relationship('HouseholdMember', backref='recipes')

    def to_dict(self):
        return {
            'id': self.id,
            'name': self.name,
            'description': self.description,
            'ingredients': self.ingredients.split(',') if self.ingredients else [],
            'instructions': self.instructions,
            'servings': self.servings,
            'caloriesPerServing': self.calories_per_serving,
            'proteinPerServing': self.protein_per_serving,
            'carbsPerServing': self.carbs_per_serving,
            'fatPerServing': self.fat_per_serving,
            'tags': self.tags.split(',') if self.tags else [],
            'category': self.category,
            'isShared': self.is_shared,
            'createdAt': self.created_at.isoformat(),
            'updatedAt': self.updated_at.isoformat(),
        }


# Register routes (to be called from main.py)

def register_meal_routes(app, db_session_func):
    """Register meal sync routes with Flask app."""

    @app.route('/api/v1/meals/plans', methods=['GET'])
    def get_meals():
        """
        GET /api/v1/meals/plans?household_id=1&member_id=1&since=2024-04-20T10:30:00Z
        Fetch meal updates for a member since last sync.
        """
        try:
            household_id = request.args.get('household_id', type=int)
            member_id = request.args.get('member_id', type=int)
            since = request.args.get('since')  # ISO format datetime

            if not household_id or not member_id:
                return jsonify({'error': 'household_id and member_id required'}), 400

            db = db_session_func()
            query = db.query(HouseholdMeal).filter_by(
                household_id=household_id,
                member_id=member_id,
            )

            if since:
                since_dt = datetime.fromisoformat(since.replace('Z', '+00:00'))
                query = query.filter(HouseholdMeal.updated_at > since_dt)

            meals = query.order_by(HouseholdMeal.updated_at.desc()).all()
            db.close()

            return jsonify({
                'meals': [m.to_dict() for m in meals],
                'count': len(meals),
            }), 200

        except Exception as e:
            return jsonify({'error': str(e)}), 500

    @app.route('/api/v1/meals/plans/batch-sync', methods=['POST'])
    def batch_sync_meals():
        """
        POST /api/v1/meals/plans/batch-sync
        Upload pending local meal changes and detect conflicts.
        
        Request body:
        {
          "device_id": "uuid",
          "household_id": 1,
          "member_id": 1,
          "timestamp": "2024-04-20T10:35:00Z",
          "meals": [...]
        }
        """
        try:
            data = request.get_json() or {}
            device_id = data.get('device_id')
            household_id = data.get('household_id')
            member_id = data.get('member_id')
            meals_data = data.get('meals', [])
            sync_timestamp = data.get('timestamp', datetime.utcnow().isoformat())

            if not all([device_id, household_id, member_id]):
                return jsonify({'error': 'device_id, household_id, member_id required'}), 400

            db = db_session_func()
            synced_ids = []
            conflicts = []

            for meal_data in meals_data:
                remote_id = meal_data.get('remoteId')
                local_modified = meal_data.get('lastModifiedLocal')
                
                if remote_id:
                    # Existing meal, check for conflict
                    existing = db.query(HouseholdMeal).filter_by(
                        household_id=household_id,
                        member_id=member_id,
                        remote_id=remote_id,
                    ).first()

                    if existing:
                        # Last-write-wins: compare timestamps
                        local_ts = datetime.fromisoformat(local_modified.replace('Z', '+00:00')) if local_modified else datetime.utcnow()
                        remote_ts = existing.last_modified_remote or datetime.utcnow()

                        if local_ts > remote_ts:
                            # Local is newer, update remote
                            existing.meal = meal_data.get('meal', existing.meal)
                            existing.portion = meal_data.get('portion', existing.portion)
                            existing.category = meal_data.get('category', existing.category)
                            existing.last_modified_local = local_ts
                            existing.last_modified_remote = datetime.utcnow()
                            existing.conflict_status = 'resolved'
                            db.add(existing)
                            synced_ids.append(remote_id)
                        else:
                            # Remote is newer, conflict
                            conflicts.append({
                                'id': remote_id,
                                'local_version': local_modified,
                                'remote_version': existing.last_modified_remote.isoformat() if existing.last_modified_remote else None,
                                'conflict_reason': 'both_modified',
                                'suggestion': 'keep_remote',
                            })
                    else:
                        # New meal, insert
                        new_meal = HouseholdMeal(
                            household_id=household_id,
                            member_id=member_id,
                            device_id=device_id,
                            remote_id=remote_id,
                            time=meal_data.get('time'),
                            meal=meal_data.get('meal'),
                            portion=meal_data.get('portion'),
                            category=meal_data.get('category'),
                            day_of_week=meal_data.get('dayOfWeek'),
                            is_detox_day=meal_data.get('isDetoxDay', False),
                            substitutes=','.join(meal_data.get('substitutes', [])) if meal_data.get('substitutes') else None,
                            last_modified_local=datetime.fromisoformat(local_modified.replace('Z', '+00:00')) if local_modified else datetime.utcnow(),
                        )
                        db.add(new_meal)
                        synced_ids.append(remote_id)
                else:
                    # New meal without remoteId, insert and will return id
                    new_meal = HouseholdMeal(
                        household_id=household_id,
                        member_id=member_id,
                        device_id=device_id,
                        time=meal_data.get('time'),
                        meal=meal_data.get('meal'),
                        portion=meal_data.get('portion'),
                        category=meal_data.get('category'),
                        day_of_week=meal_data.get('dayOfWeek'),
                        is_detox_day=meal_data.get('isDetoxDay', False),
                        substitutes=','.join(meal_data.get('substitutes', [])) if meal_data.get('substitutes') else None,
                        last_modified_local=datetime.utcnow(),
                    )
                    db.add(new_meal)
                    db.flush()  # Get the ID
                    synced_ids.append(new_meal.id)

            db.commit()
            db.close()

            status = 200 if not conflicts else 409
            return jsonify({
                'synced_ids': synced_ids,
                'conflicts': conflicts,
                'errors': [],
                'timestamp': datetime.utcnow().isoformat(),
            }), status

        except Exception as e:
            return jsonify({'error': str(e)}), 500

    @app.route('/api/v1/meals/<int:meal_id>/resolve-conflict', methods=['POST'])
    def resolve_conflict(meal_id):
        """
        POST /api/v1/meals/{id}/resolve-conflict
        Resolve a conflict with user-selected strategy.
        
        Request body:
        {
          "strategy": "local" | "remote" | "merge",
          "timestamp": "2024-04-20T10:45:00Z"
        }
        """
        try:
            data = request.get_json() or {}
            strategy = data.get('strategy', 'remote')

            db = db_session_func()
            meal = db.query(HouseholdMeal).filter_by(id=meal_id).first()

            if not meal:
                db.close()
                return jsonify({'error': 'Meal not found'}), 404

            if strategy == 'local':
                meal.conflict_status = 'local_only'
            elif strategy == 'remote':
                meal.conflict_status = 'resolved'
            else:  # merge
                meal.conflict_status = 'resolved'

            db.add(meal)
            db.commit()
            db.close()

            return jsonify({
                'meal_id': meal_id,
                'conflict_status': meal.conflict_status,
                'applied_version': strategy,
                'timestamp': datetime.utcnow().isoformat(),
            }), 200

        except Exception as e:
            return jsonify({'error': str(e)}), 500

    @app.route('/api/v1/meals/shopping-list/generate', methods=['POST'])
    def generate_shopping_list():
        """
        POST /api/v1/meals/shopping-list/generate
        Generate aggregated shopping list from household members' meals.
        
        Request body:
        {
          "household_id": 1,
          "start_date": "2024-04-20",
          "end_date": "2024-04-27",
          "aggregate_by": "ingredient"
        }
        """
        try:
            data = request.get_json() or {}
            household_id = data.get('household_id')
            start_date = data.get('start_date')
            end_date = data.get('end_date')

            if not household_id:
                return jsonify({'error': 'household_id required'}), 400

            # TODO: Implement shopping list aggregation
            # For now, return placeholder
            return jsonify({
                'items': [],
                'total_items': 0,
                'period': f'{start_date} to {end_date}',
                'message': 'Shopping list aggregation not yet implemented',
            }), 200

        except Exception as e:
            return jsonify({'error': str(e)}), 500

    @app.route('/api/v1/meals/nutrition/household', methods=['GET'])
    def get_household_nutrition_summary():
        """
        GET /api/v1/meals/nutrition/household?household_id=1&start_date=2024-04-20&end_date=2024-04-27
        Get nutrition summary for household.
        """
        try:
            household_id = request.args.get('household_id', type=int)
            start_date = request.args.get('start_date')
            end_date = request.args.get('end_date')

            if not household_id:
                return jsonify({'error': 'household_id required'}), 400

            # TODO: Implement nutrition aggregation
            # For now, return placeholder
            return jsonify({
                'period': f'{start_date} to {end_date}',
                'household_summary': {
                    'total_calories': 0,
                    'avg_calories_per_day': 0,
                    'total_protein': 0,
                    'total_carbs': 0,
                    'total_fat': 0,
                    'member_count': 0,
                },
                'member_breakdown': [],
                'message': 'Nutrition aggregation not yet implemented',
            }), 200

        except Exception as e:
            return jsonify({'error': str(e)}), 500
