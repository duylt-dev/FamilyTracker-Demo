# Codebase Integration Map — Real-time Navigation to Tracked Member
## researcher-05 — 2026-08-24

---

## 1. MVI CONTRACT SPECIFICATION

### Base Class Signature

Quoted from `ui/src/main/java/core/mvi/MviViewModel.kt`:

```kotlin
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
    private val logger: AppLogger = NoopAppLogger,
) : ViewModel() {
    val state: StateFlow<S> = _state.asStateFlow()
    val effects = _effects.receiveAsFlow()
    protected val currentState: S get() = _state.value
    abstract fun onIntent(intent: I)
    protected fun setState(reducer: S.() -> S) = _state.update { it.reducer() }
    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }
    }
    protected fun launchSafely(
        onError: (AppError) -> Unit = {},
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch { try { block() } catch (cancellation: CancellationException) { throw cancellation } catch (throwable: Throwable) { … } }
    protected fun <T> Flow<T>.collectSafely(
        onError: (AppError) -> Unit = {},
        onEach: suspend (T) -> Unit,
    ): Job = launchSafely(onError) { collect { onEach(it) } }
}
```

**Key rules:**
- `onIntent(I)` is the **only** public method
- State/Intent/Effect live in `XContract.kt` (never inline in ViewModel file)
- Every coroutine goes through `launchSafely`; `CancellationException` must be rethrown
- Effects use `Channel(BUFFERED)`, never `SharedFlow`
- For Flow observation in `init`, use `collectSafely`, not `.launchIn(viewModelScope)`

### Effect Collection Pattern

Quoted from `ui/src/main/java/core/mvi/CollectEffects.kt`:

```kotlin
@Composable
fun <E : UiEffect> CollectEffects(effects: Flow<E>, onEffect: suspend (E) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { handler(it) }
        }
    }
}
```

**Key rules:**
- Use `collect`, never `collectLatest` (cancels in-flight navigation)
- Lifecycle-aware via `repeatOnLifecycle(STARTED)`
- Every declared Effect must have a collector branch, or Channel fills and deadlocks the feature

### Stateful Route + Stateless Screen Pattern

Quoted from `ui/feature/history/HistoryScreen.kt`:

```kotlin
@Composable
fun HistoryRoute(
    onOpenHistory: (epochDay) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HistoryEffect.ShowError -> { /* handle */ }
            is HistoryEffect.FocusCamera -> { /* handle */ }
        }
    }
    HistoryScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
private fun HistoryScreen(
    state: HistoryState,
    onIntent: (HistoryIntent) -> Unit,
    modifier: Modifier = Modifier,
) { /* stateless composable — never touches ViewModel */ }
```

**Key rules:**
- `XRoute` is public and stateful (injects ViewModel)
- `XScreen` is private and stateless (takes only state + onIntent)
- Effects collected only in Route
- No composables below Route see the ViewModel

---

## 2. NEW FILES REQUIRED

### Domain Layer (`:domain`)

| Path | Purpose | Size Est. |
|---|---|---|
| `domain/model/RoutingRequest.kt` | `memberId`, `startLat/Lng`, `destLat/Lng`, `timestamp` + computed `destinationChanged(other)`, `followerDeviated(fromPolyline, threshold)` | ~40 lines |
| `domain/model/NavigationRoute.kt` | Polyline points, ETA, distance, status (IDLE/COMPUTING/REROUTING) | ~30 lines |
| `domain/model/NavigationError.kt` | Sealed type: `DIRECTIONS_API_ERROR(code, message)`, `INVALID_ROUTE`, `NO_ROUTE_FOUND`, `REROUTE_THRESHOLD_BREACH` | ~25 lines |
| `domain/tracking/RouteGeometry.kt` | Pure algorithm file: `pointToSegmentDistance()`, `polylineWithinThreshold()`, `computeRemainingDistance()` — all Haversine-based | ~80 lines |
| `domain/repository/NavigationRepository.kt` | Interface: `computeRoute(from, to): Flow<AppResult<NavigationRoute>>`, `observeRerouteThresholds(): Flow<RerouteConfig>` | ~25 lines |
| `domain/usecase/ComputeRouteUseCase.kt` | `invoke(memberId, targetLocation)`: queries API, maps DTO to domain model, handles cache-invalidation on reroute triggers | ~50 lines |
| `domain/usecase/ObserveNavigationStateUseCase.kt` | Combines: current follower position, target member position, current route → emits `NavigationState(route, shouldReroute)` to decision trigger | ~60 lines |

**Total domain files: 7, ~310 lines**

### Data Layer (`:data`)

| Path | Purpose | Size Est. |
|---|---|---|
| `data/remote/dto/DirectionsApiResponse.kt` | DTO: `routes[0].legs[0].steps/polyline`, `duration_in_traffic`, `distance` — maps from Google Directions API JSON | ~50 lines |
| `data/remote/datasource/DirectionsApiDataSource.kt` | Encapsulates API call + retry + error parsing; does NOT handle caching or route invalidation | ~70 lines |
| `data/mapper/NavigationRouteMapper.kt` | DTO→ domain model; `polyline` string decode to lat/lng points via `PolylineUtils.decode()` (maps-compose-utils) | ~40 lines |
| `data/repository/NavigationRepositoryImpl.kt` | Implements `NavigationRepository` interface; calls `DirectionsApiDataSource`, caches **route points only** (invalidated on reroute signal), uses `MutableStateFlow` for reroute config | ~90 lines |
| `data/location/RerouteDetector.kt` | Stateless utility: given (currentRoute, followerPos, targetPos, lastComputedAt), returns `RerouteReason` or null — no coroutines | ~80 lines |
| `data/di/NavigationModule.kt` | Koin bindings: `single<DirectionsApiDataSource>`, `single<NavigationRepository>`, `factory<ComputeRouteUseCase>`, `factory<ObserveNavigationStateUseCase>` | ~35 lines |
| (Already exists) `data/di/DataModule.kt` | **ADD:** wire `NavigationModule` into `startKoin` `:app/FamilyTrackerApp.kt` | ~ +2 lines |

**Total data files: 6 new + modify 1, ~365 lines + 2 modifications**

### UI Layer (`:ui`)

| Path | Purpose | Size Est. |
|---|---|---|
| `ui/feature/navigation/NavigationContract.kt` | **State:** `route: NavigationRoute?`, `targetMemberId`, `targetLocation`, `isComputing`, `shouldShowReroute`, `error` (all computed `val`s where possible). **Intent:** `DestinationReached`, `RerouteDetected`, `ComputeRoute(memberId)`, `DismissError`. **Effect:** `NavigateTo(memberId)` (from Map tap), `ShowRerouteIndicator`, `ShowError(AppError)` | ~70 lines |
| `ui/feature/navigation/NavigationViewModel.kt` | ViewModel binding: `ComputeRouteUseCase`, `ObserveNavigationStateUseCase`. Manages reroute debouncing (min-interval timer), coordinate updates from tracked member flow, follower position from `TrackingRepository`. `onIntent` branches: `ComputeRoute` (cancel-replace job), `RerouteDetected` (increment debounce counter), lifecycle management | ~110 lines |
| `ui/feature/navigation/NavigationScreen.kt` | Stateful `NavigationRoute` + stateless `NavigationScreen`. Route: handle `NavigateTo` effect. Screen: bottom sheet or full screen showing polyline on map, ETA, distance remaining, reroute indicator. **Does NOT draw map itself** — reuses `FamilyTrackerMap` component from `:ui/feature/map/component/` (parent hosts it) | ~140 lines |
| `ui/feature/navigation/component/NavigationPolyline.kt` | `@GoogleMapComposable`: draws route polyline (color: primary blue), line width 6dp. Similar to `RoutePolyline.kt` but for **live** route, not history | ~50 lines |
| `ui/feature/navigation/component/NavigationStats.kt` | Shows ETA text, distance remaining, current speed (if available from `TrackingRepository.currentLocation`) — read-only composable, no state mutation | ~45 lines |
| `ui/feature/navigation/component/RerouteIndicator.kt` | Animated badge "Rerouting…" with spinner, appears when `state.shouldShowReroute = true`, positioned top-right of bottom sheet | ~40 lines |
| `ui/feature/map/component/MemberMarkers.kt` | **MODIFY:** existing file — add long-press context menu or tap affordance: "Chi đường" (Navigate) button that sends `MemberTapped(memberId)` up + raises new intent `RequestNavigation(memberId)` in MapIntent sealed interface | ~+15 lines |
| `ui/navigation/Routes.kt` | **ADD:** `@Serializable data class NavigationRoute(val memberId: String, val targetLat: Double, val targetLng: Double)` — route args passed from Map screen | ~10 lines |
| `ui/navigation/FamilyTrackerNavHost.kt` | **ADD:** wire new `NavigationRoute` destination into NavHost composable; add back-stack entry | ~15 lines |
| `ui/di/UiModule.kt` | **ADD:** `viewModelOf(::NavigationViewModel)`, `factoryOf(::ComputeRouteUseCase)`, `factoryOf(::ObserveNavigationStateUseCase)` | ~3 lines |
| `ui/designsystem/component/FamilyTrackerBottomBar.kt` | **NO CHANGE:** Navigation is **not a 5th tab**. It's an **overlay/modal** reachable from Map (member tap) or Timeline (not yet, stretch). Current 4 tabs stay as-is. | 0 lines |

**Total UI files: 7 new + modify 2, ~513 lines + modifications**

---

## 3. EXISTING FILES TO MODIFY

### Gradle & Build

