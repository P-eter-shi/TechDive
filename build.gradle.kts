// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Top-level build file where you can add configuration options common to all sub-projects/modules.


plugins {
   // id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    //id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false // Ensure this is here
    alias(libs.plugins.android.application) apply false
   // alias(libs.plugins.kotlin.android)
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
    configurations.all {
        resolutionStrategy {
            // Use this format instead
            force("androidx.core:core-ktx:1.12.0")
            // For Firebase dependencies if needed
            force("com.google.firebase:firebase-database-ktx:20.3.0")
            force("com.google.firebase:firebase-storage-ktx:20.3.0")
        }
    }
}
