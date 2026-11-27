plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.tunnix.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tunnix.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 13
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val envKeystore = System.getenv("KEYSTORE_FILE") ?: (project.findProperty("KEYSTORE_FILE") as? String)
            val keystoreFile = if (!envKeystore.isNullOrBlank()) {
                file(envKeystore)
            } else {
                project.rootProject.file("release.keystore")
            }

            val storePass = System.getenv("KEYSTORE_PASSWORD") ?: (project.findProperty("KEYSTORE_PASSWORD") as? String)
            val keyAliasVal = System.getenv("KEY_ALIAS") ?: (project.findProperty("KEY_ALIAS") as? String)
            val keyPass = System.getenv("KEY_PASSWORD") ?: (project.findProperty("KEY_PASSWORD") as? String)

            if (keystoreFile.exists() && !storePass.isNullOrBlank() && !keyAliasVal.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = keyAliasVal
                keyPassword = keyPass
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            } else {
                // Insecure fallback passwords have been completely removed.
                // If credentials are not supplied via environment or properties,
                // signingConfig is omitted so an insecure build cannot proceed silently.
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists() && !releaseSigning.storePassword.isNullOrBlank()) {
                signingConfig = releaseSigning
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    gradle.taskGraph.whenReady {
        val hasReleasePackagingTask = allTasks.any { 
            it.name.equals("packageRelease", ignoreCase = true) ||
            it.name.equals("packageReleaseBundle", ignoreCase = true) ||
            it.name.equals("assembleRelease", ignoreCase = true) ||
            it.name.equals("bundleRelease", ignoreCase = true)
        }
        // Only enforce keystore credentials when an actual release packaging or assembly task is explicitly invoked
        val isExplicitReleaseBuild = gradle.startParameter.taskNames.any { 
            it.contains("Release", ignoreCase = true) && !it.contains("test", ignoreCase = true)
        }
        if (hasReleasePackagingTask && isExplicitReleaseBuild) {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile == null || !releaseSigning.storeFile!!.exists() || releaseSigning.storePassword.isNullOrBlank()) {
                throw GradleException(
                    "❌ Secure Release Build Failed: Missing KEYSTORE_PASSWORD, KEY_ALIAS, or KEY_PASSWORD.\n" +
                    "Hardcoded fallback secrets have been completely removed for security compliance.\n" +
                    "Please provide valid credentials via environment variables (KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)."
                )
            }
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
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core-models"))
    implementation(project(":libv2ray"))
    implementation(project(":subscription-parser"))
    implementation(project(":config-modifier"))
    implementation(project(":xray-core"))
    implementation(project(":vpn-service"))
    implementation(project(":traffic-monitor"))
    implementation(project(":settings"))
    implementation(project(":ui"))

    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
}
