package com.phonemate.android.domain

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.phonemate.android.R
import java.io.File
import kotlin.math.roundToInt

enum class ResourceType {
    BATTERY,
    NETWORK
}

@Composable
fun ResourceType.label(): String {
    return when (this) {
        ResourceType.BATTERY -> stringResource(R.string.resource_type_battery)
        ResourceType.NETWORK -> stringResource(R.string.resource_type_network)
    }
}

// Formats a raw reading (live usage or a threshold value) in this resource's natural unit -
// battery is a 0-100% charge, network is an unbounded Mbps speed.
fun ResourceType.formatReading(value: Int): String {
    return "$value${readingUnit()}"
}

fun ResourceType.readingUnit(): String {
    return when (this) {
        ResourceType.BATTERY -> "%"
        ResourceType.NETWORK -> "Mbps"
    }
}

// Caps how high a threshold/reading can go for this resource. Battery is a real 0-100% charge, so
// it can't go higher; network has no natural ceiling, so this is just a generous practical bound
// covering typical Wi-Fi/LTE speeds.
fun ResourceType.thresholdMax(): Int {
    return when (this) {
        ResourceType.BATTERY -> 100
        ResourceType.NETWORK -> 500
    }
}

enum class CharacterState {
    LYING,
    STANDING,
    WALKING,
    RUNNING
}

enum class CharacterImageShape {
    ORIGINAL,
    ROUND
}

data class ResourceThresholds(
    val standingPercent: Int = 40,
    val walkingPercent: Int = 60,
    val runningPercent: Int = 80
) {
    fun normalize(maxValue: Int = 100): ResourceThresholds {
        val standing = standingPercent.coerceIn(1, maxValue - 2)
        val walking = walkingPercent.coerceIn(standing + 1, maxValue - 1)
        val running = runningPercent.coerceIn(walking + 1, maxValue)
        return ResourceThresholds(
            standingPercent = standing,
            walkingPercent = walking,
            runningPercent = running
        )
    }

    companion object {
        val Default = ResourceThresholds()

        // Anchored to YouTube's recommended sustained speeds, so each tier maps to something
        // directly observable: 720p needs ~2.5Mbps, 1080p ~5Mbps, 1440p ~9Mbps.
        val NetworkDefault = ResourceThresholds(standingPercent = 3, walkingPercent = 5, runningPercent = 9)
    }
}

data class AppSettings(
    val selectedResourceType: ResourceType = ResourceType.BATTERY,
    val showResourceBar: Boolean = true,
    val showSpeechBubble: Boolean = false,
    val showImageBorder: Boolean = false,
    val useLightText: Boolean = false,
    val characterId: String = "blob",
    val characterImageShape: CharacterImageShape = CharacterImageShape.ORIGINAL,
    val animationSpeedMultiplier: Float = 1.0f,
    val characterScale: Float = 1.0f,
    val overlayX: Int = 32,
    val overlayY: Int = 96,
    val overlayWidth: Int = 220,
    val overlayHeight: Int = 190,
    val batteryThresholds: ResourceThresholds = ResourceThresholds.Default,
    val networkThresholds: ResourceThresholds = ResourceThresholds.NetworkDefault
) {
    fun thresholdsFor(resourceType: ResourceType): ResourceThresholds {
        return when (resourceType) {
            ResourceType.BATTERY -> batteryThresholds
            ResourceType.NETWORK -> networkThresholds
        }
    }

    fun normalizedCharacterScale(): Float {
        return characterScale.coerceIn(MIN_CHARACTER_SCALE, MAX_CHARACTER_SCALE)
    }

    fun scaledOverlayWidth(): Int {
        return (overlayWidth * normalizedCharacterScale()).roundToInt()
            .coerceIn(MIN_OVERLAY_SIZE, MAX_OVERLAY_SIZE)
    }

    fun scaledOverlayHeight(): Int {
        return (overlayHeight * normalizedCharacterScale()).roundToInt()
            .coerceIn(MIN_OVERLAY_SIZE, MAX_OVERLAY_SIZE)
    }

    companion object {
        const val DEFAULT_OVERLAY_WIDTH = 220
        const val DEFAULT_OVERLAY_HEIGHT = 190
        const val MIN_OVERLAY_SIZE = 96
        const val MAX_OVERLAY_SIZE = 720
        const val MIN_CHARACTER_SCALE = 0.5f
        const val MAX_CHARACTER_SCALE = 4.0f
        const val DEFAULT_CHARACTER_SCALE = 1.0f
        const val MIN_ANIMATION_SPEED = 0.5f
        const val MAX_ANIMATION_SPEED = 4.0f
        const val DEFAULT_ANIMATION_SPEED = 1.0f
    }
}

sealed interface CharacterImageSource {
    val key: String

    data class Asset(val path: String) : CharacterImageSource {
        override val key: String = "asset:$path"
    }

    data class FilePath(val path: String, val originalFileName: String? = null) : CharacterImageSource {
        // Replacing an image keeps the same file name (e.g. sleeping.gif), so the modification
        // time has to be part of the key or cached views/thumbnails would keep showing the old file.
        override val key: String = "file:$path:${File(path).lastModified()}"
    }
}

data class CharacterDefinition(
    val id: String,
    val displayName: String,
    val isCustom: Boolean,
    val sleepingImage: CharacterImageSource?,
    val standingImage: CharacterImageSource?,
    val walkingImage: CharacterImageSource?,
    val runningImage: CharacterImageSource?
) {
    fun imageFor(state: CharacterState): CharacterImageSource {
        val stateImages = listOf(sleepingImage, standingImage, walkingImage, runningImage)
        val stateIndex = state.ordinal

        // An image applies from its registered state upward until a higher state supplies another
        // image. A valid custom character always has the minimum-state (LYING) image.
        return (stateIndex downTo 0).firstNotNullOfOrNull(stateImages::get)
            ?: error("Character $id has no image")
    }
}
