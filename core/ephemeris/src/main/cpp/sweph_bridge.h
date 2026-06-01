#pragma once

#include <jni.h>
#include <string>

// JNI class descriptor — must match Kotlin package exactly
#define SWEPH_JNI_CLASS "com/jugaad/core/ephemeris/jni/SwephJni"

// Planet ID constants matching Kotlin enum — do NOT change without updating PlanetPosition.kt
#define PLANET_SUN      0
#define PLANET_MOON     1
#define PLANET_MERCURY  2
#define PLANET_VENUS    3
#define PLANET_MARS     4
#define PLANET_JUPITER  5
#define PLANET_SATURN   6
#define PLANET_RAHU     7   // True North Node (SE_TRUE_NODE)
#define PLANET_KETU     8   // Derived: Rahu + 180°

// Lahiri (Chitra Paksha) ayanamsha — SE_SIDM_LAHIRI = 1
#define AYANAMSHA_LAHIRI 1

// Swiss Ephemeris flag: sidereal + speed + true node
#define SWE_FLG_SIDEREAL  (SEFLG_SIDEREAL | SEFLG_SPEED)
#define SWE_FLG_TOPOCTR   SEFLG_TOPOCTR

// Atmospheric refraction constant for sunrise (IAU standard apparent depression)
constexpr double SUNRISE_HORIZON_DEG = -0.8333;
constexpr double SUNRISE_ELEV_FACTOR = 0.0347;
