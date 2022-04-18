import com.google.protobuf.gradle.*

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("dagger.hilt.android.plugin")
    id("com.google.protobuf")
}

android {
    compileSdk = 31

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "ru.vizbash.grapevine"
        minSdk = 25
        targetSdk = 31
        versionCode =  1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        signingConfig = signingConfigs.getByName("debug")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-XXLanguage:-ProhibitJvmFieldOnOverrideFromInterfaceInPrimaryConstructor"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.1")
    implementation("androidx.activity:activity-ktx:1.4.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.4.2")
    implementation("androidx.navigation:navigation-ui-ktx:2.4.2")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.paging:paging-runtime-ktx:3.1.1")
    implementation("androidx.room:room-runtime:2.4.2")
    implementation("androidx.room:room-ktx:2.4.2")
    implementation("androidx.room:room-paging:2.4.2")
    kapt("androidx.room:room-compiler:2.4.2")

    implementation("com.google.android.material:material:1.5.0")

    implementation("com.google.dagger:hilt-android:2.41")
    kapt("com.google.dagger:hilt-compiler:2.41")

    implementation("com.google.protobuf:protobuf-kotlin-lite:3.20.0-rc-1")

    implementation("com.github.dhaval2404:imagepicker:2.1")

    implementation("jp.wasabeef:recyclerview-animators:4.0.2")

    testImplementation("junit:junit:4.13.2")
}

kapt {
    correctErrorTypes = true
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.17.3"
    }
    plugins {
        id("kotlin")
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
//                id("kotlin")
                id("java") {
                    option("lite")
                }
//                getByName("java") {
//                    option("lite")
//                }
            }
            task.plugins {
                id("kotlin")
            }
        }
    }
}