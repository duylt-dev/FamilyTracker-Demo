plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// `local.properties` keys, read as a build input via `providers` — NOT `Properties().load(...)`
// at configuration time, which configuration cache silently freezes. See LLM.md §10 and
// ENV-BRIEFING.md §5. Generalized from the original single-key `mapsApiKey` reader (routing plan
// phase-01 Step 10) to also read the 4 routing keys below.
val localPropertiesText = providers.fileContents(
    rootProject.layout.projectDirectory.file("local.properties")
).asText

// `takeIf { it.isNotEmpty() } ?: default` inside the `map`, not just `.getOrElse(default)` on the
// outer `Provider` — `getOrElse` only fires when `local.properties` itself is missing (the
// `Provider` has no value at all). A present-but-blank key (`ROUTING_ENGINE=`, which
// `local.properties.example` explicitly documents as "bỏ trống -> mặc định GRAPHHOPPER") and an
// absent key both fall through `firstOrNull`/`substringAfter` to `""`, which `getOrElse` never
// sees — `RoutingEngine.valueOf("")` would then throw at startup for a case the example file
// promises is safe. Post-review fix (coordinator defect 2, 2026-08-24): missing file, missing
// key, and blank value now all land on the same documented `default`.
fun localProperty(key: String, default: String = ""): String =
    localPropertiesText.map { text ->
        text.lineSequence().firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")?.trim()?.takeIf { it.isNotEmpty() } ?: default
    }.getOrElse(default)

val mapsApiKey = localProperty("MAPS_API_KEY")

// Routing plan phase-01 Step 10 — 4 keys read the same way. `ROUTING_ENGINE` default matches
// `local.properties.example`'s documented default; the other 3 default to empty (Valhalla-only
// or optional fields depending on engine).
val routingEngine = localProperty("ROUTING_ENGINE", default = "GRAPHHOPPER")
val graphHopperApiKey = localProperty("GRAPHHOPPER_API_KEY")
val stadiaApiKey = localProperty("STADIA_API_KEY")
val valhallaBaseUrl = localProperty("VALHALLA_BASE_URL")

android {
    namespace = "com.example.pion.family.tracker.demo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.pion.family.tracker.demo"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // F5/US-33, PRD v1.2 §6 — deliberately NOT `= BuildConfig.DEBUG.toString()`. The demo
        // build is `release` (plan.md decision #2), and the simulate button is P0: tying it to
        // DEBUG would hide it from the exact APK that ships to the demo. Declared once in
        // `defaultConfig` (not per-buildType) so it applies identically to BOTH variants —
        // see LLM.md §13 Open row if anyone "cleans this up" back to BuildConfig.DEBUG.
        buildConfigField("boolean", "SIMULATOR_ENABLED", "true")

        // Routing plan phase-01 Step 10/11 — `RoutingEngine.valueOf(BuildConfig.ROUTING_ENGINE)`
        // (FamilyTrackerApp.appConfigModule) throws on a typo'd engine name. That is deliberate:
        // a misconfigured build must fail loudly at startup, not silently fall back to the wrong
        // provider through an entire demo.
        buildConfigField("String", "ROUTING_ENGINE", "\"$routingEngine\"")
        buildConfigField("String", "GRAPHHOPPER_API_KEY", "\"$graphHopperApiKey\"")
        buildConfigField("String", "STADIA_API_KEY", "\"$stadiaApiKey\"")
        buildConfigField("String", "VALHALLA_BASE_URL", "\"$valhallaBaseUrl\"")
    }

    signingConfigs {
        // Reuses the debug keystore so the SHA-1 does not change between variants — one Maps
        // API key restriction then covers both. Demo-only choice, not for real release signing.
        // See LLM.md §10, PRD v1.2 §7.2.
        create("demo") {
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("demo")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.koin.android)
    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}