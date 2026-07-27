plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "com.metrocompose"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // `api`, not `implementation`: Compose types appear in this library's public signatures
    // (Modifier, Color, SharedTransitionScope…), so consumers need them on the compile
    // classpath. Without this, every consumer has to re-declare the whole Compose stack.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.foundation)
    api(libs.androidx.animation)
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.material3)

    // BackHandler, used by the jump grid and the navigation host.
    implementation(libs.androidx.activity.compose)
}

// components["release"] only exists once the Android plugin has finished configuring.
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.metrocompose"
                artifactId = "metro"
                version = "1.0.0"
            }
        }
    }
}
