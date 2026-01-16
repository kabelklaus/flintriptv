// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false
    id("org.sonarqube") version "7.2.2.6593"
}
sonar {
    properties {
        property("sonar.projectKey", "kabelklaus_flintriptv_31e48b26-a944-4433-93e4-81e2e9b53a91")
        property("sonar.projectName", "flintriptv")
    }
}