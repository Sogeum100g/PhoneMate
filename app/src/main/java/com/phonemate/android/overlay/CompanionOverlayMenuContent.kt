package com.phonemate.android.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonemate.android.R
import com.phonemate.android.ThresholdSlider
import com.phonemate.android.domain.AppSettings
import com.phonemate.android.domain.CharacterDefinition
import com.phonemate.android.domain.CharacterImageShape
import com.phonemate.android.domain.ResourceThresholds
import com.phonemate.android.domain.ResourceType
import com.phonemate.android.domain.label
import com.phonemate.android.domain.readingUnit
import com.phonemate.android.domain.thresholdMax
import java.util.Locale

@Composable
fun CompanionOverlayMenuContent(
    settings: AppSettings,
    characters: List<CharacterDefinition>,
    onVisibilityChanged: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onResourceSelected: (ResourceType) -> Unit,
    onThresholdsChanged: (ResourceType, ResourceThresholds) -> Unit,
    onCharacterSelected: (String) -> Unit,
    onImageShapeSelected: (CharacterImageShape) -> Unit,
    onAnimationSpeedSelected: (Float) -> Unit,
    onCharacterScaleSelected: (Float) -> Unit,
    onResetSize: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resourceType = settings.selectedResourceType
    val thresholds = settings.thresholdsFor(resourceType)
    val maxValue = resourceType.thresholdMax()

    Surface(
        modifier = modifier
            .width(304.dp)
            .heightIn(max = 560.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToggleMenuRow(
                    label = stringResource(R.string.menu_always_on_top),
                    checked = true,
                    enabled = false,
                    onCheckedChange = {}
                )
                ToggleMenuRow(
                    label = stringResource(R.string.menu_show_resource_bar),
                    checked = settings.showResourceBar,
                    onCheckedChange = {
                        onVisibilityChanged(it, settings.showSpeechBubble, settings.showImageBorder, settings.useLightText)
                    }
                )
                ToggleMenuRow(
                    label = stringResource(R.string.menu_show_speech_bubble),
                    checked = settings.showSpeechBubble,
                    onCheckedChange = {
                        onVisibilityChanged(settings.showResourceBar, it, settings.showImageBorder, settings.useLightText)
                    }
                )
                ToggleMenuRow(
                    label = stringResource(R.string.menu_show_image_border),
                    checked = settings.showImageBorder,
                    onCheckedChange = {
                        onVisibilityChanged(settings.showResourceBar, settings.showSpeechBubble, it, settings.useLightText)
                    }
                )
                ToggleMenuRow(
                    label = stringResource(R.string.label_light_text),
                    checked = settings.useLightText,
                    onCheckedChange = {
                        onVisibilityChanged(settings.showResourceBar, settings.showSpeechBubble, settings.showImageBorder, it)
                    }
                )

                MenuDivider()
                SectionTitle(stringResource(R.string.label_resource_section))
                ResourceType.entries.forEach { resourceType ->
                    RadioMenuRow(
                        label = resourceType.label(),
                        selected = settings.selectedResourceType == resourceType,
                        onClick = { onResourceSelected(resourceType) }
                    )
                }

                MenuDivider()
                SectionTitle(stringResource(R.string.menu_state_thresholds_section))
                Text(
                    text = settings.selectedResourceType.label(),
                    style = MaterialTheme.typography.bodySmall
                )
                ThresholdSlider(
                    label = stringResource(R.string.label_standing_threshold_name),
                    value = thresholds.standingPercent,
                    range = 1f..(maxValue - 2).toFloat(),
                    unit = resourceType.readingUnit(),
                    onValueChange = {
                        onThresholdsChanged(
                            resourceType,
                            thresholds.copy(standingPercent = it).normalize(maxValue)
                        )
                    }
                )
                ThresholdSlider(
                    label = stringResource(R.string.label_walking_threshold_name),
                    value = thresholds.walkingPercent,
                    range = 2f..(maxValue - 1).toFloat(),
                    unit = resourceType.readingUnit(),
                    onValueChange = {
                        onThresholdsChanged(
                            resourceType,
                            thresholds.copy(walkingPercent = it).normalize(maxValue)
                        )
                    }
                )
                ThresholdSlider(
                    label = stringResource(R.string.label_running_threshold_name),
                    value = thresholds.runningPercent,
                    range = 3f..maxValue.toFloat(),
                    unit = resourceType.readingUnit(),
                    onValueChange = {
                        onThresholdsChanged(
                            resourceType,
                            thresholds.copy(runningPercent = it).normalize(maxValue)
                        )
                    }
                )

                MenuDivider()
                SectionTitle(stringResource(R.string.label_character_section))
                characters.forEach { character ->
                    RadioMenuRow(
                        label = character.displayName,
                        selected = settings.characterId == character.id,
                        onClick = { onCharacterSelected(character.id) }
                    )
                }

                MenuDivider()
                SectionTitle(stringResource(R.string.menu_image_shape_section))
                CharacterImageShape.entries.forEach { imageShape ->
                    RadioMenuRow(
                        label = when (imageShape) {
                            CharacterImageShape.ORIGINAL -> stringResource(R.string.image_shape_original)
                            CharacterImageShape.ROUND -> stringResource(R.string.image_shape_round)
                        },
                        selected = settings.characterImageShape == imageShape,
                        onClick = { onImageShapeSelected(imageShape) }
                    )
                }

                MenuDivider()
                SectionTitle(stringResource(R.string.menu_animation_speed_section))
                Text(
                    text = "${settings.animationSpeedMultiplier.formatScale()}x",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = settings.animationSpeedMultiplier,
                    onValueChange = onAnimationSpeedSelected,
                    valueRange = AppSettings.MIN_ANIMATION_SPEED..AppSettings.MAX_ANIMATION_SPEED
                )

                MenuDivider()
                SectionTitle(stringResource(R.string.menu_character_scale_section))
                Text(
                    text = "${settings.normalizedCharacterScale().formatScale()}x",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = settings.normalizedCharacterScale(),
                    onValueChange = onCharacterScaleSelected,
                    valueRange = AppSettings.MIN_CHARACTER_SCALE..AppSettings.MAX_CHARACTER_SCALE
                )

                MenuDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onResetSize
                    ) {
                        Text(stringResource(R.string.menu_reset_size))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAppSettings
                    ) {
                        Text(stringResource(R.string.action_settings))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleMenuRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = if (enabled) onCheckedChange else null
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RadioMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}

private fun Float.formatScale(): String {
    return String.format(Locale.US, "%.2f", this)
        .trimEnd('0')
        .trimEnd('.')
}
