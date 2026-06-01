#include "sweph_bridge.h"
#include "swephexp.h"

#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <mutex>
#include <string>

#define LOG_TAG "SwephBridge"
// Structured log — event codes only, no coordinate or date values
#define LOGI(code) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, "[SWEPH] %s", code)
#define LOGE(code) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "[SWEPH_ERR] %s", code)

// Single global mutex: Swiss Ephemeris is not thread-safe internally.
// EphemerisDispatcher guarantees single-threaded dispatch; this mutex is a
// secondary safety net for unexpected concurrent JNI calls.
static std::mutex g_swe_mutex;
static bool       g_initialized = false;

// ─── Lifecycle ───────────────────────────────────────────────────────────────

extern "C" JNIEXPORT void JNICALL
Java_com_jugaad_core_ephemeris_jni_SwephJni_nativeInitialize(
        JNIEnv* env, jclass /* clazz */, jstring ephePath)
{
    std::lock_guard<std::mutex> lock(g_swe_mutex);

    const char* path = env->GetStringUTFChars(ephePath, nullptr);
    swe_set_ephe_path(path);
    env->ReleaseStringUTFChars(ephePath, path);

    // Set Lahiri (Chitra Paksha) ayanamsha — SEFLG_SIDEREAL requires this
    swe_set_sid_mode(SE_SIDM_LAHIRI, 0.0, 0.0);

    g_initialized = true;
    LOGI("INIT_OK");
}

extern "C" JNIEXPORT void JNICALL
Java_com_jugaad_core_ephemeris_jni_SwephJni_nativeRelease(
        JNIEnv* /* env */, jclass /* clazz */)
{
    std::lock_guard<std::mutex> lock(g_swe_mutex);
    swe_close();
    g_initialized = false;
    LOGI("RELEASE_OK");
}

// ─── Planet position computation ─────────────────────────────────────────────

// Returns a jdoubleArray of length 4: [longitude, latitude, speedDeg, isRetrograde(0/1)]
// planetId follows the bridge constants (0=Sun … 8=Ketu).
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jugaad_core_ephemeris_jni_SwephJni_nativeComputePlanet(
        JNIEnv* env, jclass /* clazz */, jdouble julianDayUt, jint planetId)
{
    std::lock_guard<std::mutex> lock(g_swe_mutex);

    if (!g_initialized) {
        LOGE("NOT_INIT");
        return nullptr;
    }

    double results[6] = {0};
    char   errStr[256] = {0};
    int    sweId;
    bool   isKetu = false;

    switch (planetId) {
        case PLANET_SUN:     sweId = SE_SUN;       break;
        case PLANET_MOON:    sweId = SE_MOON;      break;
        case PLANET_MERCURY: sweId = SE_MERCURY;   break;
        case PLANET_VENUS:   sweId = SE_VENUS;     break;
        case PLANET_MARS:    sweId = SE_MARS;      break;
        case PLANET_JUPITER: sweId = SE_JUPITER;   break;
        case PLANET_SATURN:  sweId = SE_SATURN;    break;
        case PLANET_RAHU:    sweId = SE_TRUE_NODE; break;
        case PLANET_KETU:    sweId = SE_TRUE_NODE; isKetu = true; break;
        default:
            LOGE("UNKNOWN_PLANET");
            return nullptr;
    }

    const int flags = SEFLG_SIDEREAL | SEFLG_SPEED;
    int ret = swe_calc_ut(julianDayUt, sweId, flags, results, errStr);

    if (ret < 0) {
        LOGE("CALC_FAILED");
        return nullptr;
    }

    double longitude = results[0];
    double latitude  = results[1];
    double speed     = results[3];  // deg/day

    if (isKetu) {
        longitude = fmod(longitude + 180.0, 360.0);
        speed     = -speed;  // Ketu mirrors Rahu motion
    }

    // speed < 0 means retrograde
    double retrograde = (speed < 0.0) ? 1.0 : 0.0;

    jdoubleArray out = env->NewDoubleArray(4);
    if (out == nullptr) return nullptr;

    double buf[4] = { longitude, latitude, speed, retrograde };
    env->SetDoubleArrayRegion(out, 0, 4, buf);
    return out;
}

// ─── House / Lagna computation ───────────────────────────────────────────────

