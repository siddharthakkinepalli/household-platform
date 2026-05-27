# Household Platform — Commands Reference

All commands run from the project root: `C:\Projects\household-platform\`

---

## Build

Build a debug APK only (no install):

```powershell
.\gradlew.bat :android:assembleDebug
```

APK output: `android\build\outputs\apk\debug\android-arm64-v8a-debug.apk`

---

## Build + Install via USB

Builds the debug APK and installs it directly on any connected USB device.
Uses `build.bat` — handles build, device detection, and `adb install -r` automatically.

```bat
build.bat
```

**Source:** [`build.bat`](build.bat)

What it does:
1. Deletes old APK if present
2. Runs `gradlew :android:assembleDebug`
3. Detects connected ADB device
4. Runs `adb install -r` on the built APK
5. Prints `[OK] Done.` or `[INFO] No device connected.`

> **Prerequisite:** USB debugging enabled on phone. Phone must appear in `adb devices` as `device` (not `unauthorized`).

Manual ADB install (if bat fails):

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "android\build\outputs\apk\debug\android-arm64-v8a-debug.apk"
```

---

## Publish to Google Play (Internal Track)

Bumps the patch version, builds a signed release AAB, and uploads to the **Internal** track as a draft. Promotion to Alpha / Beta / Production is done manually in Play Console.

```bat
publish_internal.bat
```

**Source:** [`publish_internal.bat`](publish_internal.bat) → [`scripts/publish_play_internal.py`](scripts/publish_play_internal.py)

Options (passed through to the Python script):

```bat
publish_internal.bat --bump patch          # default — 1.0.3 → 1.0.4
publish_internal.bat --bump minor          # 1.0.3 → 1.1.0
publish_internal.bat --bump major          # 1.0.3 → 2.0.0
publish_internal.bat --bump none           # keep current version
publish_internal.bat --track alpha         # upload to alpha instead of internal
publish_internal.bat --notes "Fix salary detection"
```

> **Prerequisites:**
> - `release-secrets.properties` present at project root (keystore path + passwords + Play service account JSON path)
> - Python venv at `C:\Projects\.venv` with `google-api-python-client` installed
> - Release keystore configured in `android\build.gradle.kts`

---

## Google Drive Sign-In — SHA-1 Registration

GCP requires the signing certificate SHA-1 registered for each build type.
Go to: **GCP Console → APIs & Services → Credentials → Android OAuth client**
Package name: `com.jugaad.home`

### Release key (Play Store / publish_internal.bat)

```
SHA1: 45:91:39:FC:F2:6C:73:00:71:14:C4:0C:C2:49:56:28:67:9A:A9:10
```

Valid until: 17 Feb 2055 — 2048-bit RSA, SHA256withRSA

### Debug key (USB sideload via build.bat)

Debug APKs fail with `DEVELOPER_ERROR` unless the debug keystore SHA-1 is also registered.
Run this to get it:

```powershell
keytool -list -v `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey `
  -storepass android `
  -keypass android
```

Add the `SHA1:` line from the output as a second entry in the same GCP OAuth client.

| Build type | SHA-1 source | Drive sign-in |
|------------|-------------|---------------|
| Release (Play) | Release key above | ✅ works once registered |
| Debug (USB) | keytool output above | ✅ works once registered |

---

## Smoke Tests (connected phone)

```bat
android\system-check\run_phone_smoke.bat
```

**Source:** [`android/system-check/run_phone_smoke.bat`](android/system-check/run_phone_smoke.bat)

---

## Wireless Install (Wi-Fi — recommended)

Device only needs to be **paired once**. After that, just connect each session.

**Step 1 — Get the IP:port from phone (changes every session):**
Settings → Developer Options → Wireless Debugging → note the "IP address & Port" shown

**Step 2 — Connect:**

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb connect 192.168.x.x:PORT        # use IP:port from phone screen
& $adb devices                          # should show: 192.168.x.x:PORT   device
```

**Step 3 — Build and install as normal:**

```bat
build.bat
```

`build.bat` detects any connected device (USB or wireless) — no changes needed.

> Connection drops when Wireless Debugging is toggled off or phone restarts.
> Re-run Step 2 to reconnect. No need to pair again.

---

## Check Connected Devices

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

Expected output when ready: `<serial>    device`
If it shows `unauthorized` — tap "Allow USB debugging" on the phone.

---

## Useful Gradle Tasks

```powershell
# Clean build cache
.\gradlew.bat clean

# Run unit tests
.\gradlew.bat :android:testDebugUnitTest

# Check for dependency updates
.\gradlew.bat dependencyUpdates

# See all available tasks
.\gradlew.bat :android:tasks
```
