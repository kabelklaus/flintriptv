import org.gradle.api.GradleException
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.flintr.tv"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.flintr.tv"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "1.14"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField("String", "GITHUB_REPO_OWNER", "\"kabelklaus\"")
        buildConfigField("String", "GITHUB_REPO_NAME", "\"flintriptv\"")
        
        // GitHub token: from environment variable (CI/CD) or local.properties (local builds)
        val localPropertiesFile = rootProject.file("local.properties")
        val githubToken = System.getenv("GITHUB_TOKEN")
            ?: if (localPropertiesFile.exists()) {
                val props = Properties().apply {
                    localPropertiesFile.inputStream().use(::load)
                }
                props.getProperty("github.token", "")
            } else {
                ""
            }
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("local.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties().apply {
                    keystorePropertiesFile.inputStream().use(::load)
                }
                
                val keystorePath = keystoreProperties.getProperty("keystore.path")
                val storePwd = keystoreProperties.getProperty("keystore.password")
                val keyAliasName = keystoreProperties.getProperty("keystore.keyAlias")
                val keyPwd = keystoreProperties.getProperty("keystore.keyPassword")

                if (keystorePath.isNullOrBlank() || storePwd.isNullOrBlank() || keyAliasName.isNullOrBlank() || keyPwd.isNullOrBlank()) {
                    throw GradleException("For release builds, signing configuration is required. Please ensure 'keystore.path', 'keystore.password', 'keystore.keyAlias', and 'keystore.keyPassword' are set in 'local.properties'.")
                }

                storeFile = rootProject.file(keystorePath)
                storePassword = storePwd
                keyAlias = keyAliasName
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
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
        buildConfig = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.activity)
    implementation(libs.compose.lifecycle.viewmodel)
    implementation(libs.compose.lifecycle.runtime)
    implementation(libs.compose.navigation)
    debugImplementation(libs.compose.ui.tooling)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    
    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)
    
    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.hls)
    
    // Image Loading
    implementation(libs.coil.compose)
    
    // TV Support
    implementation(libs.leanback)
    
    // Security
    implementation(libs.security.crypto)
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}