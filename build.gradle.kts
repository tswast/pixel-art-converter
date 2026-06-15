plugins {
    kotlin("multiplatform") version "1.9.24" apply false
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.compose") version "1.6.10" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
