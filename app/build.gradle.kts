plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.cleanbanar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cleanbanar"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    @Suppress("DEPRECATION")
    kotlinOptions {
        jvmTarget = "17"
    }



    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Firebase - BoM manages all Firebase library versions automatically
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")

    // UI
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Security — EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // QR Code Scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Chart & Animation
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.airbnb.android:lottie:6.4.0")
    
    // Image Cropper
    implementation("com.github.yalantis:ucrop:2.2.8")
}

