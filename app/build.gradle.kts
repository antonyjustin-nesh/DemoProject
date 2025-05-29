import java.util.Properties

plugins {
    kotlin("kapt")
    id("com.google.devtools.ksp")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    kotlin("plugin.serialization") version "1.8.0"
}

android {
    namespace = "com.nlevcluster.sender"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.nlevcluster.sender"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters+= listOf("arm64-v8a")
        }
    }

    val localProperties = Properties().apply {
        load(project.rootProject.file("local.properties").inputStream())
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file(localProperties["KEYSTORE_FILE"] as String)
            storePassword = localProperties["KEYSTORE_PASSWORD"] as String
            keyAlias = localProperties["KEY_ALIAS"] as String
            keyPassword = localProperties["KEY_PASSWORD"] as String
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
        aidl = true
    }
    android { packagingOptions.resources.excludes.add("META-INF/*") }
}

dependencies {

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.7.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
//    Hilt
    implementation("com.google.dagger:hilt-android:2.44.2")
    kapt("com.google.dagger:hilt-android-compiler:2.44.2")

// MQTT
    val mqttClient = "1.2.5"
    val mqttService = "1.1.1"
    val timber = "5.0.1"
    implementation("com.jakewharton.timber:timber:$timber")
//    implementation("org.eclipse.paho:org.eclipse.paho.android.service:$mqttService")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:$mqttClient")

//    Room
    val roomVersion = "2.5.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-paging:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

//    KotlinXDateTime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

//    Logcat
    implementation("com.squareup.logcat:logcat:0.1")

    //Gson
    implementation("com.google.code.gson:gson:2.10.1")

    //Firebase integration
    //BOM
    implementation(platform("com.google.firebase:firebase-bom:32.2.2"))
    //Analytics
    implementation("com.google.firebase:firebase-analytics-ktx")
    //Crashlytics
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // HiveMQ Client
    implementation("com.hivemq:hivemq-mqtt-client-shaded:1.3.3")

    implementation ("androidx.datastore:datastore-preferences:1.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("com.guolindev.permissionx:permissionx:1.7.1")

}

// Allow references to generated code
kapt {
    correctErrorTypes = true
}

