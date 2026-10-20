plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.opablosantanaa.granalivre"
    compileSdk {
        version = release(37)

        defaultConfig {
            minSdk = 26
            javaCompileOptions {
                annotationProcessorOptions {
                    arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.opablosantanaa.granalivre"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion") // em Java é annotationProcessor
    testImplementation("androidx.room:room-testing:$roomVersion")

    implementation("androidx.work:work-runtime:2.10.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.6.1")

    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.9.0")
    
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}