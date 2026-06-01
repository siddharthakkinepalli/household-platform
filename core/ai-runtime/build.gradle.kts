plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.jugaad.core.airuntime"
    compileSdk = 35

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        buildConfig = true
    }

    aaptOptions {
        // Tells the Android Asset Packaging Tool not to compress the model files
        // This allows ONNX to memory-map the file directly from disk
        noCompress("ort", "json", "txt")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.3")
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
