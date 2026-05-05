"""Build and upload household-platform Android AAB to Google Play Internal testing.

Usage (from household-platform root):
  C:/Projects/.venv/Scripts/python.exe scripts/publish_play_internal.py
  publish_internal.bat
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SECRETS_FILE = ROOT / "release-secrets.properties"
GRADLE_FILE = ROOT / "android" / "build.gradle.kts"
AAB_PATH = ROOT / "android" / "build" / "outputs" / "bundle" / "release" / "android-release.aab"


def read_properties(path: Path) -> dict[str, str]:
    data: dict[str, str] = {}
    if not path.exists():
        return data
    for line in path.read_text(encoding="utf-8").splitlines():
        raw = line.strip()
        if not raw or raw.startswith("#") or "=" not in raw:
            continue
        key, value = raw.split("=", 1)
        data[key.strip()] = value.strip()
    return data


def run(cmd: list[str], cwd: Path | None = None) -> str:
    print("$", " ".join(cmd))
    proc = subprocess.run(
        cmd,
        cwd=str(cwd) if cwd else None,
        check=False,
        capture_output=True,
        text=True,
    )
    if proc.stdout:
        print(proc.stdout, end="")
    if proc.stderr:
        print(proc.stderr, end="", file=sys.stderr)

    if proc.returncode == 0:
        return proc.stdout or ""

    cmd_text = " ".join(cmd).lower()
    if "gradlew" in cmd_text and "build successful" in (proc.stdout or "").lower():
        print("Warning: Gradle returned non-zero code but reported BUILD SUCCESSFUL; continuing.")
        return proc.stdout or ""

    raise subprocess.CalledProcessError(proc.returncode, cmd)


def read_versions() -> tuple[int, str]:
    text = GRADLE_FILE.read_text(encoding="utf-8")
    vc_match = re.search(r"versionCode\s*=\s*(\d+)", text)
    vn_match = re.search(r"versionName\s*=\s*\"([^\"]+)\"", text)
    if not vc_match or not vn_match:
        raise RuntimeError("Could not parse versionCode/versionName from android/build.gradle.kts")
    return int(vc_match.group(1)), vn_match.group(1)


def bump_version_name(version_name: str, mode: str) -> str:
    parts = version_name.split(".")
    while len(parts) < 3:
        parts.append("0")
    major, minor, patch = (int(parts[0]), int(parts[1]), int(parts[2]))

    if mode == "major":
        major += 1
        minor = 0
        patch = 0
    elif mode == "minor":
        minor += 1
        patch = 0
    else:
        patch += 1

    return f"{major}.{minor}.{patch}"


def write_versions(version_code: int, version_name: str) -> None:
    text = GRADLE_FILE.read_text(encoding="utf-8")
    text = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {version_code}", text, count=1)
    text = re.sub(r"versionName\s*=\s*\"[^\"]+\"", f"versionName = \"{version_name}\"", text, count=1)
    GRADLE_FILE.write_text(text, encoding="utf-8")


def build_bundle() -> None:
    gradlew = ROOT / "gradlew.bat"
    run([str(gradlew), ":android:bundleRelease"], cwd=ROOT)
    if not AAB_PATH.exists():
        raise FileNotFoundError(f"AAB not found at {AAB_PATH}")


def upload_to_play(package_name: str, track: str, release_name: str, notes: str, sa_json_path: Path) -> str:
    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
        from googleapiclient.http import MediaFileUpload
    except ImportError as exc:
        raise RuntimeError(
            "Missing Google Play API dependencies. Install with:\n"
            "  C:/Projects/.venv/Scripts/python.exe -m pip install google-api-python-client google-auth"
        ) from exc

    if not sa_json_path.exists():
        raise FileNotFoundError(f"Service account JSON not found: {sa_json_path}")

    scopes = ["https://www.googleapis.com/auth/androidpublisher"]
    creds = service_account.Credentials.from_service_account_file(str(sa_json_path), scopes=scopes)
    service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)

    def run_edit_with_status(status: str) -> str:
        edit = service.edits().insert(packageName=package_name, body={}).execute()
        edit_id = edit["id"]

        media = MediaFileUpload(str(AAB_PATH), mimetype="application/octet-stream", resumable=False)
        bundle = service.edits().bundles().upload(packageName=package_name, editId=edit_id, media_body=media).execute()
        version_code = str(bundle["versionCode"])

        release = {
            "name": release_name,
            "status": status,
            "versionCodes": [version_code],
            "releaseNotes": [{"language": "en-US", "text": notes}],
        }

        service.edits().tracks().update(
            packageName=package_name,
            editId=edit_id,
            track=track,
            body={"releases": [release]},
        ).execute()

        service.edits().commit(packageName=package_name, editId=edit_id).execute()
        return version_code

    try:
        return run_edit_with_status("completed")
    except Exception as exc:
        if "Only releases with status draft may be created on draft app" not in str(exc):
            raise
        print("Play app is in draft mode; retrying with release status 'draft'.")
        return run_edit_with_status("draft")


def main() -> int:
    parser = argparse.ArgumentParser(description="Bump version, build AAB, and upload to Play Internal testing.")
    parser.add_argument("--track", default=None, help="Play track (default from properties, else internal)")
    parser.add_argument("--bump", choices=["patch", "minor", "major", "none"], default="patch")
    parser.add_argument("--notes", default="Automated internal release")
    args = parser.parse_args()

    props = read_properties(SECRETS_FILE)
    if not props:
        print(f"Missing {SECRETS_FILE}")
        return 2

    package_name = props.get("PLAY_PACKAGE_NAME", "com.jugaad.home")
    track = args.track or props.get("PLAY_TRACK", "internal")
    sa_json = props.get("PLAY_SERVICE_ACCOUNT_JSON", "")
    if not sa_json:
        print("Set PLAY_SERVICE_ACCOUNT_JSON in release-secrets.properties")
        return 2

    version_code, version_name = read_versions()
    if args.bump != "none":
        version_code += 1
        version_name = bump_version_name(version_name, args.bump)
        write_versions(version_code, version_name)
        print(f"Bumped to versionCode={version_code}, versionName={version_name}")

    build_bundle()

    release_name = f"{version_name} ({version_code})"
    sa_json_path = Path(sa_json)
    if not sa_json_path.is_absolute():
        sa_json_path = (ROOT / sa_json).resolve()

    uploaded_version_code = upload_to_play(
        package_name=package_name,
        track=track,
        release_name=release_name,
        notes=args.notes,
        sa_json_path=sa_json_path,
    )
    print(f"Uploaded versionCode {uploaded_version_code} to track '{track}'.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as e:
        print(f"Command failed with exit code {e.returncode}")
        raise SystemExit(e.returncode)
    except Exception as e:
        print(f"Error: {e}")
        raise SystemExit(1)
