plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.rezacoffee.app"

    compileSdk = 34

    defaultConfig {
        applicationId = "ir.rezacoffee.app"

        // Android 7.0 Nougat
        minSdk = 24

        // Android 14
        targetSdk = 34

        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    /*
     * Do not generate ABI-specific APKs.
     *
     * A single APK is easier for users to install.
     *
     * If the application or one of its dependencies contains
     * native libraries, the CI workflow will inspect the APK
     * and report the available ABIs.
     */
    splits {
        abi {
            isEnable = false
        }
    }

    /*
     * Release signing
     *
     * The real release keystore is supplied by GitHub Actions
     * through environment variables.
     */
    signingConfigs {
        create("release") {
            val keystorePath =
                System.getenv("KEYSTORE_PATH")

            val keystorePassword =
                System.getenv("KEYSTORE_PASSWORD")

            val keyAlias =
                System.getenv("KEY_ALIAS")

            val keyPassword =
                System.getenv("KEY_PASSWORD")

            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
            }

            if (!keystorePassword.isNullOrBlank()) {
                storePassword = keystorePassword
            }

            if (!keyAlias.isNullOrBlank()) {
                this.keyAlias = keyAlias
            }

            if (!keyPassword.isNullOrBlank()) {
                this.keyPassword = keyPassword
            }

            /*
             * Support old and new Android versions.
             */
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {

        release {
            /*
             * R8 / resource shrinking
             */
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )

            /*
             * IMPORTANT:
             *
             * A production Release APK must be signed with the
             * permanent release keystore.
             *
             * We intentionally do NOT silently fall back to the
             * debug key.
             */
            signingConfig =
                signingConfigs.getByName("release")
        }

        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
        viewBinding = true
    }

    /*
     * Avoid duplicate META-INF files when dependencies contain them.
     */
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/{LGPL2.1,LICENSE}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")

    implementation("androidx.appcompat:appcompat:1.6.1")

    implementation("com.google.android.material:material:1.11.0")

    implementation(
        "androidx.swiperefreshlayout:swiperefreshlayout:1.1.0"
    )

    implementation("androidx.webkit:webkit:1.10.0")

    implementation("androidx.activity:activity-ktx:1.8.2")

    implementation(
        "androidx.constraintlayout:constraintlayout:2.1.4"
    )
}
