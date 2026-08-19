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
        versionCode = 9
        versionName = "0.5.4"
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
                // نکته: `file(...)` مسیرِ نسبی را نسبت به همین ماژول حساب می‌کند،
                // یعنی `app/mrm-release.p12` می‌شد `app/app/mrm-release.p12` و
                // امضای ریلیز با «فایل وجود ندارد» شکست می‌خورد. ورک‌فلو مسیر را
                // نسبت به ریشهٔ مخزن می‌دهد، پس از ریشه حساب می‌کنیم. مسیرِ مطلق
                // هم دست‌نخورده می‌ماند.
                storeFile = rootProject.file(storePath)
                val storePw = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
                storePassword = storePw
                keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
                // ورک‌فلو RELEASE_KEY_PASSWORD را پاس نمی‌دهد و امضا با «کلمهٔ عبورِ
                // کلید نیست» می‌ایستاد. در PKCS12 رمزِ کلید و رمزِ فایل عملاً یکی
                // هستند، پس اگر جداگانه داده نشد از همان رمزِ فایل استفاده می‌کنیم.
                keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull ?: storePw
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
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    // پروفایلِ پایه را روی دستگاه نصب می‌کند. بدون این، APKای که دستی نصب
    // می‌شود (خارج از پلی‌استور) از پروفایل‌های آماده‌ای که کتابخانه‌های کامپوز
    // همراه خودشان دارند هیچ استفاده‌ای نمی‌کند و همه‌چیز باید با JIT گرم شود.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // تست‌های واحد (JVM)
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // org.json در JVM tests استاب است؛ پیاده‌سازی واقعی لازم داریم.
    testImplementation("org.json:json:20240303")
}
