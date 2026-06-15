plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.core:core-ktx:1.12.0")
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.13.2")
                implementation("androidx.test.ext:junit:1.1.5")
                implementation("androidx.test.espresso:espresso-core:3.5.1")
                implementation("androidx.compose.ui:ui-test-junit4:1.6.0")
                implementation("androidx.compose.ui:ui-test-manifest:1.6.0")
            }
        }
    }
}

android {
    namespace = "tech.bananajuice.convertpixelart"
    compileSdk = 34

    defaultConfig {
        applicationId = "tech.bananajuice.convertpixelart"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

// Add cargo task to build rust lib using cargo ndk
tasks.register<Exec>("cargoBuildArm64") {
    workingDir(File(project.rootDir, "crates/android-lib"))
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "-o", "../../app/src/androidMain/jniLibs", "build", "--release")
}

tasks.register<Exec>("cargoBuildArmeabi") {
    workingDir(File(project.rootDir, "crates/android-lib"))
    commandLine("cargo", "ndk", "-t", "armeabi-v7a", "-o", "../../app/src/androidMain/jniLibs", "build", "--release")
}

tasks.register<Exec>("cargoBuildX86") {
    workingDir(File(project.rootDir, "crates/android-lib"))
    commandLine("cargo", "ndk", "-t", "x86", "-o", "../../app/src/androidMain/jniLibs", "build", "--release")
}

tasks.register<Exec>("cargoBuildX86_64") {
    workingDir(File(project.rootDir, "crates/android-lib"))
    commandLine("cargo", "ndk", "-t", "x86_64", "-o", "../../app/src/androidMain/jniLibs", "build", "--release")
}

tasks.register("cargoBuildAll") {
    dependsOn("cargoBuildArm64", "cargoBuildArmeabi", "cargoBuildX86", "cargoBuildX86_64")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("cargoBuildAll")
}
