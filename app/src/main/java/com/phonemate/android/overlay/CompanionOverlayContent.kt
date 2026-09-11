package com.phonemate.android.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.phonemate.android.domain.AppSettings
import com.phonemate.android.domain.CharacterDefinition
import com.phonemate.android.domain.CharacterImageSource
import com.phonemate.android.domain.CharacterImageShape
import com.phonemate.android.domain.CharacterState
import com.phonemate.android.domain.ResourceType
import com.phonemate.android.domain.formatReading
import com.phonemate.android.domain.label
import com.phonemate.android.domain.loadCharacterDrawable
import com.phonemate.android.domain.thresholdMax
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView

@Composable
fun CompanionOverlayContent(
    settings: AppSettings,
    usagePercent: Int,
    characterState: CharacterState,
    character: CharacterDefinition,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (settings.showSpeechBubble) {
                SpeechBubble(
                    resourceType = settings.selectedResourceType,
                    usagePercent = usagePercent,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
            ) {
                val imageModifier = if (settings.characterImageShape == CharacterImageShape.ROUND) {
                    Modifier
                        .align(Alignment.Center)
                        .size(minOf(maxWidth, maxHeight))
                        .clip(CircleShape)
                        .then(
                            if (settings.showImageBorder) {
                                Modifier.border(1.dp, Color(0xFFFF2D55), CircleShape)
                            } else {
                                Modifier
                            }
                        )
                } else {
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (settings.showImageBorder) {
                                Modifier.border(1.dp, Color(0xFFFF2D55))
                            } else {
                                Modifier
                            }
                        )
                }

                CharacterImageView(
                    source = character.imageFor(characterState),
                    speedMultiplier = settings.animationSpeedMultiplier,
                    imageShape = settings.characterImageShape,
                    modifier = imageModifier
                )
            }

            if (settings.showResourceBar) {
                ResourceBar(
                    resourceType = settings.selectedResourceType,
                    usagePercent = usagePercent,
                    useLightText = settings.useLightText,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SpeechBubble(
    resourceType: ResourceType,
    usagePercent: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xF2FFFFFF),
        contentColor = Color(0xFF111827),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "${resourceType.label()} ${resourceType.formatReading(usagePercent)}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ResourceBar(
    resourceType: ResourceType,
    usagePercent: Int,
    useLightText: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            text = "${resourceType.label()} ${resourceType.formatReading(usagePercent)}",
            color = if (useLightText) Color(0xFFF9FAFB) else Color(0xFF111827),
            style = MaterialTheme.typography.labelSmall
        )
        LinearProgressIndicator(
            progress = {
                val maxValue = resourceType.thresholdMax()
                usagePercent.coerceIn(0, maxValue) / maxValue.toFloat()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = Color(0xFF14B8A6),
            trackColor = Color(0x66111827)
        )
    }
}

@Composable
private fun CharacterImageView(
    source: CharacterImageSource,
    speedMultiplier: Float,
    imageShape: CharacterImageShape,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            GifImageView(viewContext).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
        },
        update = { view ->
            view.scaleType = when (imageShape) {
                CharacterImageShape.ORIGINAL -> android.widget.ImageView.ScaleType.FIT_CENTER
                CharacterImageShape.ROUND -> android.widget.ImageView.ScaleType.CENTER_CROP
            }
            view.adjustViewBounds = imageShape == CharacterImageShape.ORIGINAL

            if (view.tag != source.key) {
                view.setImageDrawable(loadCharacterDrawable(context, source))
                view.tag = source.key
            }

            (view.drawable as? GifDrawable)?.setSpeed(speedMultiplier.coerceIn(0.5f, 4.0f))
        }
    )
}
