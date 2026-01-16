// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.sonarqube)
}
sonar {
    properties {
        val sonarProjectKey = System.getenv("SONAR_PROJECT_KEY") ?: findProperty("sonar.projectKey")?.toString() ?: ""
        val sonarProjectName = System.getenv("SONAR_PROJECT_NAME") ?: findProperty("sonar.projectName")?.toString() ?: ""

        property("sonar.projectKey", sonarProjectKey)
        property("sonar.projectName", sonarProjectName)
        
        // Exclude test directories from analysis
        property("sonar.exclusions", "**/test/**,**/androidTest/**,**/*Test.kt,**/*Tests.kt")
        property("sonar.test.exclusions", "**/*")
        
        // Android Lint report path
        property("sonar.androidLint.reportPaths", "app/build/reports/lint-results-debug.xml")
    }
}

// Ensure lint report is generated before sonar task
tasks.named("sonar").configure {
    dependsOn(":app:lintReportDebug")
}