package com.todoquest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val QuestColorScheme = darkColorScheme(
    primary = QuestGold,
    secondary = QuestXp,
    error = QuestDanger,
    background = QuestBackground,
    surface = QuestSurface,
    outline = QuestOutline,
    onPrimary = QuestBackground,
    onSecondary = QuestBackground,
    onError = QuestTextPrimary,
    onBackground = QuestTextPrimary,
    onSurface = QuestTextPrimary,
    onSurfaceVariant = QuestTextSecondary,
)

@Composable
fun TodoQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QuestColorScheme,
        typography = TodoQuestTypography,
        content = content,
    )
}
