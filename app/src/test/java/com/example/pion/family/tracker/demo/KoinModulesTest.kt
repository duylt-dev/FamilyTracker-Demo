package com.example.pion.family.tracker.demo

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.example.pion.family.tracker.demo.data.di.databaseModule
import com.example.pion.family.tracker.demo.data.di.dataModule
import com.example.pion.family.tracker.demo.domain.model.RoutingConfig
import com.example.pion.family.tracker.demo.ui.di.uiModule
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.verify.verify
import java.io.File

/**
 * Wiring safety net — LLM.md §6. Koin only fails at runtime when a binding is missing,
 * so this test forces that failure into CI instead of onto a demo device.
 *
 * Uses `verify()` (org.koin.test.verify), not the deprecated `KoinApplication.checkModules()`
 * the phase spec's code snippet showed — its own comment already pointed at this package.
 *
 * NOT `listOf(dataModule, databaseModule, uiModule).verifyAll()`: `verifyAll()` calls
 * `module.verify()` on each module INDEPENDENTLY (see `VerifyModule.kt` — `forEach { it.verify() }`),
 * so `dataModule`'s repository impls (constructor-injected with DAOs from `databaseModule`)
 * would fail with `MissingKoinDefinitionException` even though the binding exists one module
 * over. Verified against source: `Verification`'s definition index is built from
 * `module.includedModules + module` only — sibling modules in the same list are never merged.
 * `includes(...)` on a throwaway wrapper module is what actually merges the definition indices.
 *
 * `extraTypes = listOf(Context::class, SavedStateHandle::class)` — phase-04: `TrackingRepositoryImpl`
 * takes a `Context` constructor param resolved by `androidContext()` inside the `single { }`
 * lambda (LLM.md §6), not by a `get()` call `verify()`'s static constructor-reflection can see.
 * `Context` itself is never a Koin *definition* (`startKoin { androidContext(...) }` registers it
 * as a special bean outside the normal graph), so `verify()`'s own doc names this exact case:
 * "extra type, to be bound above the existing definitions" (`VerifyModule.kt`).
 *
 * `SavedStateHandle::class` — phase-06: `ZoneEditorViewModel(savedStateHandle: SavedStateHandle, ...)`.
 * Same shape as `Context`: never a registered Koin definition, synthesized instead by
 * `org.koin.viewmodel.factory.AndroidParametersHolder` from the ViewModel's `CreationExtras` at
 * RUNTIME (a `NavBackStackEntry`'s `SavedStateRegistryOwner`), which `verify()`'s static
 * constructor-reflection cannot see either.
 *
 * `RoutingConfig::class` — routing plan phase-01 Key Insight #6: the real definition lives in
 * `FamilyTrackerApp.appConfigModule`, `private` in `:app`, which this test cannot include (only
 * `dataModule`/`databaseModule`/`uiModule` are). Added in phase-01, before any provider exists,
 * because a `GraphHopperRoutingProvider(…, RoutingConfig)` constructor added later would
 * otherwise make `verify()` throw `MissingKoinDefinitionException` for a binding that is real at
 * runtime — the same shape as `Context`/`SavedStateHandle` above, just for a plain data class
 * instead of a framework type.
 *
 * `File::class` — routing plan phase-04: `OnDevicePolylineCache(routesDir: File, json: Json)`
 * takes a plain `java.io.File`, computed by `DataModule.kt`'s `single { }` lambda
 * (`File(androidContext().filesDir, "routes")`) rather than by a Koin `get()` call — same reason
 * as `Context` above (`verify()`'s static constructor-reflection sees the param type on
 * `OnDevicePolylineCache`'s primary constructor, not how the lambda actually produces the value).
 * Deliberately NOT resolved via `Context` directly: `OnDevicePolylineCache` has to run on a plain
 * JVM unit test (`OnDevicePolylineCacheTest`, LLM.md §11 — no Robolectric), and `Context.filesDir`
 * is an unmocked stub there.
 */
class KoinModulesTest {

    @Test
    fun `all koin modules resolve`() {
        module { includes(dataModule, databaseModule, uiModule) }
            .verify(extraTypes = listOf(Context::class, SavedStateHandle::class, RoutingConfig::class, File::class))
    }
}
