# JUGAAD System Rules: feature:astro Configuration

**Last updated:** 2026-06-01  
**Phase 1 status:** ✅ Committed — security foundation, module structure, widget receivers, profile/rule engine scaffolding

## Architectural Mandates
- **Zero Cloud Dependencies:** All calculations and text transformations must execute 100% offline.
- **Background Execution Rules:** Never run local SLM/LLM text inference within an asynchronous WorkManager task. All generative text must be compiled once at midnight and cached in SQLite.
- **Time API:** Use java.time APIs exclusively. Timestamps must be stored internally in UTC Epoch Milliseconds.

## Astronomical Core Rules
- **Calculation Core:** Swiss Ephemeris (libswe) via NDK/JNI running on a thread-confined EphemerisDispatcher.
- **Zodiac & House:** Sidereal Lahiri ayanamsha, Whole Sign Houses, True Node (True Rahu/Ketu).
- **Refraction:** h = -0.8333° - (0.0347° * sqrt(elevation_in_meters)).
- **Panhcang:** Drik Panchang standard. Resolve Graha Yuddha by penalizing the lower declination planet by 50% of its Shadbala score.

## Security & Performance Budgets
- **Storage:** Mandatory SQLCipher encryption via Android Keystore.
- **RAM Limits:** Under 64MB for background workers, 512MB foreground limit for the ONNX Runtime engine.