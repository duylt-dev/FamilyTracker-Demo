package com.example.pion.family.tracker.demo.ui.feature.zone

import com.example.pion.family.tracker.demo.domain.model.AppResult
import com.example.pion.family.tracker.demo.domain.model.Zone
import com.example.pion.family.tracker.demo.domain.usecase.DeleteZoneUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZoneMembershipUseCase
import com.example.pion.family.tracker.demo.domain.usecase.ObserveZonesUseCase
import com.example.pion.family.tracker.demo.domain.usecase.SaveZoneUseCase
import com.example.pion.family.tracker.demo.ui.core.mvi.MviViewModel
import kotlinx.coroutines.flow.combine

/**
 * US-12→US-15. `init` gộp `observeZones()` + `observeZoneMembership()` bằng `combine()` — khác
 * `MapViewModel.init` (phase-05, 3 nguồn ĐỘC LẬP, mỗi cái một `collectSafely` riêng): ở đây
 * [ZoneListItem.membersInside] của một dòng phụ thuộc TRỰC TIẾP vào cặp (zone, map thành viên
 * đang ở trong) của CÙNG một lần phát, nên phải ghép trước khi vào `setState` — combine() chỉ
 * dựng `Flow`, coroutine thật vẫn đi qua `collectSafely` như luật MVI doc §1 đòi.
 *
 * `latestZones` là field thường (không phải `UiState`) — bộ nhớ đệm để dựng lại [Zone] đầy đủ khi
 * [ZoneListIntent.NotifyToggled] cần ghi cả `notifyOnEnter`/`notifyOnExit`, tránh phải thêm một
 * repository method mới chỉ để "sửa 2 cờ" (YAGNI — [SaveZoneUseCase] đã đủ).
 */
class ZoneListViewModel(
    observeZones: ObserveZonesUseCase,
    observeZoneMembership: ObserveZoneMembershipUseCase,
    private val saveZoneUseCase: SaveZoneUseCase,
    private val deleteZoneUseCase: DeleteZoneUseCase,
) : MviViewModel<ZoneListState, ZoneListIntent, ZoneListEffect>(ZoneListState()) {

    private var latestZones: List<Zone> = emptyList()

    init {
        combine(observeZones(), observeZoneMembership()) { zones, membersByZone -> zones to membersByZone }
            .collectSafely { (zones, membersByZone) ->
                latestZones = zones
                setState {
                    copy(
                        isLoading = false,
                        zones = zones.map { zone ->
                            ZoneListItem(
                                id = zone.id,
                                name = zone.name,
                                radiusMeters = zone.radiusMeters,
                                membersInside = membersByZone[zone.id].orEmpty().map { member ->
                                    ZoneMemberChip(id = member.id, name = member.name, colorArgb = member.colorArgb)
                                },
                                notifyEnabled = zone.notifyOnEnter || zone.notifyOnExit,
                            )
                        },
                    )
                }
            }
    }

    override fun onIntent(intent: ZoneListIntent) {
        when (intent) {
            is ZoneListIntent.ZoneTapped -> sendEffect(ZoneListEffect.OpenEditor(intent.zoneId))
            is ZoneListIntent.NotifyToggled -> onNotifyToggled(intent.zoneId, intent.enabled)
            is ZoneListIntent.DeleteRequested -> setState { copy(pendingDeleteId = intent.zoneId) }
            ZoneListIntent.DeleteConfirmed -> onDeleteConfirmed()
            ZoneListIntent.DeleteCancelled -> setState { copy(pendingDeleteId = null) }
            ZoneListIntent.CreateTapped -> sendEffect(ZoneListEffect.OpenEditor(null))
        }
    }

    private fun onNotifyToggled(zoneId: String, enabled: Boolean) {
        val zone = latestZones.firstOrNull { it.id == zoneId } ?: return
        val updated = zone.copy(notifyOnEnter = enabled, notifyOnExit = enabled)
        launchSafely(onError = { sendEffect(ZoneListEffect.ShowMessage(it)) }) {
            when (val result = saveZoneUseCase(updated)) {
                // Không setState ở nhánh Success — observeZones() re-emit là nguồn sự thật duy
                // nhất (MVI doc §3 "Room is the single source of truth").
                is AppResult.Success -> Unit
                is AppResult.Failure -> sendEffect(ZoneListEffect.ShowMessage(result.error))
            }
        }
    }

    private fun onDeleteConfirmed() {
        val zoneId = currentState.pendingDeleteId ?: return
        launchSafely(
            onError = {
                setState { copy(pendingDeleteId = null) }
                sendEffect(ZoneListEffect.ShowMessage(it))
            },
        ) {
            when (val result = deleteZoneUseCase(zoneId)) {
                is AppResult.Success -> setState { copy(pendingDeleteId = null) }
                is AppResult.Failure -> {
                    setState { copy(pendingDeleteId = null) }
                    sendEffect(ZoneListEffect.ShowMessage(result.error))
                }
            }
        }
    }
}
