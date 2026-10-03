// Shared Android library: the WebView shell both apps are built from.
// The Android Gradle plugin version comes from the consuming app's root build.
plugins {
    id("com.android.library")
}

android {
    namespace = "com.sectorchakra.shell"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
