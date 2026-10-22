plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kisskh.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kisskh.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 10
        versionName = "3.3"
    }

    // Reuse the established key, supplied outside git. Without it, release builds are unsigned.
    val signingValues = listOf("KH_KEYSTORE_PATH", "KH_STORE_PASSWORD", "KH_KEY_ALIAS", "KH_KEY_PASSWORD")
        .map { System.getenv(it) }
    require(signingValues.all { it.isNullOrBlank() } || signingValues.all { !it.isNullOrBlank() }) {
        "Provide all four KH signing environment variables, or none for an unsigned validation build."
    }
    val releaseSigning = if (signingValues.all { !it.isNullOrBlank() }) {
        signingConfigs.create("establishedRelease") {
            storeFile = file(signingValues[0]!!)
            storePassword = signingValues[1]
            keyAlias = signingValues[2]
            keyPassword = signingValues[3]
        }
    } else null

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.tv:tv-foundation:1.0.0-alpha12")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.paging:paging-compose:3.3.5")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // Installs the baseline profiles shipped inside Compose and Media3, and our own, so release starts faster.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Reaches the video inside embed players' frames, for the seek bar.
    implementation("androidx.webkit:webkit:1.12.1")

    // Media3 ExoPlayer — HLS playback, the YouTube-grade player
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JVM unit tests only
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
