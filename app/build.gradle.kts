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

// --- Versioning ----------------------------------------------------------
// version.txt (repo root) is the single source of truth, bumped by the
// release-please workflow (.github/workflows/release-please.yml) from
// Conventional Commits merged to main. versionCode is derived from it so
// the two never drift: MAJOR*1_000_000 + MINOR*1_000 + PATCH. MINOR and
// PATCH must each stay below 1000 or they collide with the next unit.
val versionFile = rootProject.layout.projectDirectory.file("version.txt")
check(versionFile.asFile.exists()) {
    "version.txt not found at repo root (${versionFile.asFile}); it must be checked in as the " +
        "single source of truth for versionName/versionCode."
}
val versionNameProp = providers.fileContents(versionFile).asText.get().trim()
val (versionMajor, versionMinor, versionPatch) = versionNameProp
    .split("-", limit = 2)[0]
    .split(".")
    .also {
        require(it.size == 3) {
            "version.txt must be MAJOR.MINOR.PATCH (optionally with a -prerelease " +
                "suffix), got '$versionNameProp'"
        }
    }
    .map {
        it.toIntOrNull()
            ?: throw GradleException("version.txt component '$it' is not a number (got '$versionNameProp')")
    }
    .also { (_, minor, patch) ->
        require(minor < 1000 && patch < 1000) {
            "version.txt MINOR and PATCH must be < 1000 to keep versionCode collision-free, got '$versionNameProp'"
        }
    }
    .let { Triple(it[0], it[1], it[2]) }
val computedVersionCode = versionMajor * 1_000_000 + versionMinor * 1_000 + versionPatch

android {
    namespace = "com.magick.gsm2sip"
    compileSdk = 34

    if (usePjsipStub) {
        // No native PJSIP present (CI / dev): build against the API-compatible
        // stub. At runtime System.loadLibrary("pjsua2") fails gracefully and SIP
        // stays disabled until a real AAR is dropped into app/libs/.
        sourceSets.getByName("main").java.srcDir("src/pjsipStub/java")
    } else {
        // Real PJSIP bindings drive the app, but the SWIG jar's classes require
        // libpjsua2.so, which cannot load in plain JVM / Robolectric unit tests
        // (UnsatisfiedLinkError). The stub is binary-compatible with the real
        // bindings, so compile & run the unit tests against it instead. The real
        // jar is also filtered off the unit-test classpath below so there is a
        // single, native-free source of the org.pjsip.pjsua2.* classes for tests.
        sourceSets.getByName("test").java.srcDir("src/pjsipStub/java")
    }

    defaultConfig {
        applicationId = "com.magick.gsm2sip"
        minSdk = 31          // Android 12 (backward-compat floor)
        targetSdk = 34       // Android 14
        versionCode = computedVersionCode
        versionName = versionNameProp

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

    lint {
        // MODIFY_PHONE_STATE and CAPTURE_AUDIO_OUTPUT are signature/privileged
        // permissions declared on purpose for system/rooted deployments (see
        // README "Known limitations"). Suppress the ProtectedPermissions error
        // rather than weakening lint globally; everything else still fails CI.
        disable += "ProtectedPermissions"
        abortOnError = true
        warningsAsErrors = false
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

// Keep the native pjsua2.jar off the JVM unit-test classpath so tests resolve
// the stub source set added above instead of the SWIG bindings (which would try
// to System.loadLibrary("pjsua2") and fail on the JVM). The app/APK variants are
// unaffected and still ship the real bindings + .so.
if (!usePjsipStub) {
    tasks.withType<Test>().configureEach {
        classpath = classpath.filter { it != pjsipJar }.let { files(it) }
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
