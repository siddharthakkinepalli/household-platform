"""
ai_pipeline.py
Practical, local-first AI pipeline scaffold for Household FOS.

Stages:
1. capture metadata intake (source + raw_text)
2. extraction (rule-first, LLM-ready contract)
3. normalization (date/time/category cleanup)
4. event graph builder (linked actions)
5. automation rules (alerts/tasks)
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import re
from typing import Any


@dataclass
class ExtractionResult:
    title: str
    date: str | None
    time: str | None
    category: str
    confidence: float


def extract_structured_data(raw_text: str) -> ExtractionResult:
    """Rule-first extraction with stable JSON-like output contract."""
    txt = (raw_text or "").strip()
    lowered = txt.lower()

    category = "event"
    if any(k in lowered for k in ["rent", "bill", "paid", "payment", "invoice"]):
        category = "expense"
    if any(k in lowered for k in ["renewal", "expires", "expiry", "passport"]):
        category = "reminder"

    # naive title extraction: use first sentence/chunk
    title = txt.split("\n")[0].strip()
    if len(title) > 90:
        title = title[:90].rstrip() + "..."

    # date extraction patterns (e.g. 3rd June 2026, 03/06/2026)
    date_iso = _extract_date_iso(txt)
    time_24h = _extract_time_24h(txt)

    confidence = 0.45
    if date_iso:
        confidence += 0.2
    if time_24h:
        confidence += 0.15
    if title:
        confidence += 0.2

    return ExtractionResult(
        title=title or "Untitled Item",
        date=date_iso,
        time=time_24h,
        category=category,
        confidence=min(confidence, 0.99),
    )


def normalize_payload(extracted: ExtractionResult, source: str) -> dict[str, Any]:
    """Normalize to canonical event schema used by DB and automation."""
    normalized = {
        "title": extracted.title,
        "event_date": extracted.date,
        "event_time": extracted.time,
        "event_type": extracted.category,
        "source": source,
        "confidence": extracted.confidence,
        "normalized_at": datetime.utcnow().isoformat(),
    }
    return normalized


def build_event_graph(normalized: dict[str, Any]) -> dict[str, Any]:
    """Generate linked actions for the Smart Event Graph."""
    event_type = normalized.get("event_type", "event")
    title = normalized.get("title", "")

    linked_actions: list[str] = []

    if event_type == "event":
        linked_actions.extend(
            [
                "Set reminder 1 day before",
                "Set reminder 2 hours before",
                "Add travel buffer task",
            ]
        )
        if "school" in title.lower():
            linked_actions.append("Notify parent role members")
            linked_actions.append("Suggest outfit prep checklist")

    if event_type == "expense":
        linked_actions.extend(
            [
                "Track in monthly budget",
                "If recurring, create due reminder",
            ]
        )

    if event_type == "reminder":
        linked_actions.extend(
            [
                "Create D-7, D-3, D-1 reminders",
                "Link to family timeline",
            ]
        )

    return {
        "event": normalized,
        "linked_actions": linked_actions,
    }


def apply_automation_rules(graph: dict[str, Any]) -> list[dict[str, Any]]:
    """Return concrete automation actions to execute by app services."""
    event = graph.get("event", {})
    event_type = event.get("event_type", "event")

    actions: list[dict[str, Any]] = []

    if event_type == "event":
        actions.append({"action": "create_reminder", "offset": "-1d"})
        actions.append({"action": "create_reminder", "offset": "-2h"})
    elif event_type == "expense":
        actions.append({"action": "budget_check", "mode": "threshold"})
    elif event_type == "reminder":
        actions.append({"action": "create_reminder", "offset": "-7d"})
        actions.append({"action": "create_reminder", "offset": "-3d"})
        actions.append({"action": "create_reminder", "offset": "-1d"})

    return actions


def _extract_time_24h(text: str) -> str | None:
    m = re.search(r"\b([01]?\d|2[0-3])[:.]([0-5]\d)\b", text)
    if m:
        return f"{int(m.group(1)):02d}:{m.group(2)}"

    m = re.search(r"\b(\d{1,2})\s?(am|pm)\b", text, flags=re.IGNORECASE)
    if m:
        h = int(m.group(1))
        ampm = m.group(2).lower()
        if ampm == "pm" and h != 12:
            h += 12
        if ampm == "am" and h == 12:
            h = 0
        return f"{h:02d}:00"

    return None


def _extract_date_iso(text: str) -> str | None:
    # dd/mm/yyyy
    m = re.search(r"\b(\d{1,2})/(\d{1,2})/(\d{4})\b", text)
    if m:
        d, mo, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
        return f"{y:04d}-{mo:02d}-{d:02d}"

    # 3rd June 2026
    m = re.search(
        r"\b(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]+)\s+(\d{4})\b",
        text,
        flags=re.IGNORECASE,
    )
    if m:
        d = int(m.group(1))
        mo_name = m.group(2).lower()
        y = int(m.group(3))
        months = {
            "january": 1,
            "february": 2,
            "march": 3,
            "april": 4,
            "may": 5,
            "june": 6,
            "july": 7,
            "august": 8,
            "september": 9,
            "october": 10,
            "november": 11,
            "december": 12,
        }
        mo = months.get(mo_name)
        if mo:
            return f"{y:04d}-{mo:02d}-{d:02d}"

    return None
