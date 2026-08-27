package com.example.pion.family.tracker.demo.data.local

import androidx.room.TypeConverter
import com.example.pion.family.tracker.demo.domain.model.EventSource
import com.example.pion.family.tracker.demo.domain.model.ZoneEventType
import java.time.Instant

/**
 * `Instant` <-> epoch millis `Long`, `ZoneEventType`/`EventSource` <-> `String` — registered
 * once at `@Database` level (`FamilyTrackerDatabase`), applies to every entity. Storing millis
 * (not ISO strings) is what keeps the 7-day DB under 20 MB — PRD §7.1.
 */
class Converters {
    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun zoneEventTypeToName(value: ZoneEventType?): String? = value?.name

    @TypeConverter
    fun nameToZoneEventType(value: String?): ZoneEventType? = value?.let(ZoneEventType::valueOf)

    @TypeConverter
    fun eventSourceToName(value: EventSource?): String? = value?.name

    @TypeConverter
    fun nameToEventSource(value: String?): EventSource? = value?.let(EventSource::valueOf)
}
