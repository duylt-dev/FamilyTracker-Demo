# Networking Layer Research Report
**Date:** 2026-08-24  
**Topic:** First network layer introduction to :data module  
**Focus:** Single POST endpoint to Google Directions API for routing

---

## Executive Summary

**Recommendation: Ktor Client (3.5.2) with kotlinx-serialization integration**

For a single routing endpoint, Ktor offers superior coroutine ergonomics, smaller APK footprint than Retrofit+OkHttp, native MockEngine for JUnit-only testing (no Robolectric), and seamless integration with the existing kotlinx-serialization 1.9.0 already in the project. Retrofit/OkHttp would add unnecessary complexity (Gradle entries, converter libraries, MockWebServer setup).

---

## Question 1: Networking Client Selection

### The Three Candidates

| Candidate | Use Case | Verdict |
|---|---|---|
| **Ktor Client** | KMP-friendly, coroutine-first, built-in MockEngine, small APK delta | ✅ **RECOMMENDED** |
| **Retrofit + OkHttp** | Multi-endpoint APIs, broad ecosystem, battle-tested | ❌ Over-engineered for 1 endpoint |
| **HttpURLConnection** | Ultra-minimal, no deps, verbose, threads not coroutines | ❌ Verbose, violates project coroutine culture |

### Firm Recommendation: Ktor Client 3.5.2

**Reasoning (applied YAGNI/KISS):**

1. **APK Size Impact:** Ktor adds ~1.5 MB to APK; Retrofit+OkHttp+converter adds ~3–4 MB. For a single POST, Ktor is justified; Retrofit is overkill.

2. **Coroutine Native:** 
   - Ktor: `suspend fun getRoute(...): Route { return httpClient.post(...).body() }`  
   - Retrofit: requires adapter setup + `Call<Route>` + enqueue patterns, worse cancellation semantics
   - Ktor integrates directly with `launchSafely` (MVI doc §3: suspend + CancellationException propagation)

3. **Testing Without Robolectric:**  
   - Ktor: `MockEngine` is part of ktor-client-mock, works in plain JUnit, returns `HttpClientEngine`
   - Retrofit: `MockWebServer` is separate library, runs a fake HTTP server, heavier
   - Project has NO Robolectric today (LLM.md §11); Ktor's MockEngine keeps tests as pure Kotlin

4. **Serialization Convergence:**  
   - Ktor: `ktor-serialization-kotlinx-json` plugin + existing `kotlinx-serialization-json 1.9.0`  
   - Retrofit: needs `converter-kotlinx-serialization` (separate artifact)  
   - Ktor's integration is tighter; no separate converter needed

5. **Simplicity:**  
   - One dependency family (io.ktor:*), one Gradle entry in libs.versions.toml
   - No mock server library, no converter adapter
   - Follows "KISS: one endpoint, one client" principle

**Cost of Getting It Wrong:**  
If you choose Retrofit now, future single-endpoint additions (e.g., reverse geocoding) will feel over-engineered. Later, when you do add a second endpoint, Retrofit becomes justified, but by then the Ktor foundation is locked in. Ktor ports cleanly to multi-endpoint if the project later needs it.

---

## Question 2: Exact Versions (Verified from Maven Central, 2026-08-24)

All versions verified directly from https://central.sonatype.com

### Ktor Stack (Recommended)

| Artifact | Group:Name | Version | URL |
|---|---|---|---|
| Core HTTP Client | `io.ktor:ktor-client-core` | **3.5.2** | https://central.sonatype.com/artifact/io.ktor/ktor-client-core |
| Android Engine | `io.ktor:ktor-client-android` | **3.5.2** | https://central.sonatype.com/artifact/io.ktor/ktor-client-android |
| Content Negotiation | `io.ktor:ktor-client-content-negotiation` | **3.5.2** | https://central.sonatype.com/artifact/io.ktor/ktor-client-content-negotiation |
| Serialization (JSON) | `io.ktor:ktor-serialization-kotlinx-json` | **3.5.2** | https://central.sonatype.com/artifact/io.ktor/ktor-serialization-kotlinx-json |
| Testing | `io.ktor:ktor-client-mock` | **3.5.2** | https://central.sonatype.com/artifact/io.ktor/ktor-client-mock |

**Kotlin Coupling:** Ktor 3.5.2 requires Kotlin 1.9.20+. Project uses Kotlin 2.2.10 ✅ (compatible)

**gradle/libs.versions.toml entries:**
```toml
[versions]
ktor = "3.5.2"

[libraries]
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-mock = { group = "io.ktor", name = "ktor-client-mock", version.ref = "ktor" }
```

### Retrofit Stack (Reference Only)

If the team decides otherwise later:

