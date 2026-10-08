plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Reads a signing credential from the environment first, then gradle.properties. Blank is
 * treated as absent: CI passes an empty string for a secret that is not configured.
 */
fun credential(environmentVariable: String, property: String): String? =
    (System.getenv(environmentVariable) ?: project.findProperty(property)?.toString())
        ?.takeIf { it.isNotBlank() }

val releaseKeystore = credential("APOGEE_KEYSTORE_FILE", "apogee.keystoreFile")

/**
 * The single source of truth for the version. The code is derived from the name so the two
 * can never drift: Google Play rejects an upload whose versionCode has not increased, and
 * Android needs it to increase to treat an install as an upgrade rather than a reinstall.
 */
val appVersionName = "1.4"

val appVersionCode = appVersionName.split(".")
    .map { it.toIntOrNull() ?: error("version '$appVersionName' must be numeric, dot-separated") }
    .let { parts ->
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        require(minor < 100 && patch < 100) { "minor and patch must each stay under 100" }
        major * 10_000 + minor * 100 + patch
    }

android {
    namespace = "io.apogee.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.apogee.launcher"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // Two builds of the same launcher. `standard` asks for nothing at all and installs
    // without a Play Protect warning; `badges` adds the notification listener that powers
    // live tile badges, at the cost of that warning when sideloaded.
    flavorDimensions += "notifications"

    productFlavors {
        create("standard") {
            dimension = "notifications"
            isDefault = true
            buildConfigField("boolean", "BADGES_AVAILABLE", "false")
        }
        create("badges") {
            dimension = "notifications"
            buildConfigField("boolean", "BADGES_AVAILABLE", "true")
            versionNameSuffix = "-badges"
        }
    }

    signingConfigs {
        // Only declared when credentials are actually available, so a plain checkout still
        // configures.
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = credential("APOGEE_KEYSTORE_PASSWORD", "apogee.keystorePassword")
                keyAlias = credential("APOGEE_KEY_ALIAS", "apogee.keyAlias")
                keyPassword = credential("APOGEE_KEY_PASSWORD", "apogee.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Fall back to the debug key so `assembleRelease` always produces an APK you can
            // sideload and test. A debug-signed APK is fine for testing but cannot be
            // published to Play — set the APOGEE_KEYSTORE_* credentials for that.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
