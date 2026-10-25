plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mecnun.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mecnun.assistant"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Sabit imza anahtarı: her derlemede aynı imza olsun ki yeni APK eskisinin üstüne güncellenebilsin.
    signingConfigs {
        create("mecnun") {
            storeFile = file("mecnun.keystore")
            storePassword = "mecnun123"
            keyAlias = "mecnun"
            keyPassword = "mecnun123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("mecnun")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("mecnun")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    androidResources {
        noCompress += listOf("mdl", "fst", "int", "conf", "mat", "dubm", "stats")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Çevrimdışı konuşma tanıma
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47")
}
