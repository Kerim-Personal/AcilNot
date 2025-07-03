plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Room'un @Dao, @Entity gibi annotation'larını işlemesi için kapt plugin'ini ekleyin.
    id("kotlin-kapt")
}

android {
    namespace = "com.codenzi.acilnot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codenzi.acilnot"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    // ViewBinding'i etkinleştirmek kodu daha güvenli hale getirir, ancak şimdilik devre dışı bırakalım.
    // viewBinding {
    //     enable = true
    // }
}

dependencies {

    // Eklenecek olan yeni kütüphane
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    // Room Kütüphaneleri
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // Mevcut kütüphaneler
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}