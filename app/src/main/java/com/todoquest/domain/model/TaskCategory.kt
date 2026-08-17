package com.todoquest.domain.model

import java.util.Locale

object TaskCategory {
    const val DEFAULT: String = "일반"

    val PRESETS: List<String> = listOf(
        DEFAULT,
        "업무",
        "공부",
        "건강",
        "집안일",
        "개인",
    )

    fun normalize(value: String): String {
        val normalized = value.trim()
        if (normalized in PRESETS) return normalized

        return when (normalized.lowercase(Locale.US)) {
            "general" -> DEFAULT
            "work" -> "업무"
            "personal" -> "개인"
            else -> DEFAULT
        }
    }
}
