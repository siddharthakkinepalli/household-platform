"""
Receipt Parser Routes — Flask endpoints for receipt processing.
"""

import os
from flask import Flask, request, jsonify

from .service import get_receipt_service


def register_receipt_routes(app: Flask):
    """Register receipt parser routes on Flask app."""

    @app.route('/receipt/parse', methods=['POST'])
    def parse_receipt():
        """
        POST /receipt/parse

        Mode: local (default, free) or anthropic/openai (AI-enhanced)
        Body (JSON):
        {
            "text": "raw ocr text...",
            "mode": "local",  // or "anthropic", "openai"
            "api_key": "sk-..."  // required for AI modes
        }

        OR form-data with file upload:
        {
            "file": <image>,
            "mode": "local",
            "api_key": "sk-..."
        }

        Response:
        {
            "success": true,
            "receipt": { ... },
            "parser": "aldi",
            "confidence": 0.85,
            "mode": "local"
        }
        """
        mode = request.json.get('mode', 'local') if request.is_json else request.form.get('mode', 'local')
        api_key = None

        # Check for API key in various places
        if request.is_json:
            api_key = request.json.get('api_key')
            ocr_text = request.json.get('text')
            image_file = None
        else:
            api_key = request.form.get('api_key') or os.getenv('ANTHROPIC_API_KEY') or os.getenv('OPENAI_API_KEY')
            ocr_text = request.form.get('text')
            image_file = request.files.get('file')

        # Validate mode
        if mode not in ('local', 'anthropic', 'openai'):
            return jsonify({'error': 'mode must be local, anthropic, or openai'}), 400

        if mode in ('anthropic', 'openai') and not api_key:
            return jsonify({'error': f'api_key required for {mode} mode'}), 400

        # Get service and process
        service = get_receipt_service()

        if image_file:
            image_bytes = image_file.read()
            result = service.process(image_bytes=image_bytes, mode=mode, api_key=api_key)
        elif ocr_text:
            result = service.process(ocr_text=ocr_text, mode=mode, api_key=api_key)
        else:
            return jsonify({'error': 'Provide either text or file'}), 400

        return jsonify(result.to_dict()), 200 if result.success else 422

    @app.route('/receipt/ocr', methods=['POST'])
    def ocr_only():
        """
        POST /receipt/ocr

        Extract text from image without parsing.
        Body: form-data with "file" (image)
        """
        image_file = request.files.get('file')
        if not image_file:
            return jsonify({'error': 'file required'}), 400

        service = get_receipt_service()
        lines = service.process_image(image_file.read())

        return jsonify({
            'success': True,
            'lines': [
                {
                    'line_number': ln.line_number,
                    'text': ln.raw_text,
                    'confidence': ln.avg_confidence,
                }
                for ln in lines
            ],
            'text': '\n'.join(ln.raw_text for ln in lines),
        }), 200

    @app.route('/receipt/parse/text', methods=['POST'])
    def parse_text_only():
        """
        POST /receipt/parse/text

        Parse pre-extracted OCR text (no image processing).
        Body (JSON):
        {
            "text": "ALDI SÜD\n2026-05-13\nMilch  1,99\nBrot   2,49",
            "mode": "local"
        }
        """
        if not request.is_json:
            return jsonify({'error': 'JSON required'}), 400

        data = request.json
        ocr_text = data.get('text', '')
        mode = data.get('mode', 'local')
        api_key = data.get('api_key')

        if not ocr_text:
            return jsonify({'error': 'text required'}), 400

        service = get_receipt_service()
        result = service.process(ocr_text=ocr_text, mode=mode, api_key=api_key)

        return jsonify(result.to_dict()), 200 if result.success else 422

    @app.route('/receipt/modes', methods=['GET'])
    def get_modes():
        """
        GET /receipt/modes

        Returns available parsing modes.
        """
        return jsonify({
            'modes': [
                {
                    'name': 'local',
                    'description': 'Rule-based parsing (free, offline)',
                    'requires_api_key': False,
                },
                {
                    'name': 'anthropic',
                    'description': 'Rule-based + Claude AI correction',
                    'requires_api_key': True,
                },
                {
                    'name': 'openai',
                    'description': 'Rule-based + GPT AI correction',
                    'requires_api_key': True,
                },
            ]
        }), 200