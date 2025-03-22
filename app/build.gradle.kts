plugins {
    id("com.android.application")
    id("com.google.gms.google-services") // Ensure this is included
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

// Apply Google Services Plugin at the bottom
apply(plugin = "com.google.gms.google-services")
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
android {
    namespace = "com.example.safeguard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.safeguard" // Fix incorrect syntax
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" // Fix incorrect syntax
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }



    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    /// implementation(libs.google.firebase.messaging.ktx)
    testImplementation(libs.junit)  // Use 'junit' alias from version catalog
    implementation(libs.firebase.auth.ktx) // Ensure that `libs` is correctly set up
    //implementation(libs.firebase.messaging)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.activity)

    // Google Maps
    implementation(libs.maps)
    implementation(libs.location)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging.ktx)

    // Glide for image loading
    implementation(libs.glide)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)


    // JUnit for unit testing
    testImplementation(libs.junit)

    // AndroidX Testing
    androidTestImplementation(libs.androidx.junit)
    // androidTestImplementation(libs.androidx.espresso.core)

    // Lifecycle KTX
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // UI Testing
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(platform(libs.firebase.bom))
    //implementation(libs.firebase.database)
   // implementation(libs.firebase.storage)
    implementation(libs.androidx.core.ktx)

}

