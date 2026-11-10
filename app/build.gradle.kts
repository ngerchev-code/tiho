import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is read from keystore.properties (never committed). See README.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasKeystore = keystoreProps.getProperty("storeFile") != null

// Every automated build gets a higher number, so a newer build always installs over an older one.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.ngerchev.tiho"
    compileSdk = 35

    defaultConfig {
        // Placeholder package name. It cannot be changed after the first Play upload.
        applicationId = "com.ngerchev.tiho"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.5.$buildNumber"

        // Phones are ARM; leaving out the emulator (x86) builds halves the size of the APK.
        // (The automated tests run on an x86 emulator and pass -PallAbis to keep everything.)
        if (!project.hasProperty("allAbis")) {
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // "direct" = the APK people install from the link; it can update itself.
    // "play"   = the Google Play edition: no internet permission, updates come from Play.
    flavorDimensions += "dist"
    productFlavors {
        create("direct") { dimension = "dist" }
        create("play") { dimension = "dist" }
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
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
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // On-device translation. Only in the "direct" edition: the library needs the internet
    // once, to download the language pack.
    "directImplementation"("com.google.mlkit:translate:17.0.3")

    // Automated checks: text rules on the build machine, and the screens on an emulator.
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
