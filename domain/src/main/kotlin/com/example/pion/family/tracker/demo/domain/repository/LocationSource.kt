package com.example.pion.family.tracker.demo.domain.repository

import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import kotlinx.coroutines.flow.Flow

/**
 * PRD §8 — exact signature. Two `:data` implementations (real + simulated, LLM.md §8.4); both
 * feed the same [Flow] through the same filter/evaluator/repository chain. Implemented from
 * phase-04 (`FusedLocationSource`) / phase-09 (`SimulatedLocationSource`) onward.
 */
interface LocationSource {
    fun stream(): Flow<LocationPoint>
}
