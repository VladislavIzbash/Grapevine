// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    dependencies {
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.38.1")
    }
}

plugins {
    val kotlinVersion = "1.6.10"

    id("com.android.application") version "7.1.0" apply false
    id("com.android.library") version "7.1.0" apply false
    id("org.jetbrains.kotlin.android") version kotlinVersion apply false
    id("com.google.protobuf") version "0.8.17" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
