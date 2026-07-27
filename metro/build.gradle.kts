plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
    signing
}

// Maven coordinates. The groupId has to be a namespace you can prove you own, and nobody owns
// `com.metrocompose`, so Central would refuse it — `io.github.<user>` is the free, verifiable
// alternative. The Kotlin package stays `com.metrocompose`: a groupId and a package name are
// different things, so no source changes and no import churn for consumers.
group = "io.github.diffechento"
version = "1.0.0"

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
            // Central requires both alongside the aar, and rejects a bundle missing either.
            withSourcesJar()
            withJavadocJar()
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

// Signing material, from ~/.gradle/gradle.properties or the environment — deliberately not from
// anywhere inside the repository. Absent, signing is skipped and every ordinary build, including
// publishToMavenLocal, still works; only a Central bundle actually needs it.
val signingKeyArmored: String? =
    (findProperty("signingKey") as String?) ?: System.getenv("SIGNING_KEY")
val signingKeyPassword: String? =
    (findProperty("signingPassword") as String?) ?: System.getenv("SIGNING_PASSWORD")

// components["release"] only exists once the Android plugin has finished configuring.
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                artifactId = "metro"

                // Central validates every one of these and rejects the bundle if any is missing.
                pom {
                    name.set("MetroCompose")
                    description.set(
                        "A Jetpack Compose UI kit for building Android apps that look and move " +
                            "like Windows Phone 8: tiles, the panorama, the pivot, the long list " +
                            "with its jump grid, and the WP8 transitions."
                    )
                    url.set("https://github.com/Diffechento/MetroCompose")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://github.com/Diffechento/MetroCompose/blob/main/LICENSE")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("Diffechento")
                            name.set("Diffechento")
                            url.set("https://github.com/Diffechento")
                        }
                    }
                    scm {
                        url.set("https://github.com/Diffechento/MetroCompose")
                        connection.set("scm:git:https://github.com/Diffechento/MetroCompose.git")
                        developerConnection.set(
                            "scm:git:ssh://git@github.com/Diffechento/MetroCompose.git"
                        )
                    }
                }
            }
        }

        repositories {
            // Not a remote: the Central Portal does not accept a plain Maven deploy, it accepts an
            // uploaded bundle. So publish into a directory laid out as a Maven repository, and let
            // `centralBundle` zip it up.
            maven {
                name = "centralStaging"
                url = uri(layout.buildDirectory.dir("staging-deploy"))
            }
        }
    }

    signing {
        isRequired = signingKeyArmored != null
        if (signingKeyArmored != null) {
            useInMemoryPgpKeys(signingKeyArmored, signingKeyPassword)
            sign(publishing.publications["release"])
        }
    }
}

/**
 * The artefact you upload to https://central.sonatype.com — Publish → Upload a bundle.
 *
 * `maven-metadata.xml` is excluded on purpose: it describes a repository rather than a release,
 * and the Portal's validator rejects a bundle that contains it.
 */
val centralBundle by tasks.registering(Zip::class) {
    group = "publishing"
    description = "Builds the zip bundle to upload to the Maven Central Portal."
    dependsOn("publishReleasePublicationToCentralStagingRepository")
    from(layout.buildDirectory.dir("staging-deploy")) {
        exclude("**/maven-metadata*")
    }
    archiveFileName.set("metro-$version-central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central"))
}
