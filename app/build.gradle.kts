plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// --- PJSIP presence detection -------------------------------------------------
// One of these enables real SIP. When neither exists we compile against the
// pjsua2 API stub in src/pjsipStub so the project still builds and tests run.
val pjsipAar = file("libs/pjsua2-release.aar")
val pjsipJar = file("libs/pjsua2.jar")
val usePjsipStub = !pjsipAar.exists() && !pjsipJar.exists()

android {
    namespace = "com.magick.gsm2sip"
    compileSdk = 34

    if (usePjsipStub) {
        // No native PJSIP present (CI / dev): build against the API-compatible
        // stub. At runtime System.loadLibrary("pjsua2") fails gracefully and SIP
        // stays disabled until a real AAR is dropped into app/libs/.
        sourceSets.getByName("main").java.srcDir("src/pjsipStub/java")
    }

    defaultConfig {
        applicationId = "com.magick.gsm2sip"
        minSdk = 31          // Android 12 (backward-compat floor)
        targetSdk = 34       // Android 14
        versionCode = 1
        versionName = "1.0.0"

        // PJSIP ships native .so libs for these ABIs. Trim to what your devices need.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Let android.util.Log & friends return defaults instead of throwing
            // in plain JVM unit tests.
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                it.testLogging {
                    events("passed", "skipped", "failed")
                    setExceptionFormat("full")
                    showStackTraces = true
                    showStandardStreams = true
                }
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // pjsua2 ships a single libpjsua2.so per ABI; keep it uncompressed for fast load.
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // --- PJSIP / pjsua2 -----------------------------------------------------
    // The pjsua2 Java bindings (org.pjsip.pjsua2.*) plus the native .so files.
    // Provide ONE of the following (see docs/PJSIP_BUILD.md):
    //   1. A prebuilt AAR dropped into app/libs/ (recommended), OR
    //   2. The generated pjsua2.jar + jniLibs/<abi>/libpjsua2.so from
    //      pjsip-android-builder.
    when {
        pjsipAar.exists() -> implementation(files(pjsipAar))
        // Raw jar + jniLibs (.so files live in src/main/jniLibs/<abi>/).
        pjsipJar.exists() -> implementation(files(pjsipJar))
        // else: the stub source set (added in the android block) provides the
        // org.pjsip.pjsua2 API at compile time. See docs/PJSIP_BUILD.md.
    }

    // --- AndroidX / Kotlin --------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.android)

    // --- Compose UI ---------------------------------------------------------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // --- Persistence --------------------------------------------------------
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Testing ------------------------------------------------------------
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}
