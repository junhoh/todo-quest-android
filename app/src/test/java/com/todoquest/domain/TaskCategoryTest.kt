package com.todoquest.domain

import com.todoquest.domain.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskCategoryTest {
    @Test
    fun defaultCategoryIsGeneralInKorean() {
        assertEquals("일반", TaskCategory.DEFAULT)
    }

    @Test
    fun presetsProvideKoreanTaskCategories() {
        assertEquals(
            listOf("일반", "업무", "공부", "건강", "집안일", "개인"),
            TaskCategory.PRESETS,
        )
    }

    @Test
    fun normalizeTrimsPresetValues() {
        assertEquals("업무", TaskCategory.normalize(" 업무 "))
    }

    @Test
    fun normalizeFallsBackToDefaultForBlankOrUnknownValues() {
        assertEquals("일반", TaskCategory.normalize(""))
        assertEquals("일반", TaskCategory.normalize("General"))
    }
}
