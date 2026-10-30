plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hsxy.oplusx"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hsxy.oplusx"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-stage0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    compileOnly("de.robv.android.xposed:api:82")
    implementation("com.highcapable.yukihookapi:api:1.3.1")
    implementation("com.highcapable.kavaref:kavaref-core:1.0.2")
    implementation("com.highcapable.kavaref:kavaref-extension:1.0.1")
    implementation("com.github.kyuubiran:EzXHelper:2.2.1")
    ksp("com.highcapable.yukihookapi:ksp-xposed:1.3.1")
}
