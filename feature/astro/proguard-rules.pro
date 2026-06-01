# ── SQLCipher JNI ─────────────────────────────────────────────────────────────
# Keep all native method bridges; R8 must not rename them (JNI name resolution)
-keep class net.zetetic.database.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── Room ──────────────────────────────────────────────────────────────────────
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}

# ── Hilt ──────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keepclasseswithmembers class * {
    @dagger.* <fields>;
    @dagger.* <methods>;
    @javax.inject.* <fields>;
    @javax.inject.* <methods>;
}

# ── AstroLogger debug block stripping ─────────────────────────────────────────
# R8 eliminates branches where BuildConfig.DEBUG == false at compile time.
# No additional rules needed; ensure BuildConfig.DEBUG is a boolean constant.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# ── Swiss Ephemeris JNI bridge ─────────────────────────────────────────────────
# Retain native bridge class when core:ephemeris is linked
-keep class com.jugaad.core.ephemeris.** { native <methods>; }

# ── Kotlin serialization ───────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# ── Prevent AstroKeyProvider from being optimized away ────────────────────────
-keep class com.jugaad.core.security.keystore.AstroKeyProvider { *; }
-keep class com.jugaad.core.security.keystore.KeystoreManager { *; }
-keep class com.jugaad.core.security.keystore.WrappedSecret { *; }
