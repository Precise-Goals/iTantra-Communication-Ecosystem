import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.itantra"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.itantra"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
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
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    @Suppress("DEPRECATION")
    aaptOptions {
        noCompress += listOf("onnx", "ort", "tflite", "bin", "pb", "ftz")
    }

    androidResources {
        noCompress += listOf("onnx", "ort", "tflite", "bin", "pb", "ftz")
    }
}

// Protobuf configuration
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // ONNX Runtime Mobile
    implementation(libs.onnxruntime.android)

    // sherpa-onnx: real espeak-ng-based TTS phonemization (Piper/Coqui/Mimic3 VITS voices).
    // Uses the "static-link-onnxruntime" AAR variant — ONNX Runtime is statically linked into
    // libsherpa-onnx-jni.so instead of shipping its own libonnxruntime.so, so it doesn't collide
    // with the onnxruntime-android dependency above (verified: no libonnxruntime.so present for
    // arm64-v8a/armeabi-v7a/x86_64 inside this AAR — only an unrelated x86 variant has one, and
    // x86 isn't in our abiFilters).
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.7.aar"))

    // Pure-JVM tar+bzip2 extraction for downloaded sherpa-onnx TTS voice bundles
    implementation(libs.commons.compress)

    // Real on-device Phi-3/GGUF inference for the AI Assistant (replaces keyword-matching
    // fallback text). Native libs cover arm64-v8a + x86_64 only (verified by inspecting the
    // AAR) — no armeabi-v7a build; LlmModule.isDeviceSupported() gates this gracefully.
    implementation(libs.llamacpp.kotlin)

    // Protocol Buffers (Java Lite)
    implementation(libs.protobuf.javalite)

    // DataStore (preferences + device profile)
    implementation(libs.androidx.datastore.preferences)

    // Room (peer registry database)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager (background model downloads)
    implementation(libs.androidx.work.runtime.ktx)

    // OkHttp (resumable HTTP downloads for models)
    implementation(libs.okhttp)

    // Kotlinx Serialization (model manifest JSON)
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
