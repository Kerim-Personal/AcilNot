plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // 'kapt' yerine daha performanslı olan 'KSP' plugin'i kullanılıyor.
    alias(libs.plugins.google.ksp)
}

android {
    namespace = "com.codenzi.acilnot"
    // compileSdk ve targetSdk en son sürüme güncellendi.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.codenzi.acilnot"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true // YENİ EKLENEN SATIR: Çoklu DEX'i etkinleştir
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
    implementation(libs.androidx.room.ktx)
    // 'kapt' yerine 'ksp' kullanılıyor.
    ksp(libs.androidx.room.compiler)
    implementation(libs.gson)

    // Çoklu DEX bağımlılığı
    implementation("androidx.multidex:multidex:2.0.1") // YENİ EKLENEN SATIR

    // Mevcut kütüphaneler (zaten version catalog kullanıyordu)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}