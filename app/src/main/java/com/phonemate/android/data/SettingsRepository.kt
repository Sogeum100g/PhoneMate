package com.phonemate.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.phonemate.android.domain.AppSettings
import com.phonemate.android.domain.CharacterImageShape
import com.phonemate.android.domain.ResourceThresholds
import com.phonemate.android.domain.ResourceType
import com.phonemate.android.domain.thresholdMax
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.phonemateSettingsDataStore by preferencesDataStore(name = "phonemate_settings")

class SettingsRepository(context: Context) {
    private val dataStore: DataStore<Preferences> = context.applicationContext.phonemateSettingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map(::toSettings)

    val onboardingSeen: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[Keys.OnboardingSeen] ?: false }

    suspend fun markOnboardingSeen() {
        dataStore.edit { preferences ->
            preferences[Keys.OnboardingSeen] = true
        }
    }

    suspend fun updateSelectedResource(resourceType: ResourceType) {
        dataStore.edit { preferences ->
            preferences[Keys.SelectedResource] = resourceType.name
        }
    }

    suspend fun updateVisibility(
        showResourceBar: Boolean,
        showSpeechBubble: Boolean,
        showImageBorder: Boolean? = null,
        useLightText: Boolean? = null
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.ShowResourceBar] = showResourceBar
            preferences[Keys.ShowSpeechBubble] = showSpeechBubble
            showImageBorder?.let { preferences[Keys.ShowImageBorder] = it }
            useLightText?.let { preferences[Keys.UseLightText] = it }
        }
    }

    suspend fun updateImageBorder(showImageBorder: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ShowImageBorder] = showImageBorder
        }
    }

    suspend fun updateCharacter(characterId: String) {
        dataStore.edit { preferences ->
            preferences[Keys.CharacterId] = characterId.ifBlank { "blob" }
        }
    }

    suspend fun updateCharacterImageShape(imageShape: CharacterImageShape) {
        dataStore.edit { preferences ->
            preferences[Keys.CharacterImageShape] = imageShape.name
        }
    }

    suspend fun updateAnimationSpeed(speedMultiplier: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.AnimationSpeed] = speedMultiplier.coerceIn(
                AppSettings.MIN_ANIMATION_SPEED,
                AppSettings.MAX_ANIMATION_SPEED
            )
        }
    }

    suspend fun updateCharacterScale(scale: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.CharacterScale] = scale.coerceIn(
                AppSettings.MIN_CHARACTER_SCALE,
                AppSettings.MAX_CHARACTER_SCALE
            )
        }
    }

    suspend fun updateOverlayPosition(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.OverlayX] = x
            preferences[Keys.OverlayY] = y
        }
    }

    suspend fun updateOverlayPlacement(x: Int, y: Int, width: Int, height: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.OverlayX] = x
            preferences[Keys.OverlayY] = y
            preferences[Keys.OverlayWidth] = width.coerceIn(
                AppSettings.MIN_OVERLAY_SIZE,
                AppSettings.MAX_OVERLAY_SIZE
            )
            preferences[Keys.OverlayHeight] = height.coerceIn(
                AppSettings.MIN_OVERLAY_SIZE,
                AppSettings.MAX_OVERLAY_SIZE
            )
        }
    }

    suspend fun updateThresholds(resourceType: ResourceType, thresholds: ResourceThresholds) {
        val normalized = thresholds.normalize(resourceType.thresholdMax())
        dataStore.edit { preferences ->
            when (resourceType) {
                ResourceType.BATTERY -> {
                    preferences[Keys.BatteryStanding] = normalized.standingPercent
                    preferences[Keys.BatteryWalking] = normalized.walkingPercent
                    preferences[Keys.BatteryRunning] = normalized.runningPercent
                }
                ResourceType.NETWORK -> {
                    preferences[Keys.NetworkStanding] = normalized.standingPercent
                    preferences[Keys.NetworkWalking] = normalized.walkingPercent
                    preferences[Keys.NetworkRunning] = normalized.runningPercent
                }
            }
        }
    }

    private fun toSettings(preferences: Preferences): AppSettings {
        val selectedResource = preferences[Keys.SelectedResource]
            ?.let { runCatching { ResourceType.valueOf(it) }.getOrNull() }
            ?: ResourceType.BATTERY
        val characterImageShape = preferences[Keys.CharacterImageShape]
            ?.let { runCatching { CharacterImageShape.valueOf(it) }.getOrNull() }
            ?: CharacterImageShape.ORIGINAL

        return AppSettings(
            selectedResourceType = selectedResource,
            showResourceBar = preferences[Keys.ShowResourceBar] ?: true,
            showSpeechBubble = preferences[Keys.ShowSpeechBubble] ?: false,
            showImageBorder = preferences[Keys.ShowImageBorder] ?: false,
            useLightText = preferences[Keys.UseLightText] ?: false,
            characterId = preferences[Keys.CharacterId] ?: "blob",
            characterImageShape = characterImageShape,
            animationSpeedMultiplier = preferences[Keys.AnimationSpeed]?.coerceIn(
                AppSettings.MIN_ANIMATION_SPEED,
                AppSettings.MAX_ANIMATION_SPEED
            ) ?: AppSettings.DEFAULT_ANIMATION_SPEED,
            characterScale = preferences[Keys.CharacterScale]?.coerceIn(
                AppSettings.MIN_CHARACTER_SCALE,
                AppSettings.MAX_CHARACTER_SCALE
            ) ?: AppSettings.DEFAULT_CHARACTER_SCALE,
            overlayX = preferences[Keys.OverlayX] ?: 32,
            overlayY = preferences[Keys.OverlayY] ?: 96,
            overlayWidth = (preferences[Keys.OverlayWidth] ?: AppSettings.DEFAULT_OVERLAY_WIDTH).coerceIn(
                AppSettings.MIN_OVERLAY_SIZE,
                AppSettings.MAX_OVERLAY_SIZE
            ),
            overlayHeight = (preferences[Keys.OverlayHeight] ?: AppSettings.DEFAULT_OVERLAY_HEIGHT).coerceIn(
                AppSettings.MIN_OVERLAY_SIZE,
                AppSettings.MAX_OVERLAY_SIZE
            ),
            batteryThresholds = ResourceThresholds(
                standingPercent = preferences[Keys.BatteryStanding] ?: 40,
                walkingPercent = preferences[Keys.BatteryWalking] ?: 60,
                runningPercent = preferences[Keys.BatteryRunning] ?: 80
            ).normalize(ResourceType.BATTERY.thresholdMax()),
            networkThresholds = ResourceThresholds(
                standingPercent = preferences[Keys.NetworkStanding] ?: ResourceThresholds.NetworkDefault.standingPercent,
                walkingPercent = preferences[Keys.NetworkWalking] ?: ResourceThresholds.NetworkDefault.walkingPercent,
                runningPercent = preferences[Keys.NetworkRunning] ?: ResourceThresholds.NetworkDefault.runningPercent
            ).normalize(ResourceType.NETWORK.thresholdMax())
        )
    }

    private object Keys {
        val SelectedResource = stringPreferencesKey("selected_resource")
        val ShowResourceBar = booleanPreferencesKey("show_resource_bar")
        val ShowSpeechBubble = booleanPreferencesKey("show_speech_bubble")
        val ShowImageBorder = booleanPreferencesKey("show_image_border")
        val UseLightText = booleanPreferencesKey("use_light_text")
        val CharacterId = stringPreferencesKey("character_id")
        val CharacterImageShape = stringPreferencesKey("character_image_shape")
        val AnimationSpeed = floatPreferencesKey("animation_speed")
        val CharacterScale = floatPreferencesKey("character_scale")
        val OverlayX = intPreferencesKey("overlay_x")
        val OverlayY = intPreferencesKey("overlay_y")
        val OverlayWidth = intPreferencesKey("overlay_width")
        val OverlayHeight = intPreferencesKey("overlay_height")
        val BatteryStanding = intPreferencesKey("battery_standing")
        val BatteryWalking = intPreferencesKey("battery_walking")
        val BatteryRunning = intPreferencesKey("battery_running")
        val NetworkStanding = intPreferencesKey("network_standing")
        val NetworkWalking = intPreferencesKey("network_walking")
        val NetworkRunning = intPreferencesKey("network_running")
        val OnboardingSeen = booleanPreferencesKey("onboarding_seen")
    }
}
