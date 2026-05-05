-keep class com.household.app.** { *; }
-keep class com.household.expenses.** { *; }
-keep class com.household.core.** { *; }
-keepclassmembers class * {
    @androidx.lifecycle.* *;
}
