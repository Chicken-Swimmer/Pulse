plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "app.pulse.monitor"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Separate ID so this fork can sit next to the F-Droid build
        applicationId = "app.pulse.monitor"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 30
        versionName = "1.0.30"

        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Keep lint pragmatic but useful; keep a short, justifiable disable list
        disable += setOf(
            "ResAuto", // Using ?attr and res-auto where appropriate; false positive in some cases
            "IconMissingDensityFolder", // Vector assets and M3 iconography preferred
            "IconDensities" // Same rationale as above
        )
        // Treat warnings as non-fatal in CI/local
        abortOnError = false
        warningsAsErrors = false
        // Ignore test sources
        ignoreTestSources = true
    }
}

dependencies {
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar")))) // No local jars; keep disabled for now

    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)

    // Support Libraries & UI Components
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Networking (currently using HttpURLConnection)
    // Future: Consider migrating to Retrofit for better network handling

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Lifecycle
    implementation(libs.bundles.androidx.lifecycle)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Room
    implementation(libs.bundles.androidx.room)
    kapt(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // UI Components
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.glide)

    // Multidex
    implementation(libs.androidx.multidex)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