| File | Change | Reason | Quote Context |
|---|---|---|---|
| `gradle/libs.versions.toml` | **ADD** version alias: `googleDirectionsApi = "1.3.9"` (or latest compatible with Kotlin 2.2.10) | Google's Directions API client library (or use REST only with Retrofit, see below). **UNVERIFIED — require verification of actual version available for Kotlin 2.2.10** | If using REST: no version needed. If using SDK: https://developers.google.com/maps/documentation/directions/requests |
| `data/build.gradle.kts` | **ADD** dependency for routing. Two options: (A) **HTTP Client only** (simpler): no new dependency — use `kotlinx.coroutines.http` or existing Retrofit setup — **BUT VERIFY: does project have Retrofit?** Reading codebase shows **NO Retrofit, NO OkHttp anywhere**. (B) **Google Directions API SDK**: `implementation(libs.googleDirectionsApi)`. **MUST DECIDE: REST client + serialization, or SDK?** | Feature requires HTTP access to Google Directions API (or Routes API v2, which requires gRPC or HTTP). Project has **ZERO network layer**. Phase plan MUST specify: REST (URLConnection + kotlinx-serialization) vs SDK (heavyweight) vs third-party (Ktor) | See §5 below |
| `app/build.gradle.kts` | **NO CHANGE** — Maps API key already in manifest; Directions API uses same key | | |
| `app/src/main/AndroidManifest.xml` | **NO CHANGE** — `INTERNET` permission already granted for Maps; Directions API uses same permission | | |

### Domain Tracking Constants

| File | Change | Reason | Quote Context |
|---|---|---|---|
| `domain/tracking/TrackingConstants.kt` | **ADD three new constants:** `REROUTE_TARGET_THRESHOLD_M = 100.0` (target moved >100m from destination), `REROUTE_FOLLOWER_THRESHOLD_M = 50.0` (follower deviated >50m from route), `REROUTE_MIN_INTERVAL_MS = 10_000L` (minimum 10s between reroute attempts) | Per PRD §6 extension for routing. Thresholds drive reroute logic; interval prevents churning. All three in one file per QA requirement (LLM.md §8.8) | "Every tracking threshold lives in exactly one file: domain/tracking/TrackingConstants.kt" |

### Map Feature (Entry Point)

| File | Change | Reason | Quote Context |
|---|---|---|---|
| `ui/feature/map/MapContract.kt` | **MODIFY `MapIntent`:** add `data class RequestNavigation(val memberId: String): MapIntent` after existing member-related intents | User taps "Chi đường" button on member marker; intent flows up to ViewModel | Existing: `data class MemberTapped(val memberId: String) : MapIntent` — new intent distinguishes "marker tapped" from "navigate requested" |
| `ui/feature/map/MapViewModel.kt` | **MODIFY `onIntent`:** add branch `is MapIntent.RequestNavigation -> sendEffect(MapEffect.OpenNavigation(intent.memberId))` | Route user to Navigation screen when requesting directions | Existing: `is MapIntent.MemberTapped -> setState { copy(selectedMemberId = intent.memberId) }` |
| `ui/feature/map/MapContract.kt` | **MODIFY `MapEffect`:** add `data class OpenNavigation(val memberId: String): MapEffect` | Signal Route to navigate to Navigation screen | Existing effects: `OpenZoneEditor`, `OpenZoneList`, `OpenHistory`, `OpenTimeline`, `ShowError` |
| `ui/feature/map/MapScreen.kt` | **MODIFY effect collector:** add branch `is MapEffect.OpenNavigation -> onOpenNavigation(effect.memberId)` and pass `onOpenNavigation: (memberId: String) -> Unit` down from Route | Route composable receives `onOpenNavigation` callback and calls `navController.navigate(NavigationRoute(memberId, targetLat, targetLng))` | Existing pattern: `MapRoute` parameter `onOpenZoneEditor: (lat, lng) -> Unit` |
| `ui/feature/map/component/MemberMarkers.kt` | **MODIFY composable signature:** add `onNavigationRequested: (memberId: String) -> Unit` parameter after `onMemberTapped`. In `onClick`: after `onMemberTapped(member.id)`, show context menu or button "Chi đường" that calls `onNavigationRequested(member.id)` | Affordance for user to start navigation instead of just selecting marker | Existing: `onClick = { onMemberTapped(member.id); false }` — `false` lets info window still show |

**Context quote for MemberMarkers.kt change:**
```kotlin
// Current:
onClick = { onMemberTapped(member.id); false }

// Modified:
onClick = { 
    onMemberTapped(member.id)
    onNavigationRequested(member.id)  // ADD THIS
    false  // still let info window appear
}
```

### Routes & Navigation Host

| File | Change | Reason | Quote Context |
|---|---|---|---|
| `ui/navigation/Routes.kt` | **ADD new route:**<br>`@Serializable`<br>`data class NavigationRoute(`<br>&nbsp;&nbsp;`val memberId: String,`<br>&nbsp;&nbsp;`val targetLat: Double? = null,`<br>&nbsp;&nbsp;`val targetLng: Double? = null,`<br>`)` | Type-safe route to Navigation screen; args populated from Map when user requests nav to a member | Existing routes: `MapRoute`, `ZoneListRoute`, `HistoryRoute(epochDay, focusLat, focusLng)` |
| `ui/navigation/FamilyTrackerNavHost.kt` | **ADD composable entry:** after `composable<TimelineRoute>` block:<br>`composable<NavigationRoute> { backStackEntry ->`<br>&nbsp;&nbsp;`NavigationRoute(…)  // Route composable from feature`<br>`}` | Wire route into NavHost so navigation works | Existing pattern visible in file; each route adds one `composable<X>` block |

