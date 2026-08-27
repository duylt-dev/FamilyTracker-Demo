package com.example.pion.family.tracker.demo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** LLM.md §9 — table `members`. Exactly one row has `isSelf = true`. */
@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Int,
    val isSelf: Boolean,
)
