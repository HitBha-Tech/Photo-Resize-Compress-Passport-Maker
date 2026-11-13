import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.hitbha.photo"
    compileSdk = 36

    val adMobProps = Properties().apply {
        val f = rootProject.file("admob.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun prop(key: String) = adMobProps.getProperty(key)?.trim().orEmpty()

    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun keyProp(key: String) = keystoreProps.getProperty(key)?.trim().orEmpty()

    // AdMob IDs come exclusively from the gitignored admob.properties file
    // (same IDs for all build types).
    val adMobAppId = prop("adMobAppId")
    val adInterstitialId = prop("adInterstitialId")
    val adRewardedInterstitialId = prop("adRewardedInterstitialId")

    defaultConfig {
        applicationId = "com.hitbha.photo"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        buildConfigField("String", "ADMOB_APP_ID", "\"$adMobAppId\"")
        buildConfigField("String", "AD_INTERSTITIAL_ID", "\"$adInterstitialId\"")
        buildConfigField("String", "AD_REWARDED_INTERSTITIAL_ID", "\"$adRewardedInterstitialId\"")

        manifestPlaceholders["adMobAppId"] = adMobAppId

        androidResources.localeFilters.addAll(listOf(
            "en", "ja", "zh", "ar", "hi", "es", "fr", "pt",
            "ru", "de", "it", "ko", "th", "vi", "id", "ms",
            "tr", "bn", "ur", "fa", "pl"
        ))
    }
    
	signingConfigs {
        create("release") {
            storeFile = rootProject.file(keyProp("storeFile").ifBlank { "app/keystore/release.jks" })
            storePassword = keyProp("storePassword")
            keyAlias = keyProp("keyAlias")
            keyPassword = keyProp("keyPassword")
        }
    }
	
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    bundle {
        abi {
            enableSplit = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    // Image loading
    implementation("com.github.bumptech.glide:glide:5.0.7")
    kapt("com.github.bumptech.glide:compiler:5.0.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Google Mobile Ads
    implementation("com.google.android.gms:play-services-ads:24.9.0")

    // ML Kit Selfie Segmentation (background remover)
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    // ML Kit Text Recognition (document scanner -> Excel table)
    // Unbundled variant: OCR engine ships with Google Play services, not in the APK.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
