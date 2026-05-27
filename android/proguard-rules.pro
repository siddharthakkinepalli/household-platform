-keep class com.household.app.** { *; }
-keep class com.household.expenses.** { *; }
-keep class com.household.core.** { *; }
-keepclassmembers class * {
    @androidx.lifecycle.* *;
}

# PdfBox-Android: JP2/JPEG2000 decoder is optional; not shipped on Android
-dontwarn com.gemalto.jp2.JP2Decoder
