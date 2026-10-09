plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 사용자에게 전달하는 빌드마다 버전 0.1, 빌드 번호 1씩 올린다. 버그 없는 버전이 나오면 1.0.
val appVersionName = "0.5"
val appVersionCode = 6

android {
    namespace = "com.example.bluewind"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.bluewind"
        // BluetoothHidDevice API가 Android 9(API 28)부터 있다
        minSdk = 28
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// assembleDebug가 끝나면 dist/bluewind_v<버전>.apk 로 복사한다
val distApk = tasks.register<Copy>("distApk") {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename("app-debug.apk", "bluewind_v$appVersionName.apk")
}
tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(distApk)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
