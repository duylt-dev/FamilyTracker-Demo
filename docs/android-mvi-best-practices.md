# Android / KMP MVI — Best Practices

> Companion to [`../LLM.md`](../LLM.md). `LLM.md` says **where** code goes; this file says
> **how** to write an MVI screen. Read the relevant section before writing a ViewModel or a
> screen composable.

Every rule below is derived from the `Saola` codebase (Kotlin Multiplatform + Compose
Multiplatform, Android + iOS). Snippets are real, not illustrative. Applies unchanged to a
pure-Android Jetpack Compose project — drop the `expect/actual` parts.

---

## 0. The one-paragraph version

State flows down, intents flow up, effects fire once. A screen has exactly one
`StateFlow<S>` and exactly one write entry point, `onIntent(I)`. Anything that must happen
*to* the UI rather than *be rendered by* it — navigate, snackbar, scroll, request a
permission — is an `Effect` delivered through a `Channel`. The ViewModel never touches
Compose, Android, or a platform API; the composable never touches a repository.

```
┌──────────┐  state: StateFlow<S>   ┌──────────────┐  UseCase   ┌────────────┐
│  Screen  │ ←───────────────────── │  ViewModel   │ ─────────→ │   Domain   │
│(stateless)│  onIntent(I) ────────→ │  (MviVM<S,I,E>)│ ←────────  │            │
└──────────┘                        └──────────────┘  AppResult └────────────┘
     ↑  effects: Flow<E>  (Channel, exactly-once)  │
     └───────────────────────────────────────────── ┘
```

---

## 1. The base class

Put this in `core/mvi/MviViewModel.kt` and extend it from **every** screen ViewModel.

```kotlin
/** Everything a screen renders, in one immutable snapshot. */
interface UiState

/** Something the user did. */
interface UiIntent

/** A one-shot instruction to the UI: navigate, show a snackbar, speak a sentence. */
interface UiEffect

abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
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
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation          // NEVER swallow this
        } catch (throwable: Throwable) {
            log.e(throwable) { "Unhandled failure in ${this@MviViewModel::class.simpleName}" }
            onError(AppError.Unexpected(throwable.message))
        }
    }
}
```

`log` above is not `android.util.Log` (or platform `Log`/`NSLog` in general) called directly —
that would break the rule two paragraphs up, and on Android specifically it breaks the JVM
unit test for this exact class: `android.jar` on the unit-test classpath is a stub, and
`Log.e()` throws `RuntimeException: ... not mocked` the first time a real error reaches the
catch block. On a pure-Android (non-KMP) project, back `log` with a tiny local port instead
of an `expect`/`actual` pair — a one-method interface (`AppLogger`) plus a real Android
adapter (`AndroidAppLogger`, the only file allowed to import `android.util.Log`) wired
through the same DI mechanism as everything else. Give the port a no-op default so a
`MviViewModel` subclass constructs on a bare JVM with no test double required — that default
is what makes `launchSafely`'s catch branch testable at all.

