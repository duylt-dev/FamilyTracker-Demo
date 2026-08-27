package com.example.pion.family.tracker.demo.data.local.mapper

import com.example.pion.family.tracker.demo.data.local.entity.MemberEntity
import com.example.pion.family.tracker.demo.domain.model.Member

fun MemberEntity.toDomain(): Member = Member(id = id, name = name, colorArgb = colorArgb, isSelf = isSelf)

fun Member.toEntity(): MemberEntity = MemberEntity(id = id, name = name, colorArgb = colorArgb, isSelf = isSelf)
