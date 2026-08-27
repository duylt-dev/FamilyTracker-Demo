package com.example.pion.family.tracker.demo.data.di

import com.example.pion.family.tracker.demo.data.local.FamilyTrackerDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Room database + DAO bindings — LLM.md §6. */
val databaseModule = module {
    single { FamilyTrackerDatabase.build(androidContext()) }
    single { get<FamilyTrackerDatabase>().zoneDao() }
    single { get<FamilyTrackerDatabase>().locationPointDao() }
    single { get<FamilyTrackerDatabase>().zoneEventDao() }
    single { get<FamilyTrackerDatabase>().memberDao() }
}