### DI Configuration

| File | Change | Reason | Quote Context |
|---|---|---|---|
| `ui/di/UiModule.kt` | **ADD three lines:**<br>`viewModelOf(::NavigationViewModel)`<br>`factoryOf(::ComputeRouteUseCase)`<br>`factoryOf(::ObserveNavigationStateUseCase)` | Register new ViewModel and use cases; `viewModelOf` resolves constructor params via Koin | Existing pattern: `viewModelOf(::HistoryViewModel)`, `factoryOf(::ObserveRouteForDayUseCase)` |
| `data/di/DataModule.kt` | **ADD:** import `NavigationModule` and include in the data module chain (or create new file `NavigationModule.kt` and wire it) | Register data-layer bindings (API datasource, repository impl) | Existing: `startKoin { modules(appModule, uiModule, dataModule, databaseModule) }` in `FamilyTrackerApp.kt` |
| `app/FamilyTrackerApp.kt` | **MODIFY `startKoin` block** (if `NavigationModule` is separate): add `navigationModule` to `modules(…)` list | Wire new navigation module into Koin graph | Existing line: `modules(appModule, uiModule, dataModule, databaseModule)` in `onCreate` |

---

## 4. ENTRY POINT DECISION & COST ANALYSIS

### Current State: MapContract.kt

```kotlin
data class MapIntent : UiIntent {
    data class MemberTapped(val memberId: String) : MapIntent  // US-08
    // ^ stores selectedMemberId for info window display
}

sealed interface MapEffect : UiEffect {
    data object OpenZoneList : MapEffect
    data object OpenHistory : MapEffect
    data object OpenTimeline : MapEffect
    data class ShowError(val error: AppError) : MapEffect
}
```

### Proposed Minimal Change: Add Navigation Affordance

**Option A: Info Window Button (Minimal Cost)**
- When `MemberMarkers.onClick` fires, show standard Marker info window (name + timestamp via snippet).
- Add ONE button inside info window or **below marker popup**: "Chi đường" (navigate).
- Tap button → call new callback `onNavigationRequested(memberId)` → map sends new Intent `RequestNavigation(memberId)` → ViewModel raises Effect `OpenNavigation(memberId)` → Route navigates.
- **Cost:** +1 Intent type, +1 Effect type, +1 callback parameter on `MemberMarkers`, +1 branch in `MapViewModel.onIntent`, +1 branch in `MapScreen.effects.collect`.
- **Benefit:** Reuses existing marker selection UI; familiar interaction pattern (info window → action button).

**Option B: Long-Press Menu (Moderate Cost)**
- Add context menu on long-press: "Chi đường" / "Xem vị trí" options.
- Requires adding gesture detector to `FamilyTrackerMap` composable.
- **Cost:** ~+40 lines in Map screen composable, new Intent subtype, Effect subtype.
- **Benefit:** Doesn't clutter info window; clear visual separation of actions.

**Option C: Bottom Sheet from Selection (Moderate Cost)**
- When member tapped → show bottom sheet with member name, last location, "Chi đường" button.
- **Cost:** New composable (~50 lines), sheet state in MapState, new Intent/Effect.
- **Benefit:** More spacious UI for future expansion (share location, view history, etc.).

**RECOMMENDATION: Option A (Info Window Button)**
- **Why:** Smallest diff, leverages existing Maps SDK info window, no new gesture handling.
- **Cost breakdown:**
  - MapContract: +1 Intent, +1 Effect (~5 lines)
  - MemberMarkers: +1 callback parameter, +~10 line button/menu (~10 lines)
  - MapViewModel: +1 reducer branch (~3 lines)
  - MapScreen Route: +1 effect handler branch (~2 lines)
  - **Total:** ~20 lines across existing files, fully backward compatible.

---

## 5. FOLLOWER LIVE POSITION SOURCING

### The Hole: Navigation Screen Lifecycle vs Tracking State

**Problem statement:**
When user opens Navigation screen (not currently the Map screen), the app **must have live follower (self) position updates**. But if:
1. `LocationTrackingService` is **not running** (tracking toggle OFF), OR
2. GPS has **never acquired a lock yet** (first app launch, before any tracking),

then **there is no follower position at all** until tracking starts AND GPS returns a fix.

### Current Architecture

Reading `domain/usecase/ObserveMembersWithLastLocationUseCase.kt`:

```kotlin
operator fun invoke(): Flow<List<MemberLocation>> =
    combine(memberRepository.observeAll(), memberRepository.observeLatestLocations()) { members, locations ->
        members.map { member -> MemberLocation(member = member, lastLocation = locations[member.id]) }
    }
```

