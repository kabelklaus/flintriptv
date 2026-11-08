import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
    alias(libs.plugins.googleGmsServices)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.firebase.crashlytics)
    id("jacoco")
}

/*
 * T11 Baseline Profile: intentionally NOT activated in this branch.
 *
 * AGP 9.2.1 + Gradle 9.5.0 are newer than the androidx.baselineprofile line verified here;
 * nothing is pinned in gradle/libs.versions.toml. The documented 1.4.x line targets AGP 8.x,
 * and no AGP-9-compatible version could be verified offline, so applying it unchecked would
 * risk breaking configuration of this build. Minimal setup to finish later (operator-owned):
 *   1. add `id("androidx.baselineprofile") version "<AGP-9-compatible>"` to this plugins
 *      block and `apply false` to the root build,
 *   2. include a `:baselineprofile` com.android.test module in settings.gradle.kts,
 *   3. add profileinstaller + androidx.benchmark:benchmark-macro-junit4 to that module.
 * Then `./gradlew :app:generateBaselineProfile` becomes available.
 */

android {
    namespace = "com.flintr.tv"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.flintr.tv"
        minSdk = 24
        targetSdk = 36
        versionCode = 116
        versionName = "2.54.0"

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
        debug {
            applicationIdSuffix = ".debug"
            // App-Name für Debug kommt aus src/debug/res/values/strings.xml (AGP 9: resValues im BuildType deaktiviert)
            // FFmpeg-Decoder (.so): x86_64 nur für den Emulator
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // armeabi-v7a ist Pflicht: viele TV-Boxen laufen mit 32-bit-Userland
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            firebaseCrashlytics {
                mappingFileUploadEnabled = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
    @Suppress("DEPRECATION")
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        disable += "MutableCollectionMutableState"
        disable += "AutoboxingStateCreation"  // Bug in Lint mit Kotlin 2.x + Compose
        abortOnError = false  // Lint-Fehler brechen Build nicht ab
    }
    testOptions {
        unitTests {
            // Robolectric: Compose-UI-Tests (Klick-/Fokus-Verhalten) als lokale Unit-Tests,
            // damit sie in den Jacoco-Report einfließen, den Sonar konsumiert
            isIncludeAndroidResources = true

            all { test ->
                // mockk-Inline-Instrumentierung: Agent beim JVM-Start laden statt
                // per Self-Attach mitten im Lauf. Ohne dies schlagen Property-Stubs
                // auf finalen Klassen in kalten Test-Workern (u.a. CI) sporadisch
                // mit "Missing mocked calls" fehl.
                val byteBuddyAgent = configurations.getByName("byteBuddyAgent")
                test.jvmArgumentProviders.add(
                    CommandLineArgumentProvider {
                        listOf("-javaagent:${byteBuddyAgent.singleFile.absolutePath}")
                    }
                )
            }
        }
    }
}

val byteBuddyAgent: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    group = "verification"
    description = "Generates a Jacoco coverage report for debug unit tests (consumed by SonarQube)."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val buildDirectory = layout.buildDirectory.get().asFile
    val coverageExclusions = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "**/*_Factory.*",
        "**/*_MembersInjector.*",
        "**/*Hilt*.*",
        "**/*Dagger*.*"
    )

    classDirectories.setFrom(
        files(
            fileTree("$buildDirectory/intermediates/classes/debug/transformDebugClassesWithAsm/dirs") {
                exclude(coverageExclusions)
            }
        )
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
            )
        }
    )
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
    debugImplementation(libs.compose.ui.test.manifest)
    
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
    implementation(libs.media3.session)
    // Software-Audio-Decoder (ac3/eac3/dts/truehd) für Geräte ohne funktionierenden HW-Decoder; LGPL, siehe THIRD_PARTY_NOTICES.md
    implementation(libs.media3.ffmpeg.decoder)
    
    // Image Loading
    implementation(libs.coil.compose)
    
    // TV Support
    implementation(libs.leanback)
    
    // Security
    implementation(libs.security.crypto)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.crashlytics)
    
    // Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    byteBuddyAgent(libs.byte.buddy.agent)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.espresso.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
