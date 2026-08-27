package com.example.pion.family.tracker.demo.domain.model

/** Which routing backend [RoutingConfig.engine] selects — routing plan phase-01 Key Insight #3. */
enum class RoutingEngine { GRAPHHOPPER, VALHALLA }

/**
 * Routing settings read from `local.properties` at build time, registered into Koin by `:app`
 * (`FamilyTrackerApp.appConfigModule`) — routing plan phase-01 Step 11.
 *
 * Lives in `:domain/model/`, not a new `config` package: `LLM.md` §12 forbids inventing a
 * package for something that is just an immutable data model both `:data` (builds the provider)
 * and `:app` (loads the values) need to see.
 *
 * All fields required, no defaults — an immutable `data class`, same rule as every other
 * `:domain` model (Step 5).
 */
data class RoutingConfig(
    val engine: RoutingEngine,
    val graphHopperApiKey: String,
    val stadiaApiKey: String,
    val valhallaBaseUrl: String,
)