- `memberRepository.observeLatestLocations()` returns `Flow<Map<String, LocationPoint>>` — keyed by memberId.
- For **self**, this comes from `TrackingRepository.observeRoute(memberId=self, day=today)` → reads Room `location_points` table.
- Room writes are driven by `LocationTrackingService.trackingJob` → `FusedLocationSource.stream()` → `LocationPointProcessor`.

Reading `data/location/LocationTrackingService.kt`:

```kotlin
if (trackingJob == null) {
    trackingJob = scope.launch { collectFrom(fusedLocationSource) }
}

private suspend fun collectFrom(source: LocationSource) {
    source.stream().collect { point ->
        processor.process(point)  // writes to Room, processes zones for self
    }
}
```

### Answer: The Hole Exists, Must Be Addressed

**When Navigation screen opens:**
- **Best case (tracking ON + GPS fix exists):** `TrackingRepository.observeRoute(self, today)` emits live points every `LOCATION_INTERVAL_MS` (10s).
- **Worst case (tracking OFF or first launch):** `observeRoute()` emits empty list, map shows last-known position (if any) or nothing.

**What should happen:**
1. **At screen entry:** NavigationViewModel reads current follower location from `TrackingRepository.observeRoute(self, today)`.
2. **If empty:** Show degraded UI or error: "Tracking is off" or "Waiting for GPS fix" banner.
3. **If tracking is ON but GPS hasn't fixed yet:** Show banner "Acquiring location…" and wait (FusedLocationClient will eventually return).
4. **Once live position arrives:** Start updating route polyline in real-time.

**Implementation point:**
- `NavigationViewModel.init` must:
  ```kotlin
  init {
      // Get current target member's live location
      observeMembersWithLastLocation().collectSafely { locations ->
          val target = locations.firstOrNull { it.member.id == targetMemberId } ?: return@collectSafely
          setState { copy(targetLocation = target.lastLocation) }
      }
      
      // Get OWN live location — this is where tracking state matters
      trackingRepository.observeRoute(memberId=selfId, day=today).collectSafely { sessions ->
          if (sessions.isEmpty()) {
              setState { copy(error = AppError.Unexpected("No tracking data")) }
              return@collectSafely
          }
          val lastPoint = sessions.last().points.lastOrNull() ?: return@collectSafely
          setState { copy(followerLocation = lastPoint) }
      }
  }
  ```

**Unresolved:**
- **If navigation screen opens while tracking is OFF**, it must either:
  - (A) **Force tracking ON** (too aggressive),
  - (B) **Show error and block routing** (good UX),
  - (C) **Offer "Start tracking?" dialog** (best UX, but design-decision).
- **If GPS never locks** (indoor or no signal), `FusedLocationClient` will timeout and retry — this is framework behavior, not our responsibility. ViewModel should show spinner + "Acquiring location…".

**Recommended safeguard in NavigationViewModel:**
```kotlin
private fun onComputeRoute(memberId: String) {
    val selfLoc = currentState.followerLocation
    if (selfLoc == null) {
        setState { copy(error = AppError.Unexpected("Turn on tracking to use navigation")) }
        return
    }
    // else: proceed with API call
}
```

---

## 6. TARGET LIVE POSITION — UPDATE CADENCE

### Source: Tracked Member Position Flow

Reading `domain/repository/MemberRepository.kt`:

```kotlin
interface MemberRepository {
    fun observeLatestLocations(): Flow<Map<String, LocationPoint>>
    suspend fun recordLocation(memberId: String, point: LocationPoint)
}
```

Reading `data/location/MemberMovementSimulator.kt` (the only caller of `recordLocation`):

```kotlin
class MemberMovementSimulator(
    private val memberRepository: MemberRepository,
    …
) {
    init {
        launchSafely {
            while (isActive) {
                delay(MEMBER_ROAM_INTERVAL_MS)  // 2,500 ms (TrackingConstants)
                tickOnce()
            }
        }
    }
    
    private fun tickOnce() {
        val nextPosition = roamer.tick()  // advance Minh/Lan one step
        memberRepository.recordLocation(memberId, nextPosition)  // write to Room
    }
}
```

Reading `data/location/LocationTrackingService.kt`:

```kotlin
if (familyJob == null) familyJob = scope.launch { memberMovementSimulator.run() }
```

### Cadence

| Event | Interval | Triggered By |
|---|---|---|
| Target member position update | 2,500 ms (MEMBER_ROAM_INTERVAL_MS) | `MemberMovementSimulator.run()` loop in `LocationTrackingService` |
| Room write | 2,500 ms | `MemberRepository.recordLocation()` → DAO insert |
| Flow emission | ~2,500 ms + Room query time | Room `.observeLatestLocations()` combinator re-emits on every insert |
| Navigation screen observes | **2,500 ms** | `NavigationViewModel` collects `ObserveMembersWithLastLocationUseCase` flow |

### Stopping Condition

Reading the service again:

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
        stopSelf()
        return START_NOT_STICKY
    }
    if (familyJob == null) familyJob = scope.launch { memberMovementSimulator.run() }
    // familyJob NEVER cancelled, even when ACTION_SIMULATE runs — see LLM.md §3
    …
}

