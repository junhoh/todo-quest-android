package com.todoquest.ui.character

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor

@Composable
fun LayeredCharacterSprite(
    renderState: CharacterRenderState,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    verticalAnchor: Float? = null,
) {
    require(verticalAnchor == null || verticalAnchor.isFinite() && verticalAnchor in 0f..1f) {
        "verticalAnchor must be null or a finite normalized value"
    }
    val assetManager = LocalContext.current.assets
    val composer = remember(assetManager) { CharacterBitmapComposer(assetManager) }
    val image = remember(composer, renderState) {
        composer.compose(renderState)?.asImageBitmap()
    }
    val semanticModifier = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics {
            this.contentDescription = contentDescription
        }
    }

    Canvas(modifier = semanticModifier) {
        val composite = image ?: return@Canvas
        val availableSize = minOf(size.width, size.height)
        if (availableSize <= 0f) return@Canvas

        val integerScale = floor(availableSize / CharacterCanvasSize).toInt()
        val destinationSize = if (integerScale > 0) {
            CharacterCanvasSize * integerScale
        } else {
            floor(availableSize).toInt().coerceAtLeast(1)
        }
        val destinationOffset = IntOffset(
            x = ((size.width - destinationSize) / 2f).toInt(),
            y = verticalAnchor?.let { anchor ->
                (size.height * anchor - destinationSize * anchor).toInt()
            } ?: ((size.height - destinationSize) / 2f).toInt(),
        )
        drawImage(
            image = composite,
            dstOffset = destinationOffset,
            dstSize = IntSize(destinationSize, destinationSize),
            filterQuality = FilterQuality.None,
        )
    }
}

private const val CharacterCanvasSize = 64