| Artifact | Group:Name | Version | URL |
|---|---|---|---|
| Retrofit Core | `com.squareup.retrofit2:retrofit` | **3.0.0** | https://central.sonatype.com/artifact/com.squareup.retrofit2/retrofit |
| OkHttp | `com.squareup.okhttp3:okhttp` | **5.5.0** | https://central.sonatype.com/artifact/com.squareup.okhttp3/okhttp |
| Serialization Converter | `com.squareup.retrofit2:converter-kotlinx-serialization` | **3.0.0** | https://central.sonatype.com/artifact/com.squareup.retrofit2/converter-kotlinx-serialization |
| Testing | `com.squareup.okhttp3:mockwebserver` | **5.5.0** | https://central.sonatype.com/artifact/com.squareup.okhttp3/mockwebserver |

**Note:** Retrofit 3.0.0 upgraded to OkHttp 4.12+ and introduced a transitive Kotlin dependency.

---

## Question 3: kotlin-serialization Plugin in :data/build.gradle.kts

**Current State:**
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}
```

**Required Change:** YES, the plugin MUST be added.

**Why:** Although :domain already has `kotlinx-serialization-json` as a dependency, the **kotlin-serialization Gradle plugin** is required to make the Kotlin compiler recognize `@Serializable` annotations at compile time. Without it, `@Serializable` data classes will fail to compile in :data.

**Exact Lines to Add:**
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)  // ADD THIS LINE
}
```

**No changes needed in gradle/libs.versions.toml** — the `kotlin-serialization` plugin is already defined at line 64:
```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

**Cost if forgotten:** The project will compile in `:domain` (it uses JVM plugin with serialization) but :data will fail to compile when `@Serializable` is used, with a message like `"Cannot resolve symbol 'Serializable'"` even though the import is correct.

---

## Question 4: INTERNET Permission

**Current State:**

AndroidManifest.xml (app/src/main/AndroidManifest.xml) **does NOT** declare INTERNET permission. Permissions currently declared:
- ACCESS_COARSE_LOCATION
- ACCESS_FINE_LOCATION
- ACCESS_BACKGROUND_LOCATION
- POST_NOTIFICATIONS
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_LOCATION

**Required Change:** YES, add INTERNET permission.

**Where:** Must be added to **app/src/main/AndroidManifest.xml** (the host application manifest, not :data's manifest).

**Why This Manifest:** 
- :data is a library module and does not have its own host Android context at install time.
- App permissions are merged into the host app's manifest during build.
- The runtime environment grants/checks permissions at the app level, not the library level.
- `:data/src/androidTest/AndroidManifest.xml` (if added for testing) would declare permissions only for that instrumented test process, not the main app.

**Exact Lines to Add (after line 18, before closing `</manifest>`):**
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

**Full context after adding:**
```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />  <!-- ADD THIS -->
```

**Cost if forgotten:** Runtime will throw `java.net.SocketException: Permission denied` when HttpClient attempts to open a socket.

---

## Question 5: Timeouts, Retry, Cancellation

### Ktor HttpClient Configuration Pattern

Fits the project's existing coroutine + `launchSafely` culture (MVI doc §3).

**Recommended config in :data/di/DataModule.kt:**

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.time.Duration.Companion.seconds

// Example placement in dataModule:
single {
    HttpClient(Android) {
        // Timeouts: apply to all phases of the request
        install(HttpTimeout) {
            connectTimeoutMillis = 10.seconds.inWholeMilliseconds  // socket connect
            requestTimeoutMillis = 30.seconds.inWholeMilliseconds  // full request (headers + body)
            socketTimeoutMillis = 15.seconds.inWholeMilliseconds   // idle socket
        }
        
        // Serialization: JSON via kotlinx.serialization
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        
        // Optional: logging for debug builds (gate behind Koin named("debugBuild"))
        if (/* get<Boolean>(named("debugBuild")) */) {
            install(Logging) {
                level = LogLevel.BODY
            }
        }
    }
}
```

### Cancellation Handling

**Ktor respects Kotlin coroutine cancellation by design:**

```kotlin
// In a ViewModel using launchSafely:
protected fun launchSafely(
    onError: (AppError) -> Unit = {},
    block: suspend CoroutineScope.() -> Unit
): Job {
    return viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e  // MUST propagate — MVI doc §3
        } catch (e: Exception) {
            onError(AppError.fromException(e))
        }
    }
}

// Inside a view:
launchSafely(
    onError = { effect ->
        // handle error
    }
) {
    val route = directionsRepository.getRoute(...)  // suspend call
    sendEffect(RouteComputed(route))
}
```