**The same rule applies harder to a process-wide log gate that is a plain `object`, not
constructor-injected.** `AppLogger`/`AndroidAppLogger` above is safe because it only ever
reaches code the DI graph already resolved (a ViewModel, which Koin builds). A top-level
singleton like `FtdLog` (this project's release-log gate, `data/util/FtdLog.kt` /
`ui/core/logging/FtdLog.kt`) is called from framework entry points DI does not gate at all —
`BroadcastReceiver.onReceive`, a `Task<T>` callback from a third-party SDK, an instrumented
test that never calls `startKoin`. Reading a feature flag for that object through `by
inject(named(...))` (Koin's lazy `KoinComponent.inject`) means the *first* call from any of
those paths before DI has started throws `IllegalStateException`, not falls back to a
default — and if that first call happens inside a callback on a thread nothing wraps (an SDK
task callback, a receiver's `onReceive`), the exception is uncaught and takes the whole
process down, not just the log line. **Cost paid for real**: fix-phase-11, `FtdLog` as a
`KoinComponent` turned `:data:connectedDebugAndroidTest` from 14/14 green to 1/14 (the
process crashed inside a Play Services `onComplete` callback, mid-`FtdLog.d()`, killing the
other 13 tests before they could run — LLM.md §13 Fixed #22). The fix: a plain `@Volatile
var`, default `false`, set once by direct assignment at process start (`Application.onCreate`,
before `startKoin`) — never read through DI. A logging gate's whole job is to be safe to call
from anywhere, at any time, including before its own configuration has run; that only holds
if its default requires no infrastructure to produce.

### Why `Channel`, not `SharedFlow`, for effects

| | Behaviour when the screen is backgrounded |
|---|---|
| `MutableSharedFlow(replay = 0)` | **Drops** the event. A navigation raised while the screen was stopped never happens. |
| `MutableSharedFlow(replay = 1)` | **Re-fires** on every config change / re-subscription. You then need a `consumed` flag, which is state pretending not to be. |
| `Channel(BUFFERED).receiveAsFlow()` | **Buffers**, delivers exactly once when a collector returns. ✅ |

This is testable, and it should be tested:

```kotlin
@Test
fun `effects raised with no collector are delivered exactly once`() = runTest {
    val vm = viewModel()
    vm.onIntent(LensIntent.PhotoCaptured("/captures/capture_1.jpg"))
    settle()

    vm.effects.test {
        assertTrue(awaitItem() is LensEffect.OpenDiscovery)
        expectNoEvents()
    }
}
```

### Why `launchSafely`, not `viewModelScope.launch`

`viewModelScope` carries a `SupervisorJob` but **no `CoroutineExceptionHandler`**. An
exception escaping a plain `viewModelScope.launch` reaches the platform default handler —
on Android that is the process dying.

This is a floor, not a licence. The layer below already returns `AppResult` and folds its
own failures into `AppError`. `launchSafely` catches the gap between that promise and
reality: an unwrapped Room read, a DataStore file the OS could not open, a mapper meeting a
column written by an older version.

```kotlin
// ✅ user is waiting on this — give them an error to look at
launchSafely(onError = { setState { copy(isLoading = false, error = it) } }) { … }

// ✅ background work they never asked for — log only, do not interrupt them
launchSafely { markLocationAsked() }

// ✅ observing a Flow from init — collectSafely, not .onEach{}.launchIn(viewModelScope)
trackingRepository.isTracking().collectSafely { enabled -> setState { copy(isTracking = enabled) } }

// ❌ never — same hole as viewModelScope.launch, just spelled differently
viewModelScope.launch { repository.doSomething() }
someFlow.onEach { … }.launchIn(viewModelScope)
```

**Observing a `Flow` (`init` block, or a `StateFlow` from a repository) goes through
`collectSafely`, not `.onEach { }.launchIn(viewModelScope)`.** `launchIn` is
`viewModelScope.launch { collect { } }` written differently — it carries the exact same missing
`CoroutineExceptionHandler`, but the string `launchIn` does not match a grep for
`viewModelScope.launch`, which is how this shipped once already (§10, `MapViewModel.init`,
phase-04). `collectSafely` is declared next to `launchSafely` on `MviViewModel`:

```kotlin
protected fun <T> Flow<T>.collectSafely(
    onError: (AppError) -> Unit = {},
    onEach: suspend (T) -> Unit,
): Job = launchSafely(onError) { collect { onEach(it) } }
```

**`CancellationException` must be rethrown.** It is how a coroutine is *told to stop* — a
superseded request, a screen that has left. Swallowing it turns every ordinary cancellation
into a spurious error banner and breaks structured concurrency.

#### `onError` must lower every flag the call raised

This is the rule that gets forgotten, because the code reads as if it were already handled:
the flag *is* lowered in each `AppResult` arm, and those are the only two outcomes anybody
pictures. `onError` is the third, and it reaches none of them.

```kotlin
// ❌ isLoading is raised here and lowered in the two arms below — but not on this path
setState { copy(isLoading = true) }
launchSafely(onError = { setState { copy(error = it) } }) { … }
```

The cost is not a spinner. **A busy flag is usually also the re-entry guard**, so a screen
that strands one refuses the very action that would clear it:

```kotlin
private fun search() {
    if (currentState.isLoading) return          // ← the flag above never comes down,
    …                                            //   so this returns for ever
}
```

Four screens shipped this way and were found on 04.08.2026 — Explore twice, Translation and
Discovery once each; the Discovery one took the *default* `onError`, so the traveller's own
unsaved note sat behind a save button that had silently stopped working. `LensViewModel` and
`ChatViewModel` are the shape to copy.

**Test it by retrying, not by reading the flag.** A flag nobody reads would pass
`assertFalse(state.isLoading)`; only a second call proves the screen recovered:

```kotlin
repository.throwOnNext = IllegalStateException("Room is corrupt")
vm.onIntent(Refresh); runCurrent()
assertFalse(vm.state.value.isLoading)

repository.throwOnNext = null
vm.onIntent(Refresh); runCurrent()
assertEquals(2, repository.calls)               // ← the assertion that matters
```

---

## 2. The Contract file

One file per feature: `XContract.kt`. **State, Intent, Effect — nothing else.** No logic,
no ViewModel, no composables. This is the first file anyone reads to understand a screen.

Mandatory even when a set is empty:

```kotlin
/**
 * Nothing to emit.
 *
 * The screen reads, and the three things it can do — go back, open a discovery, open the
 * lens — are navigation the route already owns. Declared rather than removed because
 * MviViewModel is typed on an effect, and a sealed interface with no cases states exactly
 * what is true.
 */
sealed interface CollectionEffect : UiEffect
```

### State

```kotlin
data class LensState(
    val mode: LensMode = LensMode.AUTO,
    val isAnalysing: Boolean = false,
    val isCapturing: Boolean = false,
    val countdown: Int = 0,
    val recentDiscoveries: List<Discovery> = emptyList(),
    val error: AppError? = null,
) : UiState {
    val isCountingDown: Boolean get() = countdown > 0

    /** True whenever a second shutter press would be wrong rather than merely early. */
    val isBusy: Boolean get() = isAnalysing || isCountingDown || isCapturing
}
```

**Rules**

- `data class`, all `val`, every field defaulted. The defaults *are* the initial state.
- One state class per screen. Never two StateFlows on one ViewModel.
- **Derive, don't duplicate.** Anything computable from other fields is a computed `val`,
  not a stored field. Two fields that can disagree will disagree.
- **Hold ids, not objects,** for "which thing is selected":

  ```kotlin
  val selectedItemId: String? = null              // ✅
  val selected: CollectionEntry? get() = selectedItemId?.let { id -> … }

  val selectedItem: CollectionEntry? = null       // ❌ goes stale on refresh
  ```
  A background refresh then re-renders the open sheet with fresh data instead of leaving a
  stale copy on screen.
- **Model the states that actually differ.** `isCapturing` is separate from `isAnalysing`
  because analysis only starts once there is a file — the few hundred milliseconds between
  them is exactly when a second shutter press starts a second capture.
- **Comment a default that is not the obvious one:**

  ```kotlin
  /**
   * Defaults to true, the opposite of the stored default on purpose: settings arrive one
   * emission after the first frame, and starting at false would flash the location card
   * onto the viewfinder on every single launch.
   */
  val hasAskedLocation: Boolean = true,
  ```
- Every type on a state class must be **stable** for Compose. Domain types come from a
  Compose-free module, so they must be listed in `compose-stability.conf` — see §8.

### Intent

```kotlin
sealed interface LensIntent : UiIntent {
    data class SelectMode(val mode: LensMode) : LensIntent
    data class PhotoCaptured(val path: String) : LensIntent
    data object ShutterPressed : LensIntent
    data object ScreenStarted : LensIntent
    data object ScreenStopped : LensIntent
}
```

**Rules**

- `sealed interface` + `data object` for no-payload, `data class` for payload. Never an enum.
- **Name the event, not the mutation.** `ShutterPressed`, not `SetCapturing(true)`. The
  ViewModel decides what a press means — with a self-timer running it means *cancel*.
- **One intent per user meaning, not per call site.** Toolbar back and system back are the
  same `BackPressed`; whether leaving is free or costs an unfinished translation is not
  something two call sites should each work out.
- **Distinguish outcomes that need different handling.** `CaptureFailed` (apologise) vs
  `CaptureAborted` (the screen left mid-capture — nothing to apologise for, and no error
  banner waiting on the way back).
- Lifecycle is an intent when it changes behaviour: `ScreenStarted` / `ScreenStopped`.

### Effect

```kotlin
sealed interface LensEffect : UiEffect {
    data class OpenDiscovery(val id: String) : LensEffect
    data class ShowMessage(val error: AppError) : LensEffect
    data object TakePhoto : LensEffect
}
```

**State or Effect?**

| Question | Answer |
|---|---|
| Should it survive a config change and be re-rendered? | **State** |
| Should it happen exactly once and then be gone? | **Effect** |

Navigation, snackbars, scroll-to-bottom, permission requests, "fire the shutter now",
opening an external URL → Effect. A loading spinner, an inline error banner, a selected tab
→ State.

**A failure can legitimately be both.** Keep it in state when the screen renders it inline,
*and* raise an effect only in the case where there is nowhere on screen to draw it:

```kotlin
is AppResult.Failure -> {
    setState { copy(isLoading = false, error = result.error) }
    // Over a map that already has markers the failure has nowhere to be drawn, so it is
    // spoken instead. With an empty map the screen renders state.error as a card and this
    // would be the same news twice.
    if (currentState.places.isNotEmpty()) sendEffect(ExploreEffect.ShowError(result.error))
}
```

---

## 3. The ViewModel

```kotlin
class LensViewModel(
    private val recognizeImage: RecognizeImageUseCase,
    private val markLocationAsked: MarkLocationAskedUseCase,
    observeDiscoveries: ObserveDiscoveriesUseCase,      // no `private val` — init-only
    observeSettings: ObserveSettingsUseCase,
) : MviViewModel<LensState, LensIntent, LensEffect>(LensState()) {

    private var analysisJob: Job? = null
    private var countdownJob: Job? = null

    init {
        observeDiscoveries()
            .map { it.take(RECENT_COUNT) }
            .onEach { recent -> setState { copy(recentDiscoveries = recent) } }
            .launchIn(viewModelScope)

        observeSettings()
            .onEach { s -> setState { copy(hasAskedLocation = s.hasAskedLocation) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: LensIntent) {
        when (intent) {
            is LensIntent.SelectMode      -> setState { copy(mode = intent.mode, error = null) }
            is LensIntent.ShutterPressed  -> onShutterPressed()
            is LensIntent.PhotoCaptured   -> { setState { copy(isCapturing = false) }; analyse(intent.path) }
            is LensIntent.LocationAsked   -> launchSafely { markLocationAsked() }
            // …exhaustive, no `else`
        }
    }
}
```

### Where the rule stops: screens, not every ViewModel

**`MviViewModel` is for screens** — anything with a route and a back-stack entry, a user who
acts on it, and a state to render. A **window host** is the stated exception: in this codebase
`MainViewModel` owns the theme and the splash gate for the whole window and is read by the
Android Activity and by `MainViewController` on iOS, before any screen exists. It has no
route, nothing sends it an intent, and nothing would collect an effect, so it stays a plain
`ViewModel` with two StateFlows.

That is the whole exemption for a *plain* ViewModel. It is written down here, in `LLM.md` §3,
and in the class's own KDoc so that nobody files it as an oversight — and so nobody cites it
for a screen. If it is reached by navigation, it extends `MviViewModel`.

**A second exemption goes the other way: a screen with no ViewModel at all.** The licences page
is five fixed paragraphs and four outbound links — no state, nothing asynchronous, nothing that
can fail. `MviViewModel` there would mean a Koin binding, a suite under §7's one-per-ViewModel
rule, and an effect channel with no sender, and the reducer would be the identity function. The
rule to read out of this is not "screens may skip the ViewModel"; it is:

> **A screen gets a ViewModel when it has something to decide.** The moment one acquires a
> decision — a setting to read, a request to make, a failure to report — it acquires a
> ViewModel in the same change, not in a later tidy-up.

Two things stay true even there. The **Route/Screen split does not bend**: that rule is about
who may touch what, and `LicensesRoute` does touch something the page must not — the platform's
URL opener. And the screen still renders `PageHeader`, still appears in
`DesignTokenTest.HEADER_OWNERS`, and still obeys §11: chrome rules are about what a page draws,
not about what holds its state.

### Hard rules

1. **`onIntent` is the only public method.** No other public function, no public property
   besides `state` and `effects`. `onCleared()` is a framework override, not a public method
   in this sense.

   ```kotlin
   fun newCapturePath(): String = captureStore.newCapturePath()   // ❌ escape hatch
   fun onMicPermissionGranted() { … }                             // ❌ should be an Intent
   ```
   If the composable needs a value the VM has, either raise it in an Effect or let the
   composable get the collaborator from DI directly. Both shapes are in this codebase, and
   which one is right depends on **who knows when the value is needed**:

   ```kotlin
   // The lens: the ViewModel already decides WHEN the shutter fires (self-timer), so it
   // decides WHERE the file goes at the same moment and sends both together.
   data class TakePhoto(val outputPath: String) : LensEffect
   ```
   ```kotlin
   // Discovery's note camera: the shutter is pressed inside the overlay and the ViewModel
   // never learns of the press, so there is no effect for a path to ride on. The composable
   // takes the store from Koin — it is the store, not the ViewModel, and nothing below the
   // Route sees a ViewModel.
   val captureStore: CaptureStore = koinInject()
   ```
   What is *not* acceptable is the third option both replaced: a public method on the
   ViewModel, threaded down as a lambda through five composables.

2. **The `when` must be exhaustive with no `else`.** That is the entire reason Intent is a
   sealed interface: adding a case becomes a compile error at every reducer.

3. **`init` observes; it does not act.** Wire up `observeX().onEach { setState { … } }
   .launchIn(viewModelScope)` and stop. Work that needs a permission or a user decision
   starts from an intent — `ExploreIntent.PermissionResolved` — because the permission is
   the screen's to ask for.

4. **Constructor arguments used only in `init` are not `private val`.** Keeping a reference
   to a use case you never call again is a needless retention.

5. **Route arguments are read from `SavedStateHandle` into the initial state**, not copied
   in a frame later:

   ```kotlin
   ) : MviViewModel<TranslationState, …>(
       // The arguments ARE the initial state. A `setState { copy(imagePath = imagePath) }`
       // in init silently reads the state's own property instead of the field beside it.
       TranslationState(imagePath = savedStateHandle[Routes.ARG_IMAGE_PATH] ?: ""),
   )
   ```

6. **Never import anything from `androidx.compose.*` or a platform SDK** into a ViewModel.
   The one exception in this codebase is `ImageBitmap` on `PassportState`, and it costs a
   line in `compose-stability.conf`.

7. **No business rules in the ViewModel.** It orchestrates use cases and reduces state. A
   rule that could be unit-tested without a `StateFlow` belongs in `:domain`.

### Concurrency

**Cancel and replace, don't queue.** A second request usually means "that one, now", not
"both of them":

```kotlin
private fun analyse(imagePath: String) {
    // A second capture while one is in flight replaces it: the traveller has moved on, and
    // finishing the old request would navigate them somewhere they no longer care about.
    analysisJob?.cancel()
    analysisJob = launchSafely(onError = { setState { copy(isAnalysing = false, error = it) } }) { … }
}
```

**Guard re-entrancy in the reducer, not in the UI:**

```kotlin
private fun generate(date: LocalDate) {
    // One at a time: each is a full model call, and two in flight would let a slow one
    // overwrite a fresh one when it lands.
    if (currentState.generatingDate != null) return
    …
}
```

**Own sub-jobs structurally, never as a field you must remember to cancel.** This is the
single highest-value rule in this document — it is a real bug that shipped:

```kotlin
analysisJob = launchSafely(…) {
    // A CHILD of this job, not a `stageJob` field.
    //
    // As a field it had to be cancelled by hand at each of the four places a request can
    // end, and two were missed: the early-return translate branch, and any exception. That
    // left a `while (isActive)` loop writing state every 1.8s for the rest of the
    // ViewModel's life, recomposing a screen the user had left.
    val ticker = launch {
        var stage = 0
        while (isActive) { delay(STAGE_INTERVAL_MILLIS); setState { copy(analysisStage = ++stage) } }
    }
    try {
        val result = withTimeoutOrNull(DETECT_TIMEOUT_MILLIS) { recognizeImage(imagePath, currentState.mode) }
        when (result) { … }
    } finally {
        ticker.cancel()   // an infinite child would keep the parent from ever completing
    }
}
```

**Bound every wait.** `withTimeoutOrNull(DETECT_TIMEOUT_MILLIS)` rather than
`withTimeout`, so the expiry lands in the same `when` as every other outcome instead of in
a `catch` that must be careful not to swallow the screen's own cancellation.

**Guard late results against a changed selection:**

```kotlin
is AppResult.Success -> setState {
    // A slow response for a place the traveller has since closed must not reopen it.
    if (selectedPlaceId == placeId) copy(details = result.data, isLoadingDetails = false) else this
}
```

**Release what you claimed, in `onCleared`:**

```kotlin
override fun onCleared() {
    super.onCleared()
    volumeShutter.disarm()
    analysisJob?.cancel()
    countdownJob?.cancel()
}
```

### Room / DataStore is the single source of truth

After a write, do **not** copy the result into state. Let the observed flow re-emit:

```kotlin
is AppResult.Success -> setState { copy(generatingDate = null) }
// The written summary arrives through observeJournal(), so there is nothing to assign
// here — Room stays the single source of truth.
```

---

## 4. The screen

Two composables, always. `XRoute` is public and stateful; `XScreen` is private and pure.

**The screen is the only part of a feature that a form factor may have twice.** This project
splits its presentation layer into `mobile/` and `tablet/` (`LLM.md` §3, §5): the Contract
and the ViewModel stay in `feature/<name>/`, one copy each, while `XScreen.kt` lives under a
branch. Everything in this section applies unchanged inside a branch, plus one rule:

> **A branch screen arranges; it never decides.** No `if` on data, no derived business
> value, no ViewModel of its own. Two arrangements of one state — if the tablet needs to
> *know* something the phone does not, that knowledge belongs on the shared `XState` as a
> computed `val`, where both branches read the same answer.

The reason is the failure mode, not tidiness: a rule computed in one branch is a rule the
other branch does not have, and the divergence surfaces as "it works on my phone" months
later. Duplicating a *layout* is cheap and visible; duplicating a *decision* is neither.
A composable both branches draw is lifted into `feature/<name>/` — never copied.

**A branch `Route` may hold more than one ViewModel; nothing below it may hold any.** A large
window shows two features at once — the guide beside the discovery, the passport and the
collection beside the journal's day column — and the feature living in the pane still needs
its own ViewModel. It is resolved on the host Route, from the same back-stack entry, and the
pane below takes `state` + `onIntent` like any other stateless piece. The alternative, a pane
that calls `koinViewModel()` itself, is a Route with no destination, and there would be one
per pane before anyone noticed. Name those files `XPane.kt` so the tree says which is which,
and list every extra parameter in `ComposeStabilityReportTest`'s allowlist.

```kotlin
@Composable
fun LensRoute(
    onDiscoveryCaptured: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LensViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is LensEffect.OpenDiscovery -> onDiscoveryCaptured(effect.id)
            is LensEffect.ShowMessage   -> Unit   // rendered inline by the error banner
        }
    }

    LensScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
private fun LensScreen(
    state: LensState,
    onIntent: (LensIntent) -> Unit,
    modifier: Modifier = Modifier,
) { … }
```

### The effect collector — written once, in `core/mvi/CollectEffects.kt`

Six screens hand-rolling this block is how two of them ended up forgetting it entirely.
There is now exactly one copy, and `effects.collect` appears nowhere else in the codebase.

```kotlin
// core/mvi/CollectEffects.kt
@Composable
fun <E : UiEffect> CollectEffects(effects: Flow<E>, onEffect: suspend (E) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { handler(it) }
        }
    }
}
```

- **`collect`, never `collectLatest`.** `collectLatest` cancels the handling of the
  previous effect the moment the next arrives — a navigation half-performed.
- **`repeatOnLifecycle(STARTED)`,** so nothing is handled while the screen is not visible.
  The channel buffers meanwhile; nothing is lost.
- **The handler is not a `LaunchedEffect` key.** It is a fresh lambda on every recomposition,
  so keying on it would tear the collector down and rebuild it each frame.
  `rememberUpdatedState` is what lets the collector stay up while still calling the newest
  navigation lambdas.
- **If a ViewModel declares an Effect, some screen must collect it.** An uncollected
  `Channel(BUFFERED)` fills at 64 and every subsequent `send` suspends forever inside
  `viewModelScope` — one leaked coroutine per emission, and the feature silently dead. The
  matching rule is that an effect nobody collects should not be declared: `ChatEffect`,
  `CollectionEffect`, `PassportEffect` and `SovereigntyEffect` are empty sealed interfaces,
  and those four routes correctly have no collector.
- **An effect must carry everything its handler needs.** The collector runs one main-queue
  turn after `sendEffect`; the recomposition that reacts to the `setState` beside it runs on
  the next *frame*, which is later. So a handler that reads anything the same emission just
  wrote to state reads the value from *before* the failure. This shipped, and it was silent:

  ```kotlin
  // ❌ resolved during composition, still null when the effect lands
  val errorMessage = state.error?.toUserMessage()
  CollectEffects(viewModel.effects) { effect ->
      when (effect) {
          is JournalEffect.ShowMessage -> scope.launch {
              snackbarHostState.showError(errorMessage ?: return@launch)   // drops it, every time
          }
      }
  }

  // ✅ from the effect's own payload — no recomposition involved
  is JournalEffect.ShowMessage -> scope.launch {
      snackbarHostState.showError(effect.error.userMessage())
  }
  ```

  `toUserMessage()` is `@Composable`, which is *why* the wrong version looked necessary.
  `core/util/ErrorMessages.kt` therefore has a `suspend` twin, `AppError.userMessage()`,
  built on `getString` rather than `stringResource` and sharing one `when` with the composable
  form. Reach for it in any collector.

  The cost of getting this wrong was a failed day summary reaching the traveller as a spinner
  that simply stopped — see `LLM.md` §11 row #15.

- **If a screen draws no inline error, the failure does not belong in state at all.** Holding
  it in both places is what invited the bug above: `JournalState.error` and
  `SettingsState.error` were written on every failure and read by nobody. Keep a failure in
  state only where a composable renders it — `ExploreState.error` is the good case, drawn as
  a card whenever the map is empty, with the effect reserved for the case where the map
  already has markers and there is nowhere left to draw it.

- **Anything that suspends for a visible duration goes in a `scope.launch { … }` inside the
  branch, not awaited by the collector.** `SnackbarHostState.showSnackbar` suspends until the
  notice is dismissed, so a collector that awaited it would hold the next effect behind the
  current snackbar — which turns "replace" into "queue four seconds later". Same for work
  that must outlive the collector: the collector now dies at `ON_STOP`, and a capture in
  flight has to reach its `finally`.

  ```kotlin
  is SettingsEffect.ShowMessage -> scope.launch {          // ✅ collector stays free
      snackbarHostState.showError(errorMessage ?: return@launch)
  }
  ```

### Rules for `XScreen` and below

- Takes `state: XState` and `onIntent: (XIntent) -> Unit`. Never the ViewModel.
- **Never derives business truth.** `if (state.isBusy)`, not
  `if (state.isAnalysing || state.countdown > 0 || state.isCapturing)`. That expression
  belongs on the state class.
- No `remember { mutableStateOf(…) }` for anything the ViewModel should own. Purely visual
  local state (a `SnackbarHostState`, a `LazyListState`, an animation target) is fine.
- **Visual local state that belongs to a *control* lives in the control, not in the screen
  above it.** Whether a picker is open is one of these: it survives a rotation and nothing
  else, so it is right that no ViewModel holds it — but it is a fact about that row, not about
  the arrangement drawing the row. `ThemeRow` therefore owns its `rememberSaveable` and draws
  both the row and the dialog it opens, and the phone and the large window each call it once.
  Left in the two screens, the flag is declared twice and the second declaration is the one
  that misses the next change to it. Ask which of the two the state describes: a
  `LazyListState` hoisted above a pane switch belongs to the *screen* (see
  `JournalTabletScreen`), and an open/closed flag belongs to the thing that opens.
- Every parameter must be **stable**, or the composable can never skip and re-executes on
  every parent recomposition.
- Passes `onIntent` down as-is. Do not wrap it in a new lambda per item in a list — that
  allocates a new instance each recomposition and defeats skipping.

### Platform state lives in the composable, and reports upward

Permissions need an Activity result launcher on Android and a `CLLocationManager` on iOS.
Neither belongs in a ViewModel:

```kotlin
val permission = rememberLocationPermissionState()
LaunchedEffect(permission.isGranted) {
    viewModel.onIntent(ExploreIntent.PermissionResolved(permission.isGranted))
}
```

Same shape for lifecycle events the VM must react to:

```kotlin
DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> viewModel.onIntent(LensIntent.ScreenStarted)
            Lifecycle.Event.ON_STOP  -> viewModel.onIntent(LensIntent.ScreenStopped)
            else -> Unit
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
        viewModel.onIntent(LensIntent.ScreenStopped)
    }
}
```

### File size

Target **under 200 lines**. Past ~150 lines a private composable moves to
`XComponents.kt` in the same package. Screens in this codebase currently run to 2170 lines
— that is the backlog, not the pattern.

---

## 5. Consuming the domain layer

The repository contract is `AppResult<T>` + `AppError`. **Exceptions never cross a layer
boundary** — `launchSafely` exists only because reality occasionally disagrees.

```kotlin
when (val result = generateDaySummary(date)) {
    is AppResult.Failure -> {
        setState { copy(generatingDate = null, error = result.error) }
        sendEffect(JournalEffect.ShowMessage(result.error))
    }
    is AppResult.Success -> setState { copy(generatingDate = null) }
}
```

`AppError` is a sealed hierarchy, not a `String`. The screen maps it to a localised message
at render time (`core/util/ErrorMessages.kt`) — the ViewModel must never build user-facing
copy, because it has no access to the string resources and no business being in the
translation pipeline.

**Observe, don't fetch,** wherever a Flow exists. Every `observeX()` re-emits on write, so
the screen is correct after a change made from anywhere else in the app.

---

## 6. Dependency injection (Koin)

```kotlin
val presentationModule = module {
    viewModel { LensViewModel(recognizeImage = get(), observeSettings = get()) }

    // Reads route arguments → needs SavedStateHandle from the nav back-stack entry
    viewModel { params -> ChatViewModel(savedStateHandle = params.get(), observeChat = get()) }
}
```

- `viewModel { }`, never `single { }`. A `single` ViewModel outlives its screen, keeps its
  state and its jobs forever, and is the classic "why is the old data still there" bug.
- Constructor injection only. No `by inject()` inside a ViewModel body.
- Register the use case in `useCaseModule` and the ViewModel in `presentationModule` in the
  same change that adds them.

---

## 7. Testing an MVI ViewModel

The ViewModel is the only part of the presentation layer worth unit-testing, and with this
architecture it is fully testable: no Android, no Compose, no `Context`.

```kotlin
@BeforeTest fun setUp() {
    // viewModelScope is hard-wired to Dispatchers.Main.immediate, which does not exist on a
    // plain JVM runtime. Unconfined so a launch inside init has run by the time the
    // constructor returns — which is what the screen sees too.
    Dispatchers.setMain(UnconfinedTestDispatcher())
}
@AfterTest fun tearDown() = Dispatchers.resetMain()
```

**Cover these five categories:**

| Category | Assert |
|---|---|
| Reducers | `vm.onIntent(X); assertEquals(expected, vm.state.value.field)` |
| Effects | `vm.effects.test { assertTrue(awaitItem() is XEffect.Y); expectNoEvents() }` |
| Crash containment | a fake that throws → `state.error is AppError.Unexpected`, **not** a thrown test |
| Stuck states | a request that never answers → the spinner **must** come down at the timeout; and a repository that **throws** → the flag comes down *and* the next attempt reaches the repository |
| Unbounded growth | 500 rows in the fake → `assertEquals(5, state.recent.size)` |

**Intent fuzzing** is the standard for any ViewModel with more than two concurrent jobs.
Exhaustive `when` proves you handled every intent; it proves nothing about *combinations*:

```kotlin
@Test fun `no ordering of intents throws`() = runTest(timeout = 30.seconds) {
    val random = Random(seed = 20260802)          // fixed → reproducible
    repeat(FUZZ_ROUNDS) {
        val vm = viewModel()
        repeat(20) {
            vm.onIntent(intents[random.nextInt(intents.size)])
            // Let that make progress, so the next intent lands on a ViewModel mid-flight.
            if (random.nextBoolean()) advanceTimeBy(random.nextLong(1, 2_000))
            runCurrent()
        }
        settle()
        vm.clearAsFrameworkWould()
    }
}
```

This is what found the leaked stage ticker — and it found it by *hanging*, which is why:

> **Never use `advanceUntilIdle` on a ViewModel with a `while (isActive)` loop.** The
> scheduler holding a leaked one never becomes idle, so the call hangs the suite instead of
> failing it. Use a bounded advance:
> ```kotlin
> private fun TestScope.settle(horizonMillis: Long = 120_000) {
>     advanceTimeBy(horizonMillis); runCurrent()
> }
> ```

Fakes live in `commonTest/testing/Fakes.kt` with switchable failure modes
(`throwOnRecognize`, `recognizeDelayMillis`) plus `clearAsFrameworkWould()` to invoke
`onCleared()`. No mocking library — a fake you can read beats a mock you have to decode,
and MockK does not work on Kotlin/Native anyway.

### Where a ViewModel test cannot reach: the sixth category

Everything above tests what a screen *knows*. Some screens have a claim that lives entirely in
what they *draw*, and there a ViewModel suite is green while the feature is broken. Two shapes
qualify, and both belong in `androidDeviceTest`:

- **A `Canvas` that answers a gesture.** `VietnamMapCanvas` turns a tap into a longitude and a
  latitude and back into a province; `PassportViewModelTest` proves the state around that is
  right and cannot see any of it. A sign error in `latitudeAt` puts a finger on Nghệ An and
  opens Thanh Hóa, and nothing but a person on a phone would notice.
- **A claim about accessibility.** Semantics are not state — they are a tree the platform
  builds out of a composition — so the only way to assert that a province is reachable, named
  and actionable is to compose it and ask. `PassportMapTest` does, and it found something no
  reading of the code would have: two provinces whose bounding boxes sat inside a
  later-placed neighbour's were absent from the tree entirely.

Two rules for writing them, both learned from that suite:

1. **Resolve strings inside the composition** (`stringResource`, assigned to a `var` the test
   reads back), never as English literals. These run on whatever locale the device is in.
2. **Invoke a semantics action with `performSemanticsAction(SemanticsActions.OnClick)`, not
   `performClick`.** `performClick` injects a real touch, which is exactly what a
   semantics-only node has no handler for — so it passes through to whatever is underneath and
   the test proves nothing about the node it named.

---

## 8. Compose performance — the part MVI makes or breaks

Every `setState { copy(…) }` creates a new state object. If any type on that object is
**unstable**, the composable taking it compares by reference, can never skip, and the whole
subtree re-executes on every emission. On a screen with a 1-second timer tick that is the
entire viewfinder repainting once a second.

Domain types come from a module compiled **without** the Compose plugin, so they are all
inferred unstable regardless of being immutable `data class`es. Fix it by declaring them,
not by putting a Compose dependency in `:domain`:

```conf
// compose-stability.conf   (// comments only — a # breaks the parse)
kotlin.collections.List
kotlin.collections.Map
kotlin.collections.Set
kotlin.ranges.IntRange
kotlin.time.Instant
kotlinx.datetime.LocalDate
androidx.compose.ui.graphics.ImageBitmap
com.example.pion.family.tracker.demo.domain.model.*
com.example.pion.family.tracker.demo.domain.util.*
```

Wire the reports and gate on them:

```kotlin
composeCompiler {
    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}
