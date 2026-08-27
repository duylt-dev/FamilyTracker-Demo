package com.example.pion.family.tracker.demo.data.perf

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pion.family.tracker.demo.data.local.FamilyTrackerDatabase
import com.example.pion.family.tracker.demo.data.local.entity.LocationPointEntity
import com.example.pion.family.tracker.demo.data.local.mapper.toDomain
import com.example.pion.family.tracker.demo.domain.model.LocationPoint
import com.example.pion.family.tracker.demo.domain.model.TrackSession
import com.example.pion.family.tracker.demo.domain.tracking.RouteSplitter
import com.example.pion.family.tracker.demo.domain.tracking.RouteStats
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fix-phase-08 — measures the FULL History pipeline at PRD §7.1's stated scale (8,640 raw
 * points/day) stage by stage, on-device (real Room, real SQLite, real `PolyUtil`/`LatLng`).
 *
 * Reason this test exists: `HistoryMap.kt`'s `history_rendered.renderMs` only timed one
 * `withFrameNanos` wait AFTER a `TrackSession` was already fully resolved in memory — it never
 * measured the Room query, entity→domain mapping, `RouteSplitter.split`, `RouteStats.of`, or
 * `PolyUtil.simplify` that PRD §7.1 ("< 1s từ lúc chọn ngày") actually cares about. See
 * `reports/fix-phase-08-report.md` for the full writeup.
 *
 * Lives in `:data` (not `:domain`, which is a pure-JVM module with no instrumented-test
 * capability at all — see `domain/build.gradle.kts`, `kotlin.jvm` plugin only). `PolyUtil`/`LatLng`
 * need `maps-compose-utils`, added here as `androidTestImplementation` only — test-scoped, not
 * shipped in the app or `:data`'s production AAR (`data/build.gradle.kts`).
 *
 * Seed data generation lives in [HistoryPipelineTestFixture].kt / timing helpers too, to keep this
 * file under LLM.md §5's 200-line limit.
 */
@RunWith(AndroidJUnit4::class)
class HistoryPipelineScaleTest {