// Returns jdoubleArray of length 13:
//   [0]   = Lagna (Ascendant) longitude (sidereal)
//   [1–12] = Whole Sign house cusps (not used for WSH but returned for reference)
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jugaad_core_ephemeris_jni_SwephJni_nativeComputeLagna(
        JNIEnv* env, jclass /* clazz */,
        jdouble julianDayUt, jdouble latitudeDeg, jdouble longitudeDeg)
{
    std::lock_guard<std::mutex> lock(g_swe_mutex);

    if (!g_initialized) {
        LOGE("NOT_INIT");
        return nullptr;
    }

    double cusps[13] = {0};   // SE_HOUSE_SYSTEM_WHOLE: 12 cusps + dummy[0]
    double ascmc[10]  = {0};  // [0]=ASC [1]=MC [2]=ARMC [3]=Vertex …

    // 'W' = Whole Sign house system
    swe_houses_ex(julianDayUt, SEFLG_SIDEREAL, latitudeDeg, longitudeDeg, 'W', cusps, ascmc);

    // ascmc[0] is the true eastern horizon (Lagna) in sidereal longitude
    jdoubleArray out = env->NewDoubleArray(13);
    if (out == nullptr) return nullptr;

    double buf[13];
    buf[0] = ascmc[0];             // Lagna
    for (int i = 1; i <= 12; i++) {
        buf[i] = cusps[i];         // Whole sign cusps (all 30° multiples)
    }
    env->SetDoubleArrayRegion(out, 0, 13, buf);
    return out;
}

// ─── Sunrise / Sunset with elevation correction ──────────────────────────────

// Returns jdoubleArray of length 2: [sunriseJd, sunsetJd]
// Applies apparent horizon correction: h = -0.8333° - (0.0347° * sqrt(elevMeters))
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jugaad_core_ephemeris_jni_SwephJni_nativeComputeSunriseSunset(
        JNIEnv* env, jclass /* clazz */,
        jdouble julianDayUt, jdouble latitudeDeg, jdouble longitudeDeg, jdouble elevationMeters)
{
    std::lock_guard<std::mutex> lock(g_swe_mutex);

    if (!g_initialized) {
        LOGE("NOT_INIT");
        return nullptr;
    }

    // Apparent horizon depression with elevation refraction
    double horizonDeg = SUNRISE_HORIZON_DEG - (SUNRISE_ELEV_FACTOR * sqrt(elevationMeters));

    double geopos[3]   = { longitudeDeg, latitudeDeg, elevationMeters };
    double atpress     = 1013.25;   // standard atmosphere hPa
    double attemp      = 15.0;      // standard temperature °C
    double sunriseJd   = 0.0;
    double sunsetJd    = 0.0;
    char   errStr[256] = {0};

    // SE_CALC_RISE: compute next sunrise from julianDayUt
    int retRise = swe_rise_trans(
        julianDayUt,
        SE_SUN, nullptr,
        SEFLG_SIDEREAL,
        SE_CALC_RISE,
        geopos, atpress, attemp,
        &sunriseJd, errStr
    );

    // SE_CALC_SET: compute next sunset from julianDayUt
    int retSet = swe_rise_trans(
        julianDayUt,
        SE_SUN, nullptr,
        SEFLG_SIDEREAL,
        SE_CALC_SET,
        geopos, atpress, attemp,
        &sunsetJd, errStr
    );

    if (retRise < 0 || retSet < 0) {
        LOGE("SUNRISE_FAILED");
        return nullptr;
    }

    jdoubleArray out = env->NewDoubleArray(2);
    if (out == nullptr) return nullptr;

    double buf[2] = { sunriseJd, sunsetJd };
    env->SetDoubleArrayRegion(out, 0, 2, buf);
    return out;
}

// ─── Ayanamsha query ─────────────────────────────────────────────────────────

extern "C" JNIEXPORT jdouble JNICALL
Java_com_jugaad_core_ephemeris_jni_SwephJni_nativeGetAyanamsha(
        JNIEnv* /* env */, jclass /* clazz */, jdouble julianDayUt)
{
    std::lock_guard<std::mutex> lock(g_swe_mutex);
    return swe_get_ayanamsa_ut(julianDayUt);
}
