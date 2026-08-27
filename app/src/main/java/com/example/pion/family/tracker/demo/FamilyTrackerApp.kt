package com.example.pion.family.tracker.demo

import android.app.Application
import com.example.pion.family.tracker.demo.data.di.databaseModule
import com.example.pion.family.tracker.demo.data.di.dataModule
import com.example.pion.family.tracker.demo.data.notification.NotificationChannels
import com.example.pion.family.tracker.demo.data.seed.DemoDataSeeder
import com.example.pion.family.tracker.demo.data.util.FtdLog
import com.example.pion.family.tracker.demo.domain.model.RoutingConfig
import com.example.pion.family.tracker.demo.domain.model.RoutingEngine
import com.example.pion.family.tracker.demo.domain.usecase.PurgeOldHistoryUseCase
import com.example.pion.family.tracker.demo.ui.core.logging.FtdLog as UiFtdLog
import com.example.pion.family.tracker.demo.ui.di.uiModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The only file in the repo that sees both `:data` and `:ui` Koin modules — see LLM.md §6.
 * Also runs demo seeding + 7-day history purge once at startup (PRD §3.3, §10). Both use cases
 * live in `:domain`/`:data`, which cannot import `android.util.Log` — the `FTD_EVENT
 * purge_completed` line has to be logged here, at the one place allowed to see both worlds.
 *
 * phase-07 Implementation Step 4: tạo notification channels ở mỗi lần app khởi động.
 *
 * fix-zone-follows-members: bước "đăng ký lại geofence từ Room" đã biến mất cùng cả đường phát
 * hiện Geofencing API (LLM.md §8.1) — API đó chỉ bắn transition cho THIẾT BỊ đang chạy app, tức là
 * chỉ cho self, mà chủ thể của zone giờ là các thành viên được theo dõi.
 */
class FamilyTrackerApp : Application(), KoinComponent {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val demoDataSeeder: DemoDataSeeder by inject()
    private val purgeOldHistoryUseCase: PurgeOldHistoryUseCase by inject()

    override fun onCreate() {
        super.onCreate()
        // fix-phase-11 (LLM.md §13 Fixed #22): set BEFORE startKoin, and by direct assignment, not
        // through Koin — FtdLog must never require the DI graph to be up just to stay silent.
        FtdLog.debugBuild = BuildConfig.DEBUG
        UiFtdLog.debugBuild = BuildConfig.DEBUG
        startKoin {
            androidContext(this@FamilyTrackerApp)
            modules(dataModule, databaseModule, uiModule, appConfigModule)
        }
        NotificationChannels.ensureAll(this)
        applicationScope.launch {
            demoDataSeeder.seedIfEmpty()
            val result = purgeOldHistoryUseCase()
            FtdLog.d(
                "FTD_EVENT",
                "purge_completed deletedPoints=${result.deletedPoints} deletedEvents=${result.deletedEvents}",
            )
        }
    }
}

/**
 * phase-09, US-33 — `:ui` deliberately does NOT enable its own `buildConfig` (Key Insight #7:
 * a second `BuildConfig` class with no `SIMULATOR_ENABLED` field would exist, and the wrong one
 * is the one closer at hand for an import). `SimulateRouteButton` (`:ui`) reads this single
 * `Boolean` binding through `koinInject(named("simulatorEnabled"))` instead. `FamilyTrackerApp.kt`
 * is the one file in the repo allowed to see `:app`'s own `BuildConfig` AND register it into the
 * shared Koin graph (§6 "chỗ DUY NHẤT biết cả hai").
 *
 * `debugBuild` (G7 gate for `FtdLog`) used to live here too, as a second Koin boolean — moved OFF
 * Koin in fix-phase-11 (LLM.md §13 Fixed #22): a logging gate must work even before Koin has
 * started (every `:data:connectedDebugAndroidTest` runs with no Koin at all). `FtdLog` in both
 * `:data` and `:ui` now reads a plain `@Volatile var`, set by direct assignment in `onCreate`
 * above, before `startKoin` runs — not through this module.
 *
 * `RoutingConfig` (routing plan phase-01 Step 11) is a plain constructor-injected binding, not a
 * qualified primitive like `simulatorEnabled` — it goes through Koin's normal graph, which is
 * exactly why `KoinModulesTest`'s `extraTypes` needs `RoutingConfig::class` (Key Insight #6): the
 * definition lives in this `private` module, which `KoinModulesTest` cannot include, so any
 * phase-02+ provider taking a `RoutingConfig` constructor param would otherwise fail
 * `verify()`'s static analysis even though the binding is real at runtime.
 * `RoutingEngine.valueOf(...)` throws on a typo'd `ROUTING_ENGINE` — deliberate, see
 * `app/build.gradle.kts` Step 10/11 comment.
 */
private val appConfigModule = module {
    single<Boolean>(named("simulatorEnabled")) { BuildConfig.SIMULATOR_ENABLED }
    single {
        RoutingConfig(
            engine = RoutingEngine.valueOf(BuildConfig.ROUTING_ENGINE),
            graphHopperApiKey = BuildConfig.GRAPHHOPPER_API_KEY,
            stadiaApiKey = BuildConfig.STADIA_API_KEY,
            valhallaBaseUrl = BuildConfig.VALHALLA_BASE_URL,
        )
    }
}