    private lateinit var db: FamilyTrackerDatabase
    private lateinit var range: SeededRange

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // File-backed (not in-memory): real disk I/O, matches the production `family_tracker.db`
        // shape. This androidTest builds its own self-instrumenting test APK/data dir — cannot
        // collide with the real app's database.
        db = Room.databaseBuilder(context, FamilyTrackerDatabase::class.java, PERF_DB_NAME).build()
        range = seedOneDayAt8640Points(db)
    }

    @After
    fun tearDown() {
        db.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(PERF_DB_NAME)
    }

    @Test
    fun measuresEachPipelineStageAt8640Points() = runBlocking {
        val dao = db.locationPointDao()

        // Stage 1 — Room query.
        val queryTimings = mutableListOf<Long>()
        var entities: List<LocationPointEntity> = emptyList()
        repeat(PERF_ITERATIONS) {
            val start = System.nanoTime()
            entities = dao.observeBetween(PERF_MEMBER_ID, range.fromMillis, range.toMillis).first()
            queryTimings += elapsedMs(start)
        }
        assertEquals("seed didn't produce the expected row count", PERF_POINT_COUNT, entities.size)

        // Stage 2 — entity -> :domain mapping.
        val mapTimings = mutableListOf<Long>()
        var domainPoints: List<LocationPoint> = emptyList()
        repeat(PERF_ITERATIONS) {
            val start = System.nanoTime()
            domainPoints = entities.map { it.toDomain() }
            mapTimings += elapsedMs(start)
        }

        // Stage 3 — RouteSplitter.split.
        val splitTimings = mutableListOf<Long>()
        var sessions: List<TrackSession> = emptyList()
        repeat(PERF_ITERATIONS) {
            val start = System.nanoTime()
            sessions = RouteSplitter.split(PERF_MEMBER_ID, domainPoints)
            splitTimings += elapsedMs(start)
        }
        assertEquals("seed's 20-min gap should split into exactly 2 sessions", 2, sessions.size)
        assertEquals(PERF_SESSION1_COUNT, sessions[0].points.size)
        assertEquals(PERF_POINT_COUNT - PERF_SESSION1_COUNT, sessions[1].points.size)

        // HistoryViewModel.applySessions picks the LONGEST session (by distanceMeters) by default —
        // that is the only session a first render actually pays RouteStats/PolyUtil for.
        val defaultSelectedIdx = sessions.indices.maxByOrNull { sessions[it].distanceMeters } ?: 0
        val defaultSession = sessions[defaultSelectedIdx]

        // Stage 4 — RouteStats.of for the default-selected session (matches HistoryContract.stats,
        // computed only for `selectedSession`, not every session in the list — SessionList reads
        // `session.distanceMeters` straight off the split result for the other rows, no RouteStats
        // call).
        val statsTimings = mutableListOf<Long>()
        repeat(PERF_ITERATIONS) {
            val start = System.nanoTime()
            RouteStats.of(defaultSession)
            statsTimings += elapsedMs(start)
        }

        // Stage 5 — PolyUtil.simplify, exact call RoutePolyline.kt makes (tolerance = 10.0m).
        // Measured PER SESSION (not summed): a real render only ever simplifies the ONE currently
        // selected session (RoutePolyline gets `session.points` for a single TrackSession), never
        // all sessions in the day at once. HistoryViewModel.applySessions defaults selection to the
        // session with the largest `distanceMeters` — that is the one a real "day selected" render
        // pays stage 5 for, so that's what's used for the realistic pipeline total below.
        val simplifyTimingsBySession = sessions.map { mutableListOf<Long>() }
        val inOutBySession = sessions.map { IntArray(2) } // [rawIn, simplifiedOut]
        repeat(PERF_ITERATIONS) {
            sessions.forEachIndexed { idx, session ->
                val latLngs = session.points.map { LatLng(it.latitude, it.longitude) }
                val start = System.nanoTime()
                val simplified =
                    if (latLngs.size > 2) PolyUtil.simplify(latLngs, PERF_SIMPLIFY_TOLERANCE_M) else latLngs
                simplifyTimingsBySession[idx] += elapsedMs(start)
                inOutBySession[idx][0] = latLngs.size
                inOutBySession[idx][1] = simplified.size
            }
        }
        val simplifyMedianSelectedMs = median(simplifyTimingsBySession[defaultSelectedIdx])

        val stageMedians = mapOf(
            "1_room_query" to median(queryTimings),
            "2_entity_to_domain_mapping" to median(mapTimings),
            "3_route_splitter_split" to median(splitTimings),
            "4_route_stats_of_selected_session" to median(statsTimings),
            "5_polyutil_simplify_default_selected_session" to simplifyMedianSelectedMs,
        )
        val totalMedianMs = stageMedians.values.sum()

        Log.i(
            PERF_TAG,
            "history_pipeline_scale_test pointCount=$PERF_POINT_COUNT sessionCount=${sessions.size} " +
                "iterations=$PERF_ITERATIONS defaultSelectedSessionIdx=$defaultSelectedIdx " +
                stageMedians.entries.joinToString(" ") { (k, v) -> "${k}Ms=$v" } +
                " totalMedianMs=$totalMedianMs" +
                " perSessionSimplify(" +
                sessions.indices.joinToString(", ") { idx ->
                    "session$idx: in=${inOutBySession[idx][0]} out=${inOutBySession[idx][1]} " +
                        "medianMs=${median(simplifyTimingsBySession[idx])} raw=${simplifyTimingsBySession[idx]}"
                } +
                ")" +
                " rawTimingsMs(query=$queryTimings map=$mapTimings split=$splitTimings stats=$statsTimings)",
        )

        // PRD §7.1: "Vẽ lộ trình một ngày | < 1s từ lúc chọn ngày" — the gate this test exists to
        // enforce. If this regresses, phase-11's performance gate must catch it; `history_rendered`
        // alone (single-frame wait) cannot. This total is the realistic one-render cost: whole-day
        // query/map/split (unavoidable per render) + stats/simplify for ONLY the default-selected
        // session (the one PolyUtil/RouteStats actually run for on first render).
        assertTrue(
            "Full History pipeline at $PERF_POINT_COUNT points (default-selected session) took " +
                "${totalMedianMs}ms median, PRD §7.1 budget is < 1000ms",
            totalMedianMs < 1_000L,
        )
    }
}
