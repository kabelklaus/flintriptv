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
        // build.gradle.kts + AndroidManifest: bewusste Won't-Fix-Findings (Cleartext AN, SDK-Bumps eigene Story, x86_64 nicht im Release); external_android-lint/xml-Rules reagieren weder auf NOSONAR noch auf scanner-side sonar.issue.ignore (restricted) -> Dateien aus der Analyse genommen
        property("sonar.exclusions", "**/test/**,**/androidTest/**,**/*Test.kt,**/*Tests.kt,**/build.gradle.kts,**/AndroidManifest.xml")
        property("sonar.test.exclusions", "**/*")

        // Coverage exclusions: code that cannot be meaningfully unit-tested locally.
        // - DI wiring, Android entry points: pure framework glue
        // - DebugOverlay: developer tooling, not user-facing behavior
        // - UpdateInstaller: fires PackageInstaller intents, device-only
        // - XtreamApiService: Retrofit interface definition
        // - UserRepository/CredentialsRepository: thin Firestore/EncryptedSharedPreferences
        //   I/O wrappers (AndroidKeyStore unavailable in Robolectric); covered indirectly
        //   via mocked collaborators in service tests
        // - Navigation/Player/Settings/Tab screens: require NavHost, ExoPlayer, or
        //   Hilt-provided ViewModels and are exercised via instrumented tests instead
        property(
            "sonar.coverage.exclusions",
            listOf(
                "**/com/flintr/tv/di/**",
                "**/com/flintr/tv/MainActivity.kt",
                "**/com/flintr/tv/FlintRTVApplication.kt",
                "**/com/flintr/tv/ui/components/player/DebugOverlay.kt",
                "**/com/flintr/tv/utils/UpdateInstaller.kt",
                "**/com/flintr/tv/data/api/XtreamApiService.kt",
                "**/com/flintr/tv/data/repository/UserRepository.kt",
                "**/com/flintr/tv/data/local/CredentialsRepository.kt",
                "**/com/flintr/tv/ui/navigation/**",
                "**/com/flintr/tv/ui/screens/PlayerScreen.kt",
                "**/com/flintr/tv/ui/screens/SeriesEpisodesScreen.kt",
                "**/com/flintr/tv/ui/screens/DataLoadingScreen.kt",
                "**/com/flintr/tv/ui/screens/LoginScreen.kt",
                "**/com/flintr/tv/ui/settings/**",
                "**/com/flintr/tv/ui/tabs/**",
                "**/com/flintr/tv/ui/sidebars/CategoryRow.kt",
                "**/com/flintr/tv/ui/sidebars/ContentGridLayout.kt",
                "**/com/flintr/tv/ui/components/ContentCard.kt",
                "**/com/flintr/tv/ui/components/SearchOverlay.kt",
                "**/com/flintr/tv/ui/components/MainLayout.kt",
                "**/com/flintr/tv/ui/components/player/VideoSettingsDialog.kt",
                "**/com/flintr/tv/ui/components/player/CustomPlayerControls.kt"
            ).joinToString(",")
        )
        
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
