import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
}

ksp {
    arg("room.generateKotlin", "true")
}

// Load signing credentials from keystore.properties (not committed to VCS)
val keystorePropsFile = rootProject.file("android/keystore.properties")
val keystoreProps = Properties().also { props ->
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { props.load(it) }
}

android {
    namespace = "com.household.app"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.jugaad.home"
        minSdk = 26
        targetSdk = 35
        versionCode = 30
        versionName = "1.0.29"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Per-ABI APKs: arm64-v8a (~35 MB) and armeabi-v7a (~30 MB) instead of a 120+ MB fat APK.
    // Drops x86/x86_64 emulator slices. Use a universal APK only for manual sideloading.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps["storeFile"] as String)
            storePassword = keystoreProps["storePassword"] as String
            keyAlias = keystoreProps["keyAlias"] as String
            keyPassword = keystoreProps["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://api.jugaad.app\"")
            buildConfigField("String", "HOUSEHOLD_ID", "\"3\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            buildConfigField("String", "BACKEND_BASE_URL", "\"http://10.0.2.2:5001\"")
            buildConfigField("String", "HOUSEHOLD_ID", "\"3\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Household modules
    implementation(project(":libs:household-core"))
    implementation(project(":modules:expenses"))
    implementation(project(":feature:astro"))
    implementation(project(":widget:astro-home"))
    implementation(project(":core:document-ai"))
    implementation(project(":core:llm-runtime"))

    // Core AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Lifecycle (ViewModel + LiveData)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Jetpack Compose BOM — pins all Compose library versions consistently
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose core
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Google Drive integration
    implementation("com.google.android.gms:play-services-auth:21.1.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20240123-2.0.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0")

    // Background sync
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // PDF text extraction
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // OpenCV — edge detection + perspective correction
    implementation("org.opencv:opencv:4.13.0")

    // ONNX Runtime — ML inference (DocQuad model + future use)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.26.0")

    // ML Kit OCR — Latin script (German + English + all Latin languages), bundled offline model
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-bundled-common:17.0.0")

    // CameraX (in-app receipt scanner)
    val cameraXVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    // Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Debug tooling (never in release)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // JSON serialization for local backup/restore
    implementation("com.google.code.gson:gson:2.10.1")

    // QR code generation (pure Java, no native deps)
    implementation("com.google.zxing:core:3.5.3")

    // QR / barcode scanning via camera
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // SQLCipher (Encrypted Database)
    api("net.zetetic:android-database-sqlcipher:4.5.4")

    // Jetpack Glance (Widgets)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Work around JDK image transform issues on this environment by disabling Javac tasks.
// Room KSP is configured above to generate Kotlin sources, so these tasks are not required.
afterEvaluate {
//    tasks.named("compileDebugJavaWithJavac").configure {
//        enabled = false
//    }
//    tasks.named("compileReleaseJavaWithJavac").configure {
//        enabled = false
//    }
}