```

`ComposeStabilityReportTest` then asserts against those reports on every build, so a class
added to the conf file that is not actually immutable is caught rather than assumed.
**Everything listed must be genuinely immutable after construction** — `val`-only, stable
property types, no collection ever mutated in place.

Other performance rules that follow from MVI:
- Cap anything unbounded **in the ViewModel**, not in the UI: `.map { it.take(5) }` before
  it reaches state. A `LazyColumn` rendering 5 of 500 still retains all 500.
- Split large states only if a genuinely independent region recomposes on unrelated
  changes. Prefer one state class; reach for `derivedStateOf` in the composable first.

### `skippable` is a promise the route can break

Every composable in this project is marked `skippable`, and most of them still recompose
on every emission. The mark says the compiler *may* skip the call; it skips only when
every argument is `equals` its predecessor — and `onIntent = viewModel::onIntent`, written
at the call site, is not.

A bound callable reference captures its receiver, and **every ViewModel is `unstable`** —
the compose report says so, because a `ViewModel` is a class with mutable fields. The
compiler will not memoise a lambda over an unstable capture, so the expression allocates a
new `Function1` on every recomposition of the route, every child taking it is unequal to
its predecessor, and the skip never happens. It cascades: the derived
`{ id -> onIntent(SelectPlace(id)) }` in the child cannot be memoised either, so *its*
children are denied their skip too.

```kotlin
// XRoute — hold it steady when the subtree below is expensive to re-run
val onIntent = remember(viewModel) { viewModel::onIntent }
```

Cheap subtrees do not need this and most screens here do not do it. Reach for it when the
tree below contains something that costs more than a layout pass — a map, a camera
preview, a `Canvas` that projects geometry. On Explore, re-running `PlaceMap`
means crossing into the Maps SDK once per marker on Android and re-running the whole
`UIKitView` update on iOS, and it was doing that for emissions that only moved a spinner.

**Where two arrangements draw the same expensive subtree, the line belongs above both of
them.** Explore keeps it in `feature/explore/ExploreHost.kt` rather than in each branch's
Route: an optimisation that has to be remembered twice is one that will be present on one
form factor and absent on the other, and the symptom — a map re-entering the SDK on every
spinner tick — is invisible in a screenshot.

### Warm a heavy platform engine before its composable needs it

An SDK-backed view is not free to create, and the bill is presented on the **first** frame
that composes it. The Maps SDK fetches and links its renderer out of Play services;
MapKit builds a tile pipeline. Both are synchronous, both run on the thread that asked,
and inside a composable that thread is the main one.

Composed inside a `when` arm that only becomes true when the data lands, the cost falls on
the exact frame the traveller is watching. Two halves to the fix, and it needs both:

1. **Start the engine off the main thread and gate the composable on it.** `PlaceMap` on
   Android runs `MapsInitializer.initialize` on `Dispatchers.IO` behind a process-wide
   flag and draws a plain surface until it returns. The method is `static synchronized`,
   so this is the identical work moved somewhere it does not show.
2. **Compose the view early, behind an opaque cover**, rather than swapping it in when the
   data arrives. Both of Explore's arrangements compose the map as soon as the permission
   is answered and draw the loading and failure states *over* it — so the engine starts
   while the fix and the search are still in flight, and the cover lifts onto a warm map.

A cover over a live view has to **swallow touches** as well as be opaque, or the drag goes
straight through to a map nobody can see. An empty `Modifier.pointerInput(Unit) {}` is
hit-tested like any other pointer node and stops the sibling below being tested at all.

The guard belongs on **everything** floated over the live view, not only on the covers. A
large window puts a scrolling column of results over the map, and a scroller consumes drags
only in its own direction and only while it has somewhere left to go — so without the same
empty `pointerInput`, flicking past the end of the list pans the map underneath it.

Composing early also means the view is created before the first camera target exists, so
it opens on a fallback. **Apply the first camera move without an animation** — otherwise
every visit to the tab begins by flying the traveller in from the fallback city.

---

## 9. Checklist — run this before opening a PR

**Contract**
- [ ] `XContract.kt` exists and holds State + Intent + Effect only
- [ ] State is a `data class`, all `val`, every field defaulted
- [ ] No field derivable from another field; derived values are computed `val`s
- [ ] Selections are held as ids, not as objects
- [ ] Intent is a sealed interface named after user events, not mutations
- [ ] Every Effect is one-shot; nothing renderable is an Effect
- [ ] New domain types on the state are in `compose-stability.conf`

**ViewModel**
- [ ] Extends `MviViewModel<S, I, E>`
- [ ] `onIntent` is the **only** public method
- [ ] The `when` is exhaustive with no `else`
- [ ] Every coroutine goes through `launchSafely` (or `collectSafely` for a `Flow`);
      `CancellationException` is rethrown. Grep for **all** of these, not just
      `viewModelScope.launch` — each bypasses the crash floor the same way (§1, §10):
      `viewModelScope.launch`, `.launchIn(viewModelScope)`, `GlobalScope.launch`,
      `CoroutineScope(...)`, `runBlocking`
- [ ] Every flag raised before a suspend call is lowered in `onError` too — and a test
      proves the *retry* reaches the repository, not just that the flag came down
- [ ] Every network/IO wait is bounded (`withTimeoutOrNull`)
- [ ] Superseding requests cancel the previous job
- [ ] Sub-jobs are structural children, not fields
- [ ] Late results are guarded against a changed selection
- [ ] `onCleared` releases everything claimed
- [ ] No Compose / Android / platform import
- [ ] No business rule that belongs in a use case
- [ ] Registered in `presentationModule` as `viewModel { }`

**Screen**
- [ ] `XRoute` (public, stateful) + `XScreen` (private, stateless) split
- [ ] `collectAsStateWithLifecycle()`, not `collectAsState()`
- [ ] Effects collected via `CollectEffects` — `collect`, lifecycle-aware
- [ ] **Every** declared Effect has a branch
- [ ] Nothing below `XRoute` sees the ViewModel
- [ ] No business logic in a composable
- [ ] File under 200 lines

**Arrangement** (`LLM.md` §3, `docs/large-screen-layout.md`)
- [ ] The screen has a large-window arrangement under `tablet/feature/<name>/`, **or** the
      reason it does not is written down — in its own KDoc and in `large-screen-layout.md`.
      "Nobody asked for it yet" is not a reason; "it is the same picture at any width" is
- [ ] Every composable both arrangements draw lives in `feature/<name>/component/`, not
      `private` inside one of them
- [ ] Nothing under `mobile/` or `tablet/` decides anything — no use case, no business rule,
      no second answer to a question the ViewModel already answers
- [ ] A new route is registered in **both** shells, with the same `navArgument` defaults, or
      resizing the window clears the back stack

**Screen chrome** (§11)
- [ ] The screen renders `PageHeader` or `OverlayHeader`, never a header of its own
- [ ] No `.dp` in a `Spacer`, a `padding` or a `RoundedCornerShape`; no `.sp` or
      `fontWeight` anywhere under `feature/`

**Tests**
- [ ] Reducer test per intent that changes state
- [ ] Effect test per effect
- [ ] Crash-containment test for every throwing collaborator
- [ ] Timeout test for every unbounded wait
- [ ] Intent fuzz test if the VM has >2 concurrent jobs
- [ ] `Dispatchers.setMain` / `resetMain`; no real delays; no `advanceUntilIdle`

---

## 10. Anti-patterns — with the real cost

| Anti-pattern | What it actually costs |
|---|---|
| Effect declared but never collected | `Channel(BUFFERED)` fills at 64, `send` suspends forever, one leaked coroutine per emission, feature silently dead. *Was* live in `ChatScreen` and `JournalScreen`. |
| Public method on a VM besides `onIntent` | The screen can drive the VM out of band; the reducer stops being the whole story. *Was* live: `newCapturePath()`, `onMicPermissionGranted()`. |
| `effects.collectLatest { }` | The next effect cancels the handling of the previous one. *Was* live in three screens. |
| Awaiting `showSnackbar` in the collector | It suspends for the length of the notice, so the next effect waits behind it and a replacement becomes a queue. Launch it from a `rememberCoroutineScope()`. |
| Effect handler reading state the same emission just wrote | The handler runs a main-queue turn before the next frame, so it reads the pre-failure value. **Cost a real bug**: a failed day summary was reported to nobody for as long as the feature existed. Put it on the effect. |
| A failure in state that no composable draws | Two records of one fact, one of which is never read — and the unread one is the one the collector trusted. |
| Contract inline in the ViewModel file | The state class is buried; nobody reads it; fields get duplicated. *Was* live in four features. |
| `viewModelScope.launch` without a handler | No `CoroutineExceptionHandler` on `viewModelScope` → process death on Android. |
| `someFlow.onEach { }.launchIn(viewModelScope)` | Same bug as above, spelled differently — `launchIn` is `viewModelScope.launch { collect { } }` under the hood, so it bypasses `launchSafely` too. Grepping for `viewModelScope.launch` alone misses it. **Cost a real bug**: live in `MapViewModel.init` at phase-04 (`isTracking()` observation), caught in the phase-04 fix pass, not by the phase-04 test pass — the tester's grep matched the string `viewModelScope.launch`, not `launchIn`. Use `collectSafely` (§1) instead. |
| `GlobalScope.launch { }` | Outlives the ViewModel entirely — no cancellation on `onCleared`, no crash floor. Not currently used in this codebase; grepped for in the checklist below because it is the next thing someone reaches for once shown `launchIn` is off-limits. |
| `runBlocking { }` inside a ViewModel or repository call | Blocks the calling thread (the main thread, if called from `onIntent`) until the suspend function returns — freezes the UI for however long the coroutine takes. There is no legitimate use of this on the hot path; it belongs in scripts and `main()` functions only. |
| Swallowing `CancellationException` | Every ordinary cancellation becomes an error banner; structured concurrency breaks. |
| `SharedFlow` for effects | Drops the event (replay 0) or re-fires it (replay 1). |
| Navigation flag in state | Re-navigates on every config change; needs a `consumed` flag, which is state pretending not to be. |
| Storing the selected *object* | A refresh leaves a stale copy on screen behind an open sheet. |
| Sub-job as a field | Missed cancel → `while (isActive)` loop writing state forever. **Cost a real bug.** |
| `advanceUntilIdle` with a ticker | The suite hangs instead of failing. |
| Domain types not in `compose-stability.conf` | Every composable taking one becomes non-skippable; whole screens repaint per tick. |
| `single { }` for a ViewModel | It outlives its screen with all its state and jobs. |
| Plain `ViewModel` instead of `MviViewModel` for a **screen** | No effect channel, no crash floor, untestable in the same way as everything else. *Was* live: `SovereigntyViewModel`. The window host is the one stated exception — see §3. |
| A screen building its own header | Five screens did, and produced five header boxes and four title sizes for one job. Nothing looked wrong on any single screen. See §11. |
| `statusBarsPadding()` on a screen | Held sideways, the notch is on an edge the status bar does not report, so the inset is **wrong** and the control sits under the cutout. **Cost a real defect**: five controls on the discovery page did. Use `screenInsetsPadding()` or `OverlayHeader`. |
| A `.dp` or `.sp` literal in `feature/` | It compiles to the same bytecode as the token, so no reviewer and no other test can see it. There were 57 hardcoded radii at twelve values and 195 spacing literals at nineteen before `DesignTokenTest`. |

---

## 11. Screen chrome — the header and the tokens

The MVI rules above say how a screen *behaves*. This section says what it is allowed to
decide about how it *looks*, and the answer is: less than you would expect.

### `XScreen` renders one of two headers. It never writes its own.

| Component | For | Applies the top inset? |
|---|---|---|
| `PageHeader` | a document screen — journal, settings, collection, passport, chat | **No.** The screen's outermost container does, because in landscape the display cutout moves to one side and the whole page has to move with it |
| `OverlayHeader` | an immersive screen — one drawing over a photograph, a camera feed or a map | **Yes**, itself. It floats over content, so nothing else is in a position to |

Both take strings and lambdas. Neither takes a text style, and `PageHeader` takes colours
only because two screens are fixed to the lacquer palette by design (`LLM.md` §12).

**The cost of writing your own.** Five screens used to. Their header boxes ran 0/16, 0/4,
12/4, 12/4 and 10/12 top-and-bottom, and their titles were set at `headlineLarge`,
`headlineMedium`, `headlineMedium`, `headlineMedium` and `titleLarge` — five titles, four
sizes. Nothing on any one screen looked wrong. The app looked wrong, because a traveller
crosses three of them in about four seconds and the heading moved every time.

**And a real defect, not just an inconsistency.** The discovery page reached for
`statusBarsPadding()` in five places instead of `screenInsetsPadding()`. The app hid the
system bars at the time, so that inset was zero — and the notch is a hole in the glass that
was still there. The page's close and delete, the photo viewer's close and the note camera's
close and flip all sat under the cutout on every phone that has one. The status bar has since
come back, which fixes the phone held upright and nothing else: turn it sideways and the
cutout is on an edge no bar reports. `OverlayHeader` owning the inset is what stops the next
screen repeating it.

**A second inset trap, found while reviewing this refactor.** Material3's `Surface` chains
the caller's `modifier` *ahead of* its own `.background(...)`, so an inset passed to a
`Surface` shrinks the painted area rather than the content inside it. The chat header did
exactly that for one revision and left a bare strip above its own coloured band. Put the
inset on the content, and leave the surface full-bleed.

Two screens are deliberately outside this rule, and `DesignTokenTest` records both:
`LensScreen`, whose camera tool row is a line of switches rather than a header, and
`SovereigntyScreen`, a scrolling document whose own column already takes the inset.

### Nothing is measured at the call site

| You want | Use | Never |
|---|---|---|
| A gap | `Spacing.xxs…xxl` (2/4/8/12/16/24/32) | `Spacer(Modifier.height(12.dp))` |
| A gap that must agree across screens | `PageSpacing.headerTop`, `headerToContent`, `sectionGap`, `listBottom`, `snackbarLift` | a literal repeated on three screens |
| The page edge | `ScreenGutter` | `ScreenGutter + 4.dp` |
| A corner | `MaterialTheme.shapes.X`, `Pill`, `CircleShape` | `RoundedCornerShape(20.dp)` |
| A text style | one of the fifteen scales, or `StampType` | `.copy(fontSize = …)`, or a call-site `fontWeight` |
| The top edge | `screenInsetsPadding()`, or `OverlayHeader` | `statusBarsPadding()` |

A value that is a *position* rather than a gap — how much room a floating composer takes,
where the shutter sits above the navigation bar — is not on the 4 dp scale at all. Give it
a named `private val` and a sentence saying what it was measured against.

`DesignTokenTest` (androidHostTest) enforces every row of that table by reading the sources
as text, because a literal and a token compile to identical bytecode and the difference
survives nowhere else. Its failure messages state the cost, not just the line.
