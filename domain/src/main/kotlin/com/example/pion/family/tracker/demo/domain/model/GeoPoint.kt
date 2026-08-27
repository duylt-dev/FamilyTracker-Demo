package com.example.pion.family.tracker.demo.domain.model

/**
 * A raw geographic coordinate, engine-agnostic — routing plan phase-01 Key Insight #4.
 * `:domain` is a plain `kotlin.jvm` module (LLM.md §2); importing
 * `com.google.android.gms.maps.model.LatLng` here is a compile error, not a review note. `:ui`
 * maps this to `LatLng` at the point it draws something, never earlier.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)
