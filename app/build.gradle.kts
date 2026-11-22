plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ABIs to build. Default: real arm64 phones only (fast builds). For the x86_64 emulator run
// ./gradlew ... -Pmedidiary.abis=arm64-v8a,x86_64   (or edit gradle.properties)
val medidiaryAbis = (findProperty("medidiary.abis") as String? ?: "arm64-v8a")
    .split(",").map { it.trim() }.filter { it.isNotEmpty() }

android {
    namespace = "com.medidiary.app"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.medidiary.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            // Only build for modern 64-bit ARM phones (fast builds)
            abiFilters += medidiaryAbis
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
                cppFlags += "-O3 -fexceptions -frtti"
            }
        }
    }

    // Dev 1's original JNI smoke-test library (NativeBridge.testJni). The real inference
    // libraries live in the :whisper and :llama modules.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Demo builds: sign with the debug key so the APK can be side-loaded directly.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    androidResources {
        // Never let aapt compress large binary assets (if a team member bundles a model for a demo).
        noCompress += listOf("gguf", "bin")
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { useLegacyPackaging = false }
    }
}

dependencies {
    implementation(project(":whisper"))
    implementation(project(":llama"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