private suspend fun runSimulation(stopWhenDone: Boolean) {
    trackingJob?.cancelAndJoin()  // stop GPS tracking
    // familyJob continues — member movement NOT stopped by simulation
    …
}
```

**Stopping conditions for target updates:**
1. **Foreground service is stopped** via `ACTION_STOP` — service stops, `familyJob` cancelled with it.
2. **Never:** Toggling tracking OFF does NOT stop `familyJob`; member movement continues in background.
3. **Never:** Running simulation does NOT stop `familyJob`; only `trackingJob` (GPS) stops.

**Does it require foreground service running?**
- **YES:** `MemberMovementSimulator` runs inside `LocationTrackingService.familyJob` scope.
- If service dies, simulator dies.
- Simulator is NOT resumed by any manifest receiver (no `BootCompletedReceiver` for member movement — only old Geofence code had that).

**Implication for Navigation screen:**
- Navigation screen will have live target position updates **IF AND ONLY IF** `LocationTrackingService` is running.
- Service starts when user toggles tracking ON (via `MapViewModel.onIntent(ToggleTracking)`).
- Service MAY be already running when Navigation screen opens (normal case).
- **If service crashes or is killed by system**, target position stops updating (no position available → `observeLatestLocations` doesn't emit, or emits stale map).

---

## 7. TESTING CONVENTIONS — ACTUAL PATTERNS

### Domain Layer (Pure JVM)

**File:** `domain/src/test/kotlin/…/ZoneEvaluatorTest.kt`

```kotlin
class ZoneEvaluatorTest {
    @Test
    fun `entersAt exactly at the radius is NOT an entry, boundary is exclusive`() {
        assertEquals(false, ZoneEvaluator.entersAt(distanceMeters = 100.0, radiusMeters = 100.0))
        assertEquals(true, ZoneEvaluator.entersAt(distanceMeters = 99.999, radiusMeters = 100.0))
    }
}
```

**Patterns:**
- **Framework:** JUnit 4 (`@Test`, `@Before`, `@After`)
- **Assertions:** JUnit (`assertEquals`, `assertTrue`, `assertFalse`)
- **Mocking:** NONE — hand-written test data (pure algorithms don't need fakes; input/output verified directly)
- **Coroutines:** Not needed for pure algorithms (domain layer is JVM, no Android)

### UI Layer (ViewModel + Coroutines)

**File:** `ui/src/test/java/…/HistoryViewModelTest.kt`

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        savedStateHandle: SavedStateHandle = handle(),
        trackingRepository: TrackingRepository = HistoryFakeTrackingRepository(),
        memberRepository: MemberRepository = HistoryFakeMemberRepository(listOf(self), emptyMap()),
    ) = HistoryViewModel(
        savedStateHandle = savedStateHandle,
        observeRouteForDay = ObserveRouteForDayUseCase(trackingRepository),
        observeMembersWithLastLocation = ObserveMembersWithLastLocationUseCase(memberRepository),
        startSimulation = StartSimulationUseCase(…),
    )

    @Test
    fun `reducer test`() = runTest {
        val vm = viewModel()
        vm.onIntent(HistoryIntent.SelectDay(day))
        advanceUntilIdle()
        assertEquals(day, vm.state.value.selectedDay)
    }

    @Test
    fun `effect test`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(SomeIntent)
            advanceUntilIdle()
            assertTrue(awaitItem() is HistoryEffect.ShowError)
            expectNoEvents()
        }
    }
}
```

**Patterns:**
- **Framework:** JUnit 4 + `kotlinx.coroutines.test` (`StandardTestDispatcher`, `runTest`)
- **Flow testing:** `turbine` library (`effects.test { awaitItem() }`)
- **Mocking:** **Hand-written fakes**, NOT MockK or Mockito. Fakes have switchable failure modes:
  ```kotlin
  class HistoryFakeTrackingRepository : TrackingRepository {
      var throwOnRecognize = false
      override fun observeRoute(memberId: String, day: LocalDate) = 
          if (throwOnRecognize) throw Exception("fail") else flowOf(emptyList())
  }
  ```
- **Main dispatcher setup:** `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@Before`
- **Coroutine running:** `runTest { … }` block; `advanceUntilIdle()` to let all jobs finish
- **NO `advanceUntilIdle`** on ViewModels with `while (isActive)` loops — will hang

### Data Layer (Repository + Room)

**File:** `data/src/test/java/…/MemberMovementSimulatorTest.kt`

```kotlin
class MemberMovementSimulatorTest {
    @Test
    fun `a followed member walking into the zone raises ENTER then EXIT`() = runTest {
        val members = FakeMemberRepository(
            members = listOf(self, minh),
            locations = mapOf(minh.id to pointAt(northOf(10.0, 600.0), 106.0)),
        )
        val events = FakeZoneEventRepository()
        val simulator = MemberMovementSimulator(members, FakeZoneRepository(listOf(zone)), events)

        repeat(TICKS_FOR_A_FULL_CYCLE) { simulator.tickOnce() }

        assertTrue("member walked into zone", events.recorded.any { it.type == ENTER })
    }
}
```

