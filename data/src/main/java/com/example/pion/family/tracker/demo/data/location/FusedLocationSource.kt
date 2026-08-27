package com.example.pion.family.tracker.demo.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.repository.LocationSource
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant

/**
 * `LocationSource` thật — phase-04 Requirements, Implementation Step 7. `PRIORITY_HIGH_ACCURACY`,
 * chu kỳ [TrackingConstants.LOCATION_INTERVAL_MS], `minUpdateDistance`
 * [TrackingConstants.MIN_DISTANCE_M]. Đây CHỈ là nguồn thô — [LocationPointProcessor] mới là nơi
 * áp `LocationFilter` (LLM.md §8.3), không lọc ở đây.
 */
class FusedLocationSource(private val context: Context) : LocationSource {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override fun stream(): Flow<LocationPoint> = callbackFlow {
        if (!hasFineLocationPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TrackingConstants.LOCATION_INTERVAL_MS,
        ).setMinUpdateDistanceMeters(TrackingConstants.MIN_DISTANCE_M.toFloat()).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toLocationPoint()) }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun Location.toLocationPoint(): LocationPoint = LocationPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        speedMps = speed,
        bearingDegrees = bearing,
        recordedAt = Instant.ofEpochMilli(time),
    )
}
