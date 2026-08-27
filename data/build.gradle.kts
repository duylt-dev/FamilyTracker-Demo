plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    // phase-01: `:data` did not have a serialization plugin before routing — `:ui` already did.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.pion.family.tracker.demo.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.koin.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.play.services.location)
    // phase-01 — shared HTTP client (RoutingHttpClient) + JSON, wired via DataModule. No provider
    // (GraphHopper/Valhalla) lives in this phase; see LLM.md §14 for why OkHttp 5.5.0, not Ktor.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // `mockwebserver3-junit4`, NOT the legacy `mockwebserver` artifact — phase-01 Implementation
    // Step 1: OkHttp 5.x's maintained API is `mockwebserver3.MockWebServer` (immutable, builder-
    // constructed `MockResponse`), and this variant matches the project's JUnit4 runner.
    testImplementation(libs.okhttp.mockwebserver3.junit4)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    // Backs `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` above.
    // `androidx.test.ext:junit` alone does not pull this class in transitively — its absence
    // crashes the instrumentation process with ClassNotFoundException before any test runs.
    androidTestImplementation(libs.androidx.test.runner)
    // fix-phase-08 — `HistoryPipelineScaleTest` needs `PolyUtil`/`LatLng` to measure the History
    // pipeline's simplify stage on real device data. Test-scoped only: not a production `:data`
    // dependency, not shipped in the app or `:data`'s AAR.
    androidTestImplementation(libs.maps.compose.utils)
}
