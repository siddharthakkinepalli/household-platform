# Isolated UI Workflow Testing Proposal

**Target Module:** `:core:document-ai`  
**Compliance Level:** 100% (No modifications to `:android`, no DB changes, no `android.*` imports in JVM tests)

## 1. Strategy: Isolated Host Testing
Verify library workflows on actual ARM64 hardware using the library's own `androidTest` source set. This validates NDK/JNI stability on the device without touching the main app code.

## 2. Components to Implement

### A. Test Infrastructure (In `core:document-ai`)
- **`HiltTestRunner.kt`**: Custom runner for device-side dependency injection.
- **`TestActivity.kt`**: A headless host activity for rendering library Compose components.
- **`FakeLlamaEngine.kt`**: A simulated engine for deterministic UI testing without 3GB model overhead.

### B. Verification Workflows
- **Workflow: Scan to UI**
  - Input: Raw OCR string.
  - Action: Trigger `LlmResponseParser` and `DocumentInferenceModel`.
  - Verification: Verify fields (Merchant, Date, etc.) appear in the Compose nodes on the phone.
- **Workflow: Native Resilience**
  - Action: Simulate a native JNI crash/exception.
  - Verification: Ensure the UI shows the "EMPTY" fallback state instead of a process crash.
- **Workflow: Progress UI**
  - Action: Feed progress updates from `ModelDownloadManager`.
  - Verification: Assert the progress bar state on the phone matches the download logic.

## 3. Execution
Run via Gradle:
```bash
./gradlew :core:document-ai:connectedDebugAndroidTest
```

---
*Note: This file was saved to the project root to comply with the "DO NOT touch any file under android/" hard rule.*
