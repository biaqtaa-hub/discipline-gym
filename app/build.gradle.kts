plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Each build on GitHub gets a higher version number, so a new APK installs over the old one.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "ru.disciplinegym.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.disciplinegym.app"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // One permanent key, kept in GitHub secrets (not in the code): updates keep the
    // same signature, so the phone keeps your data.
    val ksPassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = ksPassword
            keyAlias = "dg"
            keyPassword = ksPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
