plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.ksp)
}

android {
    namespace = "com.codenzi.acilnot"
    // DÜZELTME: compileSdk, en son stabil sürüme (35) çekildi.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codenzi.acilnot"
        minSdk = 24
        // DÜZELTME: targetSdk, en son stabil sürüme (35) çekildi.
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Tüm kütüphaneler artık libs.versions.toml dosyasından (version catalog) okunuyor.
    // Sabit sürüm numaraları ve yerel değişkenler kaldırıldı.

    // Lifecycle, Room ve Gson kütüphaneleri güncellendi ve version catalog'a taşındı.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx) // Bu satırın düzgün biçimlendirildiğinden emin olun
    // 'kapt' yerine 'ksp' kullanılıyor.
    ksp(libs.androidx.room.compiler)
    implementation(libs.gson)

    // Çoklu DEX bağımlılığı
    implementation("androidx.multidex:multidex:2.0.1")

    // Mevcut kütüphaneler (zaten version catalog kullanıyordu)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.kotlinx.coroutines.android)
}