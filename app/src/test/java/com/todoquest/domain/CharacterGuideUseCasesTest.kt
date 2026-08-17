package com.todoquest.domain

import com.todoquest.domain.model.CharacterStatGuideStatus
import com.todoquest.domain.repository.CharacterGuideRepository
import com.todoquest.domain.usecase.AcknowledgeCharacterStatGuideUseCase
import com.todoquest.domain.usecase.PrepareCharacterStatGuideUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterGuideUseCasesTest {
    @Test
    fun prepareOnlyWhenAutomaticDisplayIsEligibleAndNotAcknowledged() {
        val cases = listOf(
            CharacterStatGuideStatus(
                automaticDisplayEligible = false,
                acknowledged = false,
            ) to false,
            CharacterStatGuideStatus(
                automaticDisplayEligible = false,
                acknowledged = true,
            ) to false,
            CharacterStatGuideStatus(
                automaticDisplayEligible = true,
                acknowledged = false,
            ) to true,
            CharacterStatGuideStatus(
                automaticDisplayEligible = true,
                acknowledged = true,
            ) to false,
        )

        cases.forEach { (status, expected) ->
            val repository = FakeCharacterGuideRepository(status = status)

            assertEquals(expected, PrepareCharacterStatGuideUseCase(repository)())
        }
    }

    @Test
    fun acknowledgePassesThroughSuccessfulFirstConfirmation() {
        val repository = FakeCharacterGuideRepository(acknowledgementResults = listOf(true))

        val result = AcknowledgeCharacterStatGuideUseCase(repository)()

        assertTrue(result)
        assertEquals(1, repository.acknowledgementCalls)
    }

    @Test
    fun acknowledgePassesThroughRepositoryFailure() {
        val repository = FakeCharacterGuideRepository(acknowledgementResults = listOf(false))

        val result = AcknowledgeCharacterStatGuideUseCase(repository)()

        assertFalse(result)
        assertEquals(1, repository.acknowledgementCalls)
    }

    @Test
    fun duplicateAcknowledgementUsesTheRepositoryIdempotencyContract() {
        val repository = FakeCharacterGuideRepository(
            acknowledgementResults = listOf(true, true),
        )
        val useCase = AcknowledgeCharacterStatGuideUseCase(repository)

        assertTrue(useCase())
        assertTrue(useCase())
        assertEquals(2, repository.acknowledgementCalls)
    }

    private class FakeCharacterGuideRepository(
        private val status: CharacterStatGuideStatus = CharacterStatGuideStatus(
            automaticDisplayEligible = false,
            acknowledged = false,
        ),
        acknowledgementResults: List<Boolean> = emptyList(),
    ) : CharacterGuideRepository {
        private val acknowledgementResults = ArrayDeque(acknowledgementResults)

        var acknowledgementCalls: Int = 0
            private set

        override fun statAllocationGuideStatus(): CharacterStatGuideStatus = status

        override fun acknowledgeStatAllocationGuide(): Boolean {
            acknowledgementCalls += 1
            return acknowledgementResults.removeFirst()
        }
    }
}