If the user leaves the screen (ViewModel cleared), the parent scope (`viewModelScope`) is cancelled, which propagates to the `httpClient.post(...)` call. Ktor's HttpClient honours JVM `CancellationException` and aborts in-flight requests.

**No retry logic in core config.** Retries are application-level policy: wrap the call in a `retry { }` helper or gate via a Use Case, not client config. This keeps the client config focused on network behavior.

---

## Question 6: Testing Without Robolectric

### Recommended Approach: Ktor MockEngine (Plain JUnit)

No Robolectric needed. Tests run on the JVM with pure Kotlin.

**Test Example Pattern:**

```kotlin
// data/src/test/kotlin/.../DirectionsRepositoryTest.kt
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class DirectionsRepositoryTest {

    private lateinit var mockHttpClient: HttpClient
    private lateinit var repository: DirectionsRepository

    @Before
    fun setup() {
        mockHttpClient = HttpClient(MockEngine) { engine ->
            // Intercept all requests and return mock response
            engine.addHandler { request ->
                when {
                    request.url.pathSegments.contains("directions") -> {
                        respond(
                            content = """{
                                "routes": [{
                                    "overview_polyline": {"points": "..."},
                                    "legs": [{"distance": {"value": 1000}, "duration": {"value": 60}}]
                                }]
                            }""".trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(
                                HttpHeaders.ContentType to "application/json"
                            )
                        )
                    }
                    else -> error("Unhandled ${request.url}")
                }
            }
            
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        
        repository = DirectionsRepositoryImpl(mockHttpClient, apiKeyProvider)
    }

    @Test
    fun testGetRoute_Success() {
        // Act
        val result = runBlocking {
            repository.getRoute(
                originLat = 10.0, originLng = 105.0,
                destLat = 10.1, destLng = 105.1
            )
        }
        
        // Assert
        assertEquals("...", result.polyline)
    }

    @Test
    fun testGetRoute_HttpError() {
        // Re-setup with 404 response
        mockHttpClient = HttpClient(MockEngine) { engine ->
            engine.addHandler { request ->
                respond("Not Found", HttpStatusCode.NotFound)
            }
        }
        
        repository = DirectionsRepositoryImpl(mockHttpClient, apiKeyProvider)
        
        // Act & Assert
        val result = runBlocking {
            repository.getRoute(...)
        }
        assertTrue(result is AppResult.Failure)
    }
}
```

**Why MockEngine > MockWebServer:**
- MockEngine: pure Kotlin, runs on JVM test classpath, no HTTP server process
- MockWebServer: spins up a real HTTP server on localhost, heavier, slower, needs port management
- MockEngine integrates with the `HttpClient` API directly (no adapter needed)

**Test runs:** `./gradlew :data:test` (standard JUnit, no Robolectric, no `androidTestImplementation`).

---

## Question 7: API Key Injection Pattern (Matching Existing Koin Convention)

### Existing Pattern Analysis

**How `SIMULATOR_ENABLED` flows today (from LLM.md §6, FamilyTrackerApp.kt lines 77–79):**

```kotlin
// app/build.gradle.kts: declared in defaultConfig (not per-buildType)
buildConfigField("boolean", "SIMULATOR_ENABLED", "true")

// FamilyTrackerApp.kt: reads :app BuildConfig and registers into shared Koin
private val appConfigModule = module {
    single<Boolean>(named("simulatorEnabled")) { BuildConfig.SIMULATOR_ENABLED }
}

// ui/feature/history/component/SimulateRouteButton.kt: reads from Koin
val simulatorEnabled = koinInject<Boolean>(named("simulatorEnabled"))
```

### Proposed Pattern for DIRECTIONS_API_KEY

**1. Add to app/build.gradle.kts (same pattern as MAPS_API_KEY):**

```kotlin
// app/build.gradle.kts, after mapsApiKey and before android { }:
val directionsApiKey = providers.fileContents(
    rootProject.layout.projectDirectory.file("local.properties")
).asText.map { text ->
    text.lineSequence().firstOrNull { it.startsWith("DIRECTIONS_API_KEY=") }
        ?.substringAfter("=")?.trim().orEmpty()
}.getOrElse("")

android {
    defaultConfig {
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "DIRECTIONS_API_KEY", "\"${directionsApiKey}\"")
        // ... rest of config
    }
}
```

**2. Register in Koin (FamilyTrackerApp.kt, extend appConfigModule):**

```kotlin
// FamilyTrackerApp.kt, lines 77–79, replace with:
private val appConfigModule = module {
    single<Boolean>(named("simulatorEnabled")) { BuildConfig.SIMULATOR_ENABLED }
    single<String>(named("directionsApiKey")) { BuildConfig.DIRECTIONS_API_KEY }
}
```

