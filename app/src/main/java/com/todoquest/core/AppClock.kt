package com.todoquest.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface AppClock {
    val zoneId: ZoneId

    fun now(): Instant

    fun today(): LocalDate
}

class SystemAppClock(
    override val zoneId: ZoneId = ZoneId.systemDefault(),
) : AppClock {
    override fun now(): Instant = Instant.now()

    override fun today(): LocalDate = LocalDate.now(zoneId)
}
