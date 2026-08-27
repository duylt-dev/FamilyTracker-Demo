package com.example.pion.family.tracker.demo.ui.di

import com.example.pion.family.tracker.demo.domain.usecase.DeleteZoneUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveMembersWithLastLocationUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveRouteForDayUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZoneMembershipUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZoneTimelineUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZonesUseCase
import com.example.pion.family.tracker.demo.domain.usecase.SaveZoneUseCase
import com.example.pion.family.tracker.demo.domain.usecase.StartSimulationUseCase
import com.example.pion.family.tracker.demo.ui.core.logging.AndroidAppLogger
import com.example.pion.family.tracker.demo.ui.core.logging.AppLogger
import com.example.pion.family.tracker.demo.ui.feature.history.HistoryViewModel
import com.example.pion.family.tracker.demo.ui.feature.map.MapViewModel
import com.example.pion.family.tracker.demo.ui.feature.navigation.NavigationViewModel
import com.example.pion.family.tracker.demo.ui.feature.permission.PermissionViewModel
import com.example.pion.family.tracker.demo.ui.feature.timeline.TimelineViewModel
import com.example.pion.family.tracker.demo.ui.feature.zone.ZoneEditorViewModel
import com.example.pion.family.tracker.demo.ui.feature.zone.ZoneListViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.time.Clock

/** Bindings owned by `:ui` — use cases and every screen ViewModel. */
val uiModule = module {
    single<AppLogger> { AndroidAppLogger() }
    // phase-10 — `TimelineViewModel`'s `clock` param needs a REAL Koin binding: `viewModelOf(::X)`
    // resolves every constructor parameter through `get()`, it does not fall back to a Kotlin
    // default value (same reason `AppLogger` above is bound even though `MviViewModel.logger` has
    // a default too — LLM.md §4 điểm 4). `Clock.systemDefaultZone()` for the real app; unit tests
    // construct `TimelineViewModel` directly with a fixed `Clock`, bypassing Koin entirely.
    single<Clock> { Clock.systemDefaultZone() }
    factoryOf(::ObserveZonesUseCase)
    factoryOf(::ObserveMembersWithLastLocationUseCase)
    factoryOf(::ObserveZoneMembershipUseCase)
    factoryOf(::SaveZoneUseCase)
    factoryOf(::DeleteZoneUseCase)
    factoryOf(::ObserveRouteForDayUseCase)
    factoryOf(::StartSimulationUseCase) // phase-09, US-33
    factoryOf(::ObserveZoneTimelineUseCase) // phase-10, US-34
    viewModelOf(::PermissionViewModel)
    viewModelOf(::MapViewModel)
    // `ZoneEditorViewModel`/`HistoryViewModel` nhận `SavedStateHandle` thẳng ở constructor — Koin
    // tự tổng hợp nó qua `AndroidParametersHolder` khi resolve trong scope một `NavBackStackEntry`
    // (LLM.md không cần ghi thêm binding cho `SavedStateHandle`, xem KDoc ở chính ViewModel).
    viewModelOf(::ZoneListViewModel)
    viewModelOf(::ZoneEditorViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::TimelineViewModel)
    // Routing plan phase-05 — `NavigationViewModel` cũng nhận `SavedStateHandle` thẳng ở
    // constructor, cùng cơ chế `AndroidParametersHolder` đã ghi ở trên cho `ZoneEditorViewModel`.
    viewModelOf(::NavigationViewModel)
}
