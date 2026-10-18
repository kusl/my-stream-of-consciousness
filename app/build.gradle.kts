import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

fun releaseSigning(): Map<String, String>? {
    val env = listOf("MSOC_KEYSTORE_PATH", "MSOC_KEYSTORE_PASSWORD", "MSOC_KEY_ALIAS", "MSOC_KEY_PASSWORD")
        .map { System.getenv(it).orEmpty() }
    if (env[0].isNotBlank()) return signingKeys.zip(env).toMap()
    val file = rootProject.file("keystore.properties")
    if (!file.exists()) return null
    val props = Properties().apply { file.inputStream().use(::load) }
    return signingKeys.associateWith { props.getProperty(it).orEmpty() }.takeIf { it.getValue("storeFile").isNotBlank() }
}

android {
    namespace = "com.github.kusl.msoc"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.github.kusl.msoc"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("MSOC_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("MSOC_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.0"
    }

    val signing = releaseSigning()
    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = file(signing.getValue("storeFile"))
                storePassword = signing["storePassword"]
                keyAlias = signing["keyAlias"]
                keyPassword = signing["keyPassword"]
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/versions/**",
                "/about.html",
                "/plugin.properties",
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work)
    implementation(libs.jgit)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(composeBom)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
