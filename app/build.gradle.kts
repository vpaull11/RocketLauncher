import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
    kotlin("kapt")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val requireReleaseKeystore =
    (project.findProperty("requireReleaseKeystore") as String?)?.toBooleanStrictOrNull() ?: false
val enableR8 =
    (project.findProperty("rocketLauncher.enableR8") as String?)?.toBooleanStrictOrNull() ?: false

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    val requiredKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val missing = requiredKeys.filter { keystoreProperties.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        throw GradleException(
            "keystore.properties: отсутствуют или пустые ключи: ${missing.joinToString()}. " +
                "См. keystore.properties.example"
        )
    }
    val storePath = keystoreProperties.getProperty("storeFile")!!
    if (!rootProject.file(storePath).exists()) {
        throw GradleException(
            "Файл keystore не найден: $storePath (storeFile в keystore.properties)"
        )
    }
}

afterEvaluate {
    listOf("bundleRelease", "assembleRelease").forEach { taskName ->
        tasks.findByName(taskName)?.doFirst {
            if (requireReleaseKeystore && !keystorePropertiesFile.exists()) {
                throw GradleException(
                    "Требуется release keystore: создайте keystore.properties (см. keystore.properties.example). " +
                        "Сборка без него не подходит для загрузки в Google Play."
                )
            }
        }
    }
}

android {
    namespace = "com.rocketlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rocketlauncher"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.6"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile")!!)
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = enableR8
            isShrinkResources = enableR8
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    packaging {
        jniLibs {
            pickFirsts += listOf(
                "**/libc++_shared.so",
                "**/libjsc.so",
                "**/libfbjni.so"
            )
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Process lifecycle (foreground / background)
    implementation("androidx.lifecycle:lifecycle-process:2.6.2")

    // Firebase Cloud Messaging (push; замените google-services.json из Firebase Console)
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Shortcode → Unicode для тысяч эмодзи Rocket.Chat / GitHub (:poop:, :relaxed:, …)
    implementation("com.vdurmont:emoji-java:5.1.1")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-svg:2.5.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Jitsi Meet (встроенный звонок; см. CHANGELOG-MOBILE-SDKS при обновлении)
    implementation("org.jitsi.react:jitsi-meet-sdk:12.0.0") {
        isTransitive = true
    }

    // Firebase (optional, для push)
    // implementation(platform("com.google.firebase:firebase-bom:32.5.0"))
    // implementation("com.google.firebase:firebase-messaging-ktx")
}
