import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    import org.jetbrains.kotlin.gradle.dsl.JvmTarget
}

group = "com.sharmadhiraj.installed_apps"
version = "1.0-SNAPSHOT"

repositories {
        google()
        mavenCentral()
    }

android {
    namespace = "com.sharmadhiraj.installed_apps"

    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    defaultConfig {
        minSdk = 24
    }

}

 kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
