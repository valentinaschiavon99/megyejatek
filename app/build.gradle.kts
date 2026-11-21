plugins {
    alias(libs.plugins.android.application)
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"), so kotlin-android is not applied here.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

/*
 * Firebase is optional at build time: google-services.json is not in the repo (it is injected in CI
 * from a secret, and kept locally in app/). Without it the app still builds and runs fully offline,
 * just without accounts, the global leaderboard and crash reports.
 */
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

/**
 * Release signing secrets come from environment variables (CI secrets) or ~/.gradle/gradle.properties,
 * never from the repo. Without them the release build is simply left unsigned.
 */
fun secret(name: String): String? =
    providers.environmentVariable(name).orElse(providers.gradleProperty(name)).orNull?.trim()?.takeIf { it.isNotEmpty() }

val releaseKeystore = secret("MEGYEJATEK_KEYSTORE_PATH")

/** CI passes the workflow run number and commit, so every published APK has a higher versionCode. */
val buildNumber = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1
val gitSha = providers.gradleProperty("gitSha").orNull?.take(7) ?: "local"

android {
    namespace = "io.github.valentinaschiavon99.megyejatek"
    // Compose 1.13 requires compiling against the API 37.2 minor release.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.valentinaschiavon99.megyejatek"
        minSdk = 26
        targetSdk = 37
        versionCode = buildNumber
        versionName = "0.$buildNumber"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField(
            "String",
            "UPDATE_INFO_URL",
            "\"https://github.com/valentinaschiavon99/megyejatek/releases/download/latest/version.json\"",
        )

        testInstrumentationRunner = "io.github.valentinaschiavon99.megyejatek.HiltTestRunner"
        // ScreenshotTest writes PNGs through Test Storage; AGP copies them off the device after the run.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }

    signingConfigs {
        // Shared, public debug key: every machine and every CI build signs debug APKs the same way,
        // so a newer build installs over an older one without uninstalling. Never use it for release.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = secret("MEGYEJATEK_KEYSTORE_PASSWORD")
                keyAlias = secret("MEGYEJATEK_KEY_ALIAS")
                keyPassword = secret("MEGYEJATEK_KEY_PASSWORD") ?: secret("MEGYEJATEK_KEYSTORE_PASSWORD")
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Map data is read with AssetManager; keep it uncompressed for fast startup reads.
        noCompress += listOf("geojson", "json")
    }

    lint {
        // Health check: lint runs in CI and its SARIF report shows up under the repo's Security tab.
        sarifReport = true
        htmlReport = true
        checkDependencies = true
        abortOnError = true
        // Security checks that must never be ignored.
        error += listOf(
            "HardcodedDebugMode",
            "SetJavaScriptEnabled",
            "ExportedService",
            "ExportedReceiver",
            "ExportedContentProvider",
            "TrustAllX509TrustManager",
            "BadHostnameVerifier",
            "UnsafeIntentLaunch",
            "InsecureBaseConfiguration",
        )
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.crashlytics)
    // App Check: Play Integrity in release builds, the debug provider (a token you allow in the console) in debug.
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.test.storage)
    androidTestUtil(libs.androidx.test.services)
    kspAndroidTest(libs.hilt.compiler)
}
