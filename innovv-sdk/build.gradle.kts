import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    `maven-publish`
}

group = "com.zoloft.innovv"
version = "0.1.0"

kotlin {
    explicitApi()

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.target.get()))
        }
    }

    android {
        namespace = "com.zoloft.innovv.sdk"
        compileSdk = libs.versions.android.compile.sdk.get().toInt()
        minSdk = libs.versions.android.min.sdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.target.get()))
        }

        withHostTest {}
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "InnovvSDK"
            isStatic = true
            binaryOption("bundleId", "com.zoloft.innovv.sdk")
            freeCompilerArgs += listOf(
                "-Xoverride-konan-properties=" +
                    "osVersionMin.ios_arm64=18.0;" +
                    "osVersionMin.ios_simulator_arm64=18.0;" +
                    "osVersionMin.ios_x64=18.0",
            )
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.network)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("INNOVV K5 Kotlin Multiplatform SDK")
            description.set(
                "Kotlin Multiplatform client for the INNOVV K5 dashcam local HTTP, " +
                    "media, live-view and event APIs.",
            )
            url.set("https://github.com/zoloft/innovv-sdk")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("zoloft")
                    name.set("zoloft")
                }
            }
            scm {
                url.set("https://github.com/zoloft/innovv-sdk")
                connection.set("scm:git:git@github.com:zoloft/innovv-sdk.git")
                developerConnection.set("scm:git:git@github.com:zoloft/innovv-sdk.git")
            }
        }
    }

    repositories {
        maven {
            name = "buildDir"
            url = layout.buildDirectory.dir("repo").get().asFile.toURI()
        }
    }
}
