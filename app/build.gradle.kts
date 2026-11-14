plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.inkwell"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.inkwell"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "WIFI_SERVER_IP", "\"${project.findProperty("wifiServerIp") ?: ""}\"")
        buildConfigField("String", "UPLOAD_TOKEN", "\"${project.findProperty("wifiUploadToken") ?: ""}\"")
        buildConfigField("String", "CERT_PIN", "\"${project.findProperty("wifiCertPin") ?: ""}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
}
