import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing. The key's location and passwords live in "keystore.properties" in the
// project folder. That file is listed in .gitignore, so your secrets never reach GitHub.
// Without it, release builds are simply unsigned (debug builds always work).
val keystoreFile: File = rootProject.file("keystore.properties")
val keystore = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}

android {
    namespace = "io.github.javtamnanloo.cameradictionary"
    // compileSdk = the Android version the code is built against (newer libraries need 37).
    // It doesn't change how the app behaves; targetSdk below does.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // The app's permanent ID on Google Play. Never change it after the first upload.
        applicationId = "io.github.javtamnanloo.cameradictionary"
        minSdk = 24 // Android 7.0 and newer
        targetSdk = 36
        // Increase versionCode by 1 for every upload to Google Play; versionName is what users see.
        versionCode = 2
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 removes unused code and resources: a smaller, faster app.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    // Room writes a description of the database here. Keep these files in Git: they are
    // needed to safely upgrade users' saved words if the database ever changes.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // User interface
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Camera and on-device text recognition
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)

    // Saved words (Leitner box) and the daily reminder
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)

    // Dictionary lookups over HTTPS
    implementation(libs.okhttp)

    // Unit tests (they run on your computer, not on the phone)
    testImplementation(libs.junit)
    testImplementation(libs.org.json) // Android's built-in JSON parser only exists on a phone
}
