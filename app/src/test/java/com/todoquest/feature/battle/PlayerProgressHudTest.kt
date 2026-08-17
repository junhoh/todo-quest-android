package com.todoquest.feature.battle

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerProgressHudTest {
    @Test
    fun progressUsesRatioWhenRequiredExperienceIsPositive() {
        assertEquals(0.4f, calculatePlayerProgress(currentExp = 40, requiredExp = 100), 0f)
    }

    @Test
    fun progressClampsCurrentExperienceToValidRange() {
        assertEquals(0f, calculatePlayerProgress(currentExp = -1, requiredExp = 100), 0f)
        assertEquals(0f, calculatePlayerProgress(currentExp = 0, requiredExp = 100), 0f)
        assertEquals(1f, calculatePlayerProgress(currentExp = 101, requiredExp = 100), 0f)
    }

    @Test
    fun progressIsZeroWhenRequiredExperienceIsNotPositive() {
        assertEquals(0f, calculatePlayerProgress(currentExp = 40, requiredExp = 0), 0f)
        assertEquals(0f, calculatePlayerProgress(currentExp = 40, requiredExp = -1), 0f)
    }

    @Test
    fun compactNumberUsesDeterministicKoreanUnitsWithoutChangingSmallValues() {
        assertEquals("0", compact(0))
        assertEquals("9,999", compact(9_999))
        assertEquals("1만", compact(10_000))
        assertEquals("1.2만", compact(12_500))
        assertEquals("1억", compact(100_000_000))
        assertEquals("123.4억", compact(12_345_678_901))
        assertEquals("1조", compact(1_000_000_000_000))
        assertEquals("1경", compact(10_000_000_000_000_000))
        assertEquals("922.3경", compact(Long.MAX_VALUE))
    }

    @Test
    fun exactNumberKeepsCommaFormattedAccessibilityValue() {
        assertEquals("9,876,543,210", formatExactHudNumber(9_876_543_210))
        assertEquals("9,223,372,036,854,775,807", formatExactHudNumber(Long.MAX_VALUE))
    }

    private fun compact(value: Long) = formatCompactHudNumber(
        value = value,
        unitLabels = HudNumberUnitLabels(
            man = "만",
            eok = "억",
            jo = "조",
            gyeong = "경",
        ),
    )
}
