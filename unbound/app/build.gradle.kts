import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    signingConfigs {
        create("release") {
            val props = Properties().apply {
                val file = rootProject.file("local.properties")
                if (file.exists()) file.inputStream().use { load(it) }
            }
            (props["UNBOUND_KEYSTORE"] as String?)?.let { storeFile = file(it) }
            storePassword = props["UNBOUND_KEYSTORE_PASSWORD"] as String? ?: ""
            keyAlias = props["UNBOUND_KEY_ALIAS"] as String? ?: ""
            keyPassword = props["UNBOUND_KEY_PASSWORD"] as String? ?: ""
        }
    }

    namespace = "com.boazjonker.unbound"
    compileSdk = 37
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "com.boazjonker.pokemonunbound"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            applicationIdSuffix = ".dev"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core)
}
