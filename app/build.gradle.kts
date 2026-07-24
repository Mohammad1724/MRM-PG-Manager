plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mrm.pgmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mrm.pgmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.3.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            // مقادیر فقط در GitHub Actions از Secrets تزریق می‌شوند؛ هیچ کلیدی داخل repo نیست.
            val storePath = providers.gradleProperty("RELEASE_STORE_FILE").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
