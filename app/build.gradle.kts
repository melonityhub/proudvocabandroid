import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.proudvocab.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.proudvocab.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += setOf("en", "fa", "tr", "de", "fr", "es", "ar", "ru")
    }

    // ---------------------------------------------------------------------
    // Signing
    // A fresh clone must build `assembleRelease` without any secret, so the
    // release build falls back to the stable release keystore committed to
    // the repo (keystore/release.keystore + keystore/release.properties).
    // The key is stable across CI runs, which is what lets one GitHub
    // Release install as an in-place update of the previous one — the debug
    // signing config must NOT be used for releases, because a fresh CI
    // runner generates a brand-new debug key on every run.
    // To sign with your own key instead, supply a keystore through the
    // environment (takes priority over the committed one):
    //   PV_KEYSTORE_FILE, PV_KEYSTORE_PASSWORD, PV_KEY_ALIAS, PV_KEY_PASSWORD
    // ---------------------------------------------------------------------
    val envKeystore = System.getenv("PV_KEYSTORE_FILE")
    val envKeystoreReady = !envKeystore.isNullOrBlank() && file(envKeystore).exists()

    val repoKeystoreFile = file("keystore/release.keystore")
    val repoKeystoreProps = file("keystore/release.properties")
    val repoKeystoreReady = repoKeystoreFile.exists() && repoKeystoreProps.exists()

    signingConfigs {
        if (envKeystoreReady) {
            create("upload") {
                storeFile = file(envKeystore!!)
                storePassword = System.getenv("PV_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PV_KEY_ALIAS")
                keyPassword = System.getenv("PV_KEY_PASSWORD")
            }
        } else if (repoKeystoreReady) {
            val repoKeystoreProperties = java.util.Properties()
            repoKeystoreProps.inputStream().use { repoKeystoreProperties.load(it) }
            create("repoRelease") {
                storeFile = repoKeystoreFile
                storeType = "PKCS12"
                storePassword = repoKeystoreProperties.getProperty("storePassword")
                keyAlias = repoKeystoreProperties.getProperty("keyAlias")
                keyPassword = repoKeystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = when {
                envKeystoreReady -> signingConfigs.getByName("upload")
                repoKeystoreReady -> signingConfigs.getByName("repoRelease")
                else -> {
                    logger.warn(
                        "ProudVocab: no release keystore available — signing the release " +
                            "build with the debug config (unstable signature on CI runners)."
                    )
                    signingConfigs.getByName("debug")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ---------------------------------------------------------------------
    // ABI splits: one APK per popular CPU architecture plus a universal
    // APK that covers all of them. The CI workflow builds and uploads all
    // four release APKs (see .github/workflows/android.yml).
    //   arm64-v8a    — every modern 64-bit phone/tablet (dominant ABI)
    //   armeabi-v7a  — older 32-bit ARM devices
    //   x86_64       — 64-bit Intel/AMD devices and emulators
    // Distinct versionCodes per split are assigned in the androidComponents
    // block below.
    // ---------------------------------------------------------------------
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*"
            )
        }
    }

    androidResources {
        // The app is fully RTL aware — Persian is a first class citizen, not an
        // afterthought, so every screen ships with `supportRtl` and mirrored
        // layouts. Only the locales we actually translate are kept, which keeps
        // the APK small.
        // (The bundled dictionary stays compressed: it is streamed out of the
        // APK into internal storage the first time it is needed.)
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        disable += setOf("MissingTranslation", "ExtraTranslation", "GradleDependency", "OldTargetApi")
    }
}

// ---------------------------------------------------------------------
// Unique versionCode per ABI split. Google Play requires every APK in a
// multi-APK release to have a distinct versionCode, and two APKs with
// the same code can never be installed side by side. The universal APK
// keeps the base versionCode; each per-ABI split gets base + rank*1000
// so it is always newer than the universal APK and unique among the
// splits (armeabi-v7a +1000, arm64-v8a +2000, x86_64 +3000).
// ---------------------------------------------------------------------
val baseVersionCode = android.defaultConfig.versionCode ?: 1

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            val rank = when (abi) {
                "armeabi-v7a" -> 1
                "arm64-v8a" -> 2
                "x86_64" -> 3
                else -> 0
            }
            if (rank > 0) {
                output.versionCode.set(rank * 1000 + baseVersionCode)
            }
        }
    }
}

// Room writes its schema here, so future migrations can be verified.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    implementation(libs.mlkit.translate)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}

// Full assertion messages in the CI log — a bare "FAILED" is not enough to
// diagnose a broken test from a remote runner.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showExceptions = true
        showStackTraces = true
    }
}