**Patterns:**
- **Framework:** JUnit 4 + `kotlinx.coroutines.test`
- **Mocking:** Hand-written fakes with `FakeMemberRepository`, `FakeZoneEventRepository`
- **Coroutines:** `runTest` for async operations; direct mutation of fakes (no Flow mocking)
- **No Robolectric:** Direct JVM test, not Android instrumented test

### Required Test Structure for Navigation Feature

For new `NavigationViewModel`:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("memberId" to "m-minh")),
        computeRouteUseCase: ComputeRouteUseCase = FakeComputeRouteUseCase(),
        observeNavStateUseCase: ObserveNavigationStateUseCase = FakeObserveNavigationStateUseCase(),
    ) = NavigationViewModel(
        savedStateHandle = savedStateHandle,
        computeRouteUseCase = computeRouteUseCase,
        observeNavigationStateUseCase = observeNavStateUseCase,
    )

    @Test
    fun `initial state from savedStateHandle`() {
        val vm = viewModel(savedStateHandle(memberId = "m-minh"))
        assertEquals("m-minh", vm.state.value.targetMemberId)
    }

    @Test
    fun `compute route effect on ComputeRoute intent`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(NavigationIntent.ComputeRoute(memberId = "m-minh"))
            advanceUntilIdle()
            assertTrue(awaitItem() is NavigationEffect.NavigateTo)
            expectNoEvents()
        }
    }

    @Test
    fun `reroute debounce — multiple triggers within interval are coalesced`() = runTest {
        val vm = viewModel()
        vm.onIntent(NavigationIntent.RerouteDetected)
        vm.onIntent(NavigationIntent.RerouteDetected)
        vm.onIntent(NavigationIntent.RerouteDetected)
        advanceTimeBy(REROUTE_MIN_INTERVAL_MS - 1)
        assertFalse(vm.state.value.isRerouting)  // still debouncing
        advanceTimeBy(1)
        assertTrue(vm.state.value.isRerouting)  // now triggered
    }
}
```

**Key test families:**
1. **Reducers:** Intent → state change
2. **Effects:** Intent → Effect emitted exactly-once
3. **Crash containment:** fake throws → `error` field set, NOT exception propagated
4. **Debounce:** reroute triggers coalesced within `REROUTE_MIN_INTERVAL_MS`
5. **Flow observation:** live position updates from `ObserveNavigationStateUseCase` flow → state.followerLocation changes

---

## 8. KNOWN DEVIATIONS APPLICABLE TO THIS FEATURE

Reading LLM.md §13, the deviations that touch files this feature will edit:

| LLM.md §13 Row | Status | Applies Here? | File | Impact |
|---|---|---|---|---|
| Open #1 | OPEN | **NO** — involves Map screen's camera-on-every-frame, not routing | `ui/feature/map/component/ZoneCenterMap.kt` | Does not apply |
| Open #3 | (deleted in phase-11) | N/A | | |
| Open #4 | OPEN | **YES** — route simulation speed limit | `domain/tracking/RouteBlueprint.kt` | Routing feature does NOT simulate routes; only reads live. No impact. |
| Open #5 | OPEN | **NO** — onboarding background location permission | `ui/permission/PermissionStatus.kt` | Does not apply; navigation uses foreground tracking only |
| Fixed #10 | FIXED | **YES** — every coroutine must use `launchSafely` | `ui/core/mvi/MviViewModel.kt` | NavigationViewModel MUST use `launchSafely` for route computation coroutines |
| Fixed #17 | FIXED | **NO** — geofence API no longer used | `data/geofence/` (deleted) | Does not apply; feature uses live position flow |
| Fixed #22 | FIXED | **YES** — `FtdLog` now uses `@Volatile Boolean`, not Koin | `data/util/FtdLog.kt`, `ui/core/logging/FtdLog.kt` | NavigationRepositoryImpl may log `FTD_EVENT routing_computed` — use pattern from other `:data` classes |

**Bottom line:** No open deviations prevent implementation. The feature must follow Fixed #10 rule strictly: all route computation coroutines must go through `launchSafely` with proper error handling and flag management (see MVI doc §1 rule "onError must lower every flag the call raised").

---

## 9. DOCUMENTATION DEBT & UPDATE SCOPE

Per `.claude/CLAUDE.md` "Update rules", this feature triggers updates in:

| When | Where | What To Update |
|---|---|---|
| **Add files to `:domain`, `:data`, `:ui`** | `LLM.md` §3 (Bố cục package) | Add new packages if created (unlikely; most files go under existing `navigation/`). Minimal change since packages already exist. |
| **Add new Route type** | `LLM.md` §7 (Routes and navigation) | Document `NavigationRoute` structure and args |
| **Add new ViewModel** | `LLM.md` §3 (`:ui` tree) | Add `NavigationViewModel` to `ui/feature/navigation/` tree; add contract, screen files |
| **Add use cases to `:domain`** | `LLM.md` §3 (`:domain` tree) | Add `ComputeRouteUseCase`, `ObserveNavigationStateUseCase` to `domain/usecase/` |
| **Add DI module** | `LLM.md` §6 (DI wiring) | Add note about `NavigationModule` if created; link to UiModule bindings |
| **Update tracking constants** | `LLM.md` §3 (domain/tracking/) | Add three new constants with explanations |
| **Any new deviation found** | `LLM.md` §13 | If implementation reveals unexpected deviation, add row to "Open" with reasoning |
| **Add tests** | `LLM.md` §9 (Testing conventions) | If new test patterns emerge (unlikely; use existing patterns) |

**Specific sections to update in same commit:**

1. **LLM.md §3 `:domain` tree:** Add under `domain/tracking/`:
   ```
   ├── RouteGeometry.kt            pure algorithm: pointToSegmentDistance(), polylineWithinThreshold()
   └── TrackingConstants.kt        (already exists, ADD 3 constants for reroute thresholds)
   ```
   Add under `domain/repository/`:
   ```
   ├── NavigationRepository.kt     interface: computeRoute(), observeRerouteThresholds()
   ```
   Add under `domain/usecase/`:
   ```
   ├── ComputeRouteUseCase.kt
   └── ObserveNavigationStateUseCase.kt
   ```

2. **LLM.md §3 `:data` tree:** Add under `data/remote/`:
   ```
   ├── datasource/DirectionsApiDataSource.kt
   ├── dto/DirectionsApiResponse.kt
   └── mapper/NavigationRouteMapper.kt
   ```
   Add under `data/repository/`:
   ```
   └── (NavigationRepositoryImpl — add to existing tree)
   ```
   Add under `data/location/`:
   ```
   └── RerouteDetector.kt   pure algorithm
   ```

3. **LLM.md §3 `:ui` tree:** Add under `ui/feature/navigation/`:
   ```
   ├── NavigationContract.kt
   ├── NavigationViewModel.kt
   ├── NavigationScreen.kt
   └── component/
       ├── NavigationPolyline.kt
       ├── NavigationStats.kt
       └── RerouteIndicator.kt
   ```

4. **LLM.md §7 (Routes):** Add:
   ```
   @Serializable
   data class NavigationRoute(
       val memberId: String,
       val targetLat: Double? = null,
       val targetLng: Double? = null,
   ) — Routing feature, US-XX
   ```

5. **LLM.md §6 (DI):** Note in `UiModule.kt` section that navigation use cases are registered there.

---

## UNRESOLVED QUESTIONS

1. **Network Layer Choice:** Project has ZERO HTTP client (no Retrofit, no OkHttp, no Ktor). Must phase plan decide: use `java.net.URLConnection` + `kotlinx-serialization` for REST, or pull in a third-party library? **BLOCKER for data layer.**

2. **Google Directions API vs Routes API v2:** Directions API is REST (well-established, free tier available). Routes API v2 requires `best_route` gRPC or REST JSON (more modern, but requires different SDK). **Must be decided in phase plan.**

3. **Route Caching Strategy:** Should computed routes be cached in Room (with invalidation on reroute), or recomputed every time? Caching reduces API calls but increases complexity. **Design decision for phase.**

4. **Reroute Thresholds:** Assumed 100m target drift + 50m follower deviation based on PRD brief. Are these correct, or should they come from an API response (e.g., Google returning confidence radius)? **Verify in PRD or API response structure.**

5. **Map Integration:** Will Navigation screen reuse the existing `FamilyTrackerMap` component (hosted by parent), or embed its own? Current recommendation is "reuse parent's map + draw polyline on top." **Clarify in phase architecture.**

6. **UI vs Full-screen:** Is Navigation a bottom sheet overlay (like History), or a full-screen replacement? Current assumption: bottom sheet with polyline drawn on Map underneath. **Confirm in phase.**

7. **Follower Tracking Requirement:** If user opens Navigation while tracking is OFF, should app force-start tracking, or show error? **Product decision; recommend error + dialog "Turn on tracking?"**

---

## SUMMARY

- **7 new domain files** (~310 lines): models, repository interface, use cases, pure algorithms
- **6 new data files** (~365 lines): DTOs, API datasource, mapper, repository impl, detector, DI module  
- **7 new UI files** (~513 lines): contract, viewmodel, screen, components, routes
- **Modify 2 existing files:** `MemberMarkers.kt` (entry point), Routes.kt, NavHost.kt, UiModule.kt, DI setup, MapViewModel/MapContract
- **Update TrackingConstants:** 3 new threshold constants
- **Update LLM.md §3, §6, §7:** document new files and routes
- **Testing:** Hand-written fakes, `turbine` for flows, `StandardTestDispatcher`, **NO MockK/Mockito**
- **No blocking deviations:** Only needs to follow MVI doc §1 rule on `launchSafely`
- **Critical unknowns:** Network layer choice (URLConnection vs library), Google API choice (Directions vs Routes v2)
