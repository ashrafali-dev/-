plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.ashrafali.webtoonbridge"; compileSdk = 35
    defaultConfig { applicationId = "com.ashrafali.webtoonbridge"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.2.0"
        ndk { abiFilters += listOf("armeabi-v7a") }
    }
    buildTypes { release { isMinifyEnabled = false } }
    buildFeatures { buildConfig = true }
    splits { abi { isEnable = false } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}