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

android {
    namespace = "io.apogee.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.apogee.launcher"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.3"
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
