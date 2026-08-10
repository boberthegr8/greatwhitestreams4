plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.gwstreams.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.local.media.viewer.v4"
        minSdk = 24
        targetSdk = 34
        versionCode = 20
        versionName = "4.20"
        vectorDrawables { useSupportLibrary = true }
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a") // Idea 33: Cut APK size by 40% (No x86 bloat)
        }
    }


    signingConfigs {
        create("release") {
            storeFile = file("../keystore.jks")
            storePassword = "securepass123"
            keyAlias = "localmedia"
            keyPassword = "securepass123"
        }
    }
    
    buildTypes {
        debug {
            isMinifyEnabled = false // Keep debug fast
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    // Reuse the shared data/repo/model code from the app module.
    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/main/java",
                "../app/src/main/java/com/gwstreams/app/data"
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ExoPlayer / Media3
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Local persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