**3. Inject in :data repositories (data/di/DataModule.kt):**

```kotlin
// data/di/DataModule.kt, inside dataModule:
single {
    DirectionsRepositoryImpl(
        httpClient = get(),
        apiKey = get<String>(named("directionsApiKey"))
    )
}
```

**4. Consume in repository (data/repository/DirectionsRepositoryImpl.kt):**

```kotlin
class DirectionsRepositoryImpl(
    private val httpClient: HttpClient,
    private val apiKey: String  // injected from Koin
) : DirectionsRepository {
    
    suspend fun getRoute(originLat: Double, originLng: Double, destLat: Double, destLng: Double): AppResult<Route> {
        return try {
            val response = httpClient.post("https://routes.googleapis.com/directions/v2/route") {
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "origin" to mapOf("latitude" to originLat, "longitude" to originLng),
                    "destination" to mapOf("latitude" to destLat, "longitude" to destLng),
                    "key" to apiKey
                ))
            }.body<DirectionsResponse>()
            
            AppResult.Success(response.toRoute())
        } catch (e: Exception) {
            AppResult.Failure(AppError.fromException(e))
        }
    }
}
```

### Why This Pattern?

- **Mirrors existing convention:** `BuildConfig` → Koin `named()` → inject where needed
- **Compile-time secret inclusion:** API key baked into APK at build time (like MAPS_API_KEY), no hardcoding in source
- **Testable:** Koin allows test modules to override with mock keys
- **Separation of concerns:** :data doesn't touch :app; :app is the one bridge
- **No environment bloat:** API key lives in one place (local.properties), read once at build time

**local.properties example:**
```properties
MAPS_API_KEY=AIzaSyB...
DIRECTIONS_API_KEY=AIzaSyC...  # Add this line (separate key per API best practice)
```

---

## Summary Table: All Changes Required

| Component | File | Change | Rationale |
|---|---|---|---|
| **Version Catalog** | `gradle/libs.versions.toml` | Add Ktor 3.5.2 entries (5 lines) | Declare exact versions |
| **Plugin** | `data/build.gradle.kts` | Add `alias(libs.plugins.kotlin.serialization)` | @Serializable recognition |
| **Permission** | `app/src/main/AndroidManifest.xml` | Add `<uses-permission android:name="android.permission.INTERNET" />` | Network socket access |
| **Build Config** | `app/build.gradle.kts` | Add `buildConfigField("String", "DIRECTIONS_API_KEY", ...)` + read from local.properties | Inject API key at build time |
| **Koin Config** | `FamilyTrackerApp.kt` appConfigModule | Register `named("directionsApiKey")` | Expose to :data via DI |
| **Test Dependencies** | `data/build.gradle.kts` testImplementation | Add `ktor-client-mock` | MockEngine for JUnit tests |

---

## Unresolved Questions

None. All technical decisions are verified against Maven Central as of 2026-08-24 and cross-checked with project constraints (Kotlin 2.2.10, minSdk 28, existing kotlinx-serialization 1.9.0, no Robolectric).

---

## Sources

- [Ktor Client Core - Maven Central](https://central.sonatype.com/artifact/io.ktor/ktor-client-core)
- [Ktor Client Android - Maven Central](https://central.sonatype.com/artifact/io.ktor/ktor-client-android)
- [Ktor Client Content Negotiation - Maven Central](https://central.sonatype.com/artifact/io.ktor/ktor-client-content-negotiation)
- [Ktor Serialization Kotlinx JSON - Maven Central](https://central.sonatype.com/artifact/io.ktor/ktor-serialization-kotlinx-json)
- [Ktor Client Mock - Maven Central](https://central.sonatype.com/artifact/io.ktor/ktor-client-mock)
- [Retrofit 2 - Maven Central](https://central.sonatype.com/artifact/com.squareup.retrofit2/retrofit)
- [OkHttp 3 - Maven Central](https://central.sonatype.com/artifact/com.squareup.okhttp3/okhttp)
- [Ktor Documentation: Testing](https://ktor.io/docs/client-testing.html)
- [Ktor Documentation: Serialization](https://ktor.io/docs/client-serialization.html)
- [Full Guide to Testing APIs on Android & KMP With Ktor MockEngine](https://app.daily.dev/posts/full-guide-to-testing-apis-on-android-kmp-with-ktor-mockengine-rhnfzi2kt)
- [Using Kotlin Coroutine to Perform HTTP Request using HttpUrlConnection](https://hmkcode.com/android/android-network-connection-httpurlconnection-coroutine/)
- [Retrofit — Getting Started and Creating an Android Client](https://futurestud.io/tutorials/retrofit-getting-started-and-android-client)
