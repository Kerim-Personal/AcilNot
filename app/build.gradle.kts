plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.ksp)
}

android {
    namespace = "com.codenzi.snapnote"
    // DÜZELTME: compileSdk, en son stabil sürüme (35) çekildi.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codenzi.snapnote"
        minSdk = 24
        // DÜZELTME: targetSdk, en son stabil sürüme (35) çekildi.
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro" //
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
    // ViewBinding özelliğini etkinleştir
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // GÜVENLİK GÜNCELLEMESİ
    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx) //
    // 'kapt' yerine 'ksp' kullanılıyor.
    ksp(libs.androidx.room.compiler) //
    implementation(libs.gson)

    // Mevcut kütüphaneler
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
    implementation(libs.androidx.work.runtime.ktx)

    // KameraX Kütüphaneleri
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Coil (Resim Yükleme Kütüphanesi)
    implementation("io.coil-kt:coil:2.6.0")
}