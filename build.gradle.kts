// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.googleGmsServices) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
sonar {
    properties {
        val sonarProjectKey = System.getenv("SONAR_PROJECT_KEY") ?: findProperty("sonar.projectKey")?.toString() ?: ""
        val sonarProjectName = System.getenv("SONAR_PROJECT_NAME") ?: findProperty("sonar.projectName")?.toString() ?: ""
        val sonarOrganization = System.getenv("SONAR_ORGANIZATION") ?: findProperty("sonar.organization")?.toString() ?: ""

        property("sonar.projectKey", sonarProjectKey)
        property("sonar.projectName", sonarProjectName)
        if (sonarOrganization.isNotBlank()) {
            property("sonar.organization", sonarOrganization)
        }
        
        // Exclude test directories from analysis
        property("sonar.exclusions", "**/test/**,**/androidTest/**,**/*Test.kt,**/*Tests.kt")
        property("sonar.test.exclusions", "**/*")
        
        // Report paths must be absolute: the Sonar sensors resolve relative paths
        // against each module's base dir, so ':app' would look in app/app/... and
        // silently import no coverage ("No coverage report can be found").
        property("sonar.androidLint.reportPaths", "${rootProject.projectDir}/app/build/reports/lint-results-debug.xml")
        property("sonar.coverage.jacoco.xmlReportPaths", "${rootProject.projectDir}/app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml")
        property("sonar.qualitygate.wait", "true")
    }
}

// Ensure lint report is generated before sonar task
tasks.named("sonar").configure {
    dependsOn(":app:lintReportDebug", ":app:jacocoDebugUnitTestReport")
}
