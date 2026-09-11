package com.phonemate.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.phonemate.android.R
import com.phonemate.android.data.CharacterRepository
import com.phonemate.android.data.SettingsRepository
import com.phonemate.android.domain.AppSettings
import com.phonemate.android.domain.CharacterDefinition
import com.phonemate.android.domain.CharacterImageSource
import com.phonemate.android.domain.CharacterImageShape
import com.phonemate.android.domain.CharacterState
import com.phonemate.android.domain.ResourceThresholds
import com.phonemate.android.domain.ResourceType
import com.phonemate.android.domain.label
import com.phonemate.android.domain.loadCharacterDrawable
import com.phonemate.android.domain.readingUnit
import com.phonemate.android.domain.thresholdMax
import com.phonemate.android.service.FloatingCompanionService
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import pl.droidsonroids.gif.GifImageView

class MainActivity : ComponentActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var characterRepository: CharacterRepository

    private var overlayPermissionGranted by mutableStateOf(false)
    private var overlayRunning by mutableStateOf(false)
    private var characters by mutableStateOf<List<CharacterDefinition>>(emptyList())
    private var customName by mutableStateOf("")
    private var sleepingUri by mutableStateOf<Uri?>(null)
    private var standingUri by mutableStateOf<Uri?>(null)
    private var walkingUri by mutableStateOf<Uri?>(null)
    private var runningUri by mutableStateOf<Uri?>(null)
    private var clearedImageStates by mutableStateOf<Set<CharacterState>>(emptySet())
    private var editingCharacterId by mutableStateOf<String?>(null)
    private var statusMessage by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val sleepingPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                sleepingUri = result.data?.data
                if (sleepingUri != null) clearedImageStates -= CharacterState.LYING
            }
        }

    private val standingPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                standingUri = result.data?.data
                if (standingUri != null) clearedImageStates -= CharacterState.STANDING
            }
        }

    private val walkingPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                walkingUri = result.data?.data
                if (walkingUri != null) clearedImageStates -= CharacterState.WALKING
            }
        }

    private val runningPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                runningUri = result.data?.data
                if (runningUri != null) clearedImageStates -= CharacterState.RUNNING
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(this)
        characterRepository = CharacterRepository(this)
        reloadLocalState()

        setContent {
            MaterialTheme {
                val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
                val onboardingSeen by settingsRepository.onboardingSeen.collectAsState(initial = true)
                val coroutineScope = rememberCoroutineScope()

                PhoneMateScreen(
                    settings = settings,
                    overlayPermissionGranted = overlayPermissionGranted,
                    overlayRunning = overlayRunning,
                    showOnboarding = !onboardingSeen,
                    characters = characters,
                    customName = customName,
                    sleepingUri = sleepingUri,
                    standingUri = standingUri,
                    walkingUri = walkingUri,
                    runningUri = runningUri,
                    clearedImageStates = clearedImageStates,
                    editingCharacterId = editingCharacterId,
                    statusMessage = statusMessage,
                    onOpenOverlaySettings = ::openOverlaySettings,
                    onStartOverlay = ::startOverlay,
                    onStopOverlay = ::stopOverlay,
                    onDismissOnboarding = {
                        coroutineScope.launch {
                            settingsRepository.markOnboardingSeen()
                        }
                    },
                    onResourceSelected = { resourceType ->
                        coroutineScope.launch {
                            settingsRepository.updateSelectedResource(resourceType)
                        }
                    },
                    onVisibilityChanged = { showResourceBar, showSpeechBubble, showImageBorder, useLightText ->
                        coroutineScope.launch {
                            settingsRepository.updateVisibility(
                                showResourceBar = showResourceBar,
                                showSpeechBubble = showSpeechBubble,
                                showImageBorder = showImageBorder,
                                useLightText = useLightText
                            )
                        }
                    },
                    onImageShapeSelected = { imageShape ->
                        coroutineScope.launch {
                            settingsRepository.updateCharacterImageShape(imageShape)
                        }
                    },
                    onSpeedChanged = { speed ->
                        coroutineScope.launch {
                            settingsRepository.updateAnimationSpeed(speed)
                        }
                    },
                    onCharacterScaleChanged = { scale ->
                        coroutineScope.launch {
                            settingsRepository.updateCharacterScale(scale)
                        }
                    },
                    onThresholdsChanged = { resourceType, thresholds ->
                        coroutineScope.launch {
                            settingsRepository.updateThresholds(resourceType, thresholds)
                        }
                    },
                    onCharacterSelected = { characterId ->
                        coroutineScope.launch {
                            selectCharacter(characterId)
                        }
                    },
                    onCustomNameChanged = { customName = it },
                    onPickSleeping = { launchImagePicker(sleepingPicker) },
                    onPickStanding = { launchImagePicker(standingPicker) },
                    onPickWalking = { launchImagePicker(walkingPicker) },
                    onPickRunning = { launchImagePicker(runningPicker) },
                    onClearImage = ::clearImage,
                    onStartRegisterCustom = ::startRegisteringCharacter,
                    onBeginEditCustom = { characterId ->
                        coroutineScope.launch {
                            beginEditingCharacter(characterId)
                        }
                    },
                    onRegisterCustom = {
                        coroutineScope.launch {
                            saveCustomCharacter()
                        }
                    },
                    onDeleteCustomCharacters = { characterIds ->
                        coroutineScope.launch {
                            deleteCustomCharacters(characterIds, currentCharacterId = settings.characterId)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reloadLocalState()
    }

    private fun reloadLocalState() {
        overlayPermissionGranted = Settings.canDrawOverlays(this)
        overlayRunning = FloatingCompanionService.isRunning
        characters = characterRepository.loadCharacters()
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun createImagePickerIntent(): Intent {
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(pickIntent, getString(R.string.main_choose_image_intent_title)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun launchImagePicker(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(createImagePickerIntent())
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val intent = FloatingCompanionService.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        overlayRunning = true
        statusMessage = getString(R.string.main_status_overlay_started)
    }

    private fun stopOverlay() {
        startService(FloatingCompanionService.stopIntent(this))
        overlayRunning = false
        statusMessage = getString(R.string.main_status_overlay_stopped)
    }

    private suspend fun saveCustomCharacter() {
        val sleeping = sleepingUri
        val standing = standingUri
        val walking = walkingUri
        val running = runningUri
        val editingId = editingCharacterId
        val updateExisting = editingId != null
        val selectedCharacter = characters.firstOrNull { it.id == editingId }

        if (updateExisting && selectedCharacter?.isCustom != true) {
            statusMessage = getString(R.string.main_status_select_custom_to_update)
            return
        }

        if (customName.isBlank()) {
            statusMessage = getString(R.string.main_status_enter_character_name)
            return
        }

        val existingSleepingImage = selectedCharacter?.sleepingImage
            ?.takeUnless { CharacterState.LYING in clearedImageStates }
        if (sleeping == null && existingSleepingImage == null) {
            statusMessage = getString(R.string.main_status_choose_minimum_state_image)
            return
        }

        val result = runCatching {
            if (updateExisting) {
                characterRepository.updateCustomCharacter(
                    id = requireNotNull(editingId),
                    displayName = customName,
                    sleepingUri = sleeping,
                    standingUri = standing,
                    walkingUri = walking,
                    runningUri = running,
                    clearedStates = clearedImageStates
                )
            } else {
                characterRepository.registerCustomCharacter(
                    displayName = customName,
                    sleepingUri = sleeping,
                    standingUri = standing,
                    walkingUri = walking,
                    runningUri = running
                )
            }
        }

        result.onSuccess { character ->
            characters = characterRepository.loadCharacters()
            settingsRepository.updateCharacter(character.id)
            editingCharacterId = character.id
            customName = character.displayName
            sleepingUri = null
            standingUri = null
            walkingUri = null
            runningUri = null
            clearedImageStates = emptySet()
            statusMessage = if (updateExisting) {
                getString(R.string.main_status_character_updated)
            } else {
                getString(R.string.main_status_character_registered)
            }
        }.onFailure { error ->
            statusMessage = error.message ?: getString(R.string.main_status_character_operation_failed)
        }
    }

    private suspend fun deleteCustomCharacters(characterIds: Set<String>, currentCharacterId: String) {
        if (characterIds.isEmpty()) {
            statusMessage = getString(R.string.main_status_select_custom_to_delete)
            return
        }

        val deletedCount = characterIds.count { characterRepository.deleteCustomCharacter(it) }
        if (deletedCount > 0) {
            val deletedCurrentCharacter = currentCharacterId in characterIds
            val deletedEditingCharacter = editingCharacterId?.let { it in characterIds } == true
            characters = characterRepository.loadCharacters()
            if (deletedCurrentCharacter) {
                settingsRepository.updateCharacter("blob")
            }
            if (deletedEditingCharacter) {
                editingCharacterId = null
                customName = ""
                sleepingUri = null
                standingUri = null
                walkingUri = null
                runningUri = null
                clearedImageStates = emptySet()
            }
            statusMessage = getString(R.string.main_status_character_deleted)
        } else {
            statusMessage = getString(R.string.main_status_select_custom_to_delete)
        }
    }

    private fun startRegisteringCharacter() {
        editingCharacterId = null
        customName = ""
        sleepingUri = null
        standingUri = null
        walkingUri = null
        runningUri = null
        clearedImageStates = emptySet()
        statusMessage = getString(R.string.main_status_register_custom_character)
    }

    private suspend fun beginEditingCharacter(characterId: String) {
        val selectedCharacter = characters.firstOrNull {
            it.id == characterId && it.isCustom
        }
        if (selectedCharacter == null) {
            statusMessage = getString(R.string.main_status_select_custom_to_update)
            return
        }

        editingCharacterId = selectedCharacter.id
        customName = selectedCharacter.displayName
        sleepingUri = null
        standingUri = null
        walkingUri = null
        runningUri = null
        clearedImageStates = emptySet()
        settingsRepository.updateCharacter(selectedCharacter.id)
        statusMessage = getString(R.string.main_editing_character, selectedCharacter.displayName)
    }

    private suspend fun selectCharacter(characterId: String) {
        settingsRepository.updateCharacter(characterId)
        if (characters.none { it.id == editingCharacterId }) {
            editingCharacterId = null
            customName = ""
            sleepingUri = null
            standingUri = null
            walkingUri = null
            runningUri = null
            clearedImageStates = emptySet()
        }
    }

    private fun clearImage(state: CharacterState) {
        when (state) {
            CharacterState.LYING -> sleepingUri = null
            CharacterState.STANDING -> standingUri = null
            CharacterState.WALKING -> walkingUri = null
            CharacterState.RUNNING -> runningUri = null
        }
        clearedImageStates += state
    }

}

@Composable
private fun PhoneMateScreen(
    settings: AppSettings,
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    showOnboarding: Boolean,
    characters: List<CharacterDefinition>,
    customName: String,
    sleepingUri: Uri?,
    standingUri: Uri?,
    walkingUri: Uri?,
    runningUri: Uri?,
    clearedImageStates: Set<CharacterState>,
    editingCharacterId: String?,
    statusMessage: String?,
    onOpenOverlaySettings: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onDismissOnboarding: () -> Unit,
    onResourceSelected: (ResourceType) -> Unit,
    onVisibilityChanged: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onImageShapeSelected: (CharacterImageShape) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onCharacterScaleChanged: (Float) -> Unit,
    onThresholdsChanged: (ResourceType, ResourceThresholds) -> Unit,
    onCharacterSelected: (String) -> Unit,
    onCustomNameChanged: (String) -> Unit,
    onPickSleeping: () -> Unit,
    onPickStanding: () -> Unit,
    onPickWalking: () -> Unit,
    onPickRunning: () -> Unit,
    onClearImage: (CharacterState) -> Unit,
    onStartRegisterCustom: () -> Unit,
    onBeginEditCustom: (String) -> Unit,
    onRegisterCustom: () -> Unit,
    onDeleteCustomCharacters: (Set<String>) -> Unit
) {
    val selectedCharacter = characters.firstOrNull { it.id == settings.characterId }
        ?: CharacterRepository.BuiltInBlob
    val editingCharacter = characters.firstOrNull {
        it.id == editingCharacterId && it.isCustom
    }
    var showHelpDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = { showHelpDialog = true }) {
                Text(stringResource(R.string.action_help))
            }
        }

        PermissionSection(
            overlayPermissionGranted = overlayPermissionGranted,
            overlayRunning = overlayRunning,
            statusMessage = statusMessage,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onStartOverlay = onStartOverlay,
            onStopOverlay = onStopOverlay
        )

        HorizontalDivider()

        ResourceSection(
            settings = settings,
            onResourceSelected = onResourceSelected,
            onVisibilityChanged = onVisibilityChanged,
            onSpeedChanged = onSpeedChanged,
            onThresholdsChanged = onThresholdsChanged
        )

        HorizontalDivider()

        CharacterSection(
            characters = characters,
            selectedCharacter = selectedCharacter,
            imageShape = settings.characterImageShape,
            characterScale = settings.normalizedCharacterScale(),
            customName = customName,
            sleepingUri = sleepingUri,
            standingUri = standingUri,
            walkingUri = walkingUri,
            runningUri = runningUri,
            clearedImageStates = clearedImageStates,
            editingCharacter = editingCharacter,
            statusMessage = statusMessage,
            onCharacterSelected = onCharacterSelected,
            onImageShapeSelected = onImageShapeSelected,
            onCharacterScaleChanged = onCharacterScaleChanged,
            onCustomNameChanged = onCustomNameChanged,
            onPickSleeping = onPickSleeping,
            onPickStanding = onPickStanding,
            onPickWalking = onPickWalking,
            onPickRunning = onPickRunning,
            onClearImage = onClearImage,
            onStartRegisterCustom = onStartRegisterCustom,
            onBeginEditCustom = onBeginEditCustom,
            onRegisterCustom = onRegisterCustom,
            onDeleteCustomCharacters = onDeleteCustomCharacters
        )

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }

    if (showOnboarding || showHelpDialog) {
        OnboardingDialog(
            onDismiss = {
                if (showOnboarding) {
                    onDismissOnboarding()
                }
                showHelpDialog = false
            }
        )
    }
}

@Composable
private fun OnboardingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_onboarding_title)) },
        text = {
            Text(text = stringResource(R.string.main_onboarding_body))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.main_onboarding_confirm))
            }
        }
    )
}

@Composable
private fun PermissionSection(
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    statusMessage: String?,
    onOpenOverlaySettings: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.main_permission_section_title))
            Text(
                text = if (overlayPermissionGranted) {
                    stringResource(R.string.main_permission_allowed)
                } else {
                    stringResource(R.string.main_permission_required)
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenOverlaySettings) {
                Text(stringResource(R.string.action_permission))
            }
            if (overlayRunning) {
                Button(onClick = onStartOverlay, enabled = overlayPermissionGranted) {
                    Text(stringResource(R.string.action_start))
                }
            } else {
                OutlinedButton(onClick = onStartOverlay, enabled = overlayPermissionGranted) {
                    Text(stringResource(R.string.action_start))
                }
            }
            if (overlayRunning) {
                OutlinedButton(onClick = onStopOverlay) {
                    Text(stringResource(R.string.action_stop))
                }
            } else {
                Button(onClick = onStopOverlay) {
                    Text(stringResource(R.string.action_stop))
                }
            }
        }

        statusMessage?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ResourceSection(
    settings: AppSettings,
    onResourceSelected: (ResourceType) -> Unit,
    onVisibilityChanged: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onThresholdsChanged: (ResourceType, ResourceThresholds) -> Unit
) {
    val resourceType = settings.selectedResourceType
    val thresholds = settings.thresholdsFor(resourceType)
    val maxValue = resourceType.thresholdMax()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.label_resource_section), style = MaterialTheme.typography.titleMedium)
        ResourceSelector(
            selected = resourceType,
            onSelected = onResourceSelected
        )

        ToggleRow(
            label = stringResource(R.string.main_toggle_resource_bar),
            checked = settings.showResourceBar,
            onCheckedChange = { onVisibilityChanged(it, settings.showSpeechBubble, settings.showImageBorder, settings.useLightText) }
        )
        ToggleRow(
            label = stringResource(R.string.main_toggle_speech_bubble),
            checked = settings.showSpeechBubble,
            onCheckedChange = { onVisibilityChanged(settings.showResourceBar, it, settings.showImageBorder, settings.useLightText) }
        )
        ToggleRow(
            label = stringResource(R.string.main_toggle_image_border),
            checked = settings.showImageBorder,
            onCheckedChange = { onVisibilityChanged(settings.showResourceBar, settings.showSpeechBubble, it, settings.useLightText) }
        )
        ToggleRow(
            label = stringResource(R.string.label_light_text),
            checked = settings.useLightText,
            onCheckedChange = { onVisibilityChanged(settings.showResourceBar, settings.showSpeechBubble, settings.showImageBorder, it) }
        )

        Text(text = stringResource(R.string.main_animation_speed, settings.animationSpeedMultiplier.formatOneDecimal()))
        Slider(
            value = settings.animationSpeedMultiplier,
            onValueChange = { onSpeedChanged(it) },
            valueRange = AppSettings.MIN_ANIMATION_SPEED..AppSettings.MAX_ANIMATION_SPEED
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
    }
}

@Composable
private fun ResourceSelector(
    selected: ResourceType,
    onSelected: (ResourceType) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResourceType.entries.forEach { resourceType ->
            if (resourceType == selected) {
                Button(onClick = { onSelected(resourceType) }) {
                    Text(resourceType.label())
                }
            } else {
                OutlinedButton(onClick = { onSelected(resourceType) }) {
                    Text(resourceType.label())
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CharacterSection(
    characters: List<CharacterDefinition>,
    selectedCharacter: CharacterDefinition,
    imageShape: CharacterImageShape,
    characterScale: Float,
    customName: String,
    sleepingUri: Uri?,
    standingUri: Uri?,
    walkingUri: Uri?,
    runningUri: Uri?,
    clearedImageStates: Set<CharacterState>,
    editingCharacter: CharacterDefinition?,
    statusMessage: String?,
    onCharacterSelected: (String) -> Unit,
    onImageShapeSelected: (CharacterImageShape) -> Unit,
    onCharacterScaleChanged: (Float) -> Unit,
    onCustomNameChanged: (String) -> Unit,
    onPickSleeping: () -> Unit,
    onPickStanding: () -> Unit,
    onPickWalking: () -> Unit,
    onPickRunning: () -> Unit,
    onClearImage: (CharacterState) -> Unit,
    onStartRegisterCustom: () -> Unit,
    onBeginEditCustom: (String) -> Unit,
    onRegisterCustom: () -> Unit,
    onDeleteCustomCharacters: (Set<String>) -> Unit
) {
    val customCharacters = characters.filter { it.isCustom }
    val editingCustomCharacter = editingCharacter != null
    var showEditDialog by remember { mutableStateOf(false) }
    var editDialogSelectedId by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteDialogSelectedIds by remember { mutableStateOf(emptySet<String>()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.label_character_section), style = MaterialTheme.typography.titleMedium)
        CharacterDropdown(
            characters = characters,
            selectedCharacter = selectedCharacter,
            onCharacterSelected = onCharacterSelected
        )

        Text(text = stringResource(R.string.label_image_shape_section))
        ImageShapeSelector(
            selected = imageShape,
            onSelected = onImageShapeSelected
        )

        Text(text = stringResource(R.string.main_character_scale, characterScale.formatScale()))
        Slider(
            value = characterScale,
            onValueChange = { onCharacterScaleChanged(it) },
            valueRange = AppSettings.MIN_CHARACTER_SCALE..AppSettings.MAX_CHARACTER_SCALE
        )

        Text(
            text = if (editingCustomCharacter) {
                stringResource(R.string.main_editing_character, editingCharacter.displayName)
            } else {
                stringResource(R.string.main_new_custom_character)
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onStartRegisterCustom
            ) {
                Text(stringResource(R.string.main_new_custom))
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    editDialogSelectedId = editingCharacter?.id ?: customCharacters.firstOrNull()?.id
                    showEditDialog = true
                },
                enabled = customCharacters.isNotEmpty()
            ) {
                Text(stringResource(R.string.main_edit_custom))
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                deleteDialogSelectedIds = emptySet()
                showDeleteDialog = true
            },
            enabled = customCharacters.isNotEmpty()
        ) {
            Text(stringResource(R.string.main_delete_custom))
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = customName,
            onValueChange = onCustomNameChanged,
            singleLine = true,
            label = { Text(stringResource(R.string.main_custom_name_label)) }
        )

        ImagePickerRow(
            label = stringResource(R.string.label_sleeping),
            uri = sleepingUri,
            existingImage = editingCharacter?.sleepingImage.takeUnless { CharacterState.LYING in clearedImageStates },
            onPick = onPickSleeping,
            onClear = { onClearImage(CharacterState.LYING) }
        )
        ImagePickerRow(
            label = stringResource(R.string.label_standing),
            uri = standingUri,
            existingImage = editingCharacter?.standingImage.takeUnless { CharacterState.STANDING in clearedImageStates },
            onPick = onPickStanding,
            onClear = { onClearImage(CharacterState.STANDING) }
        )
        ImagePickerRow(
            label = stringResource(R.string.label_walking),
            uri = walkingUri,
            existingImage = editingCharacter?.walkingImage.takeUnless { CharacterState.WALKING in clearedImageStates },
            onPick = onPickWalking,
            onClear = { onClearImage(CharacterState.WALKING) }
        )
        ImagePickerRow(
            label = stringResource(R.string.label_running),
            uri = runningUri,
            existingImage = editingCharacter?.runningImage.takeUnless { CharacterState.RUNNING in clearedImageStates },
            onPick = onPickRunning,
            onClear = { onClearImage(CharacterState.RUNNING) }
        )

        val hasMinimumStateImage = sleepingUri != null ||
            editingCharacter?.sleepingImage?.takeUnless {
                CharacterState.LYING in clearedImageStates
            } != null
        if (!hasMinimumStateImage) {
            Text(
                text = stringResource(R.string.main_minimum_state_image_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRegisterCustom,
            enabled = customName.isNotBlank() && hasMinimumStateImage
        ) {
            Text(
                if (editingCustomCharacter) {
                    stringResource(R.string.main_save_changes)
                } else {
                    stringResource(R.string.main_save_new_character)
                }
            )
        }

        statusMessage?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showEditDialog) {
        EditCharacterDialog(
            customCharacters = customCharacters,
            selectedCharacterId = editDialogSelectedId,
            onCharacterSelected = { editDialogSelectedId = it },
            onEdit = { characterId ->
                showEditDialog = false
                onBeginEditCustom(characterId)
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showDeleteDialog) {
        DeleteCharactersDialog(
            customCharacters = customCharacters,
            selectedCharacterIds = deleteDialogSelectedIds,
            onToggleCharacter = { characterId ->
                deleteDialogSelectedIds = if (characterId in deleteDialogSelectedIds) {
                    deleteDialogSelectedIds - characterId
                } else {
                    deleteDialogSelectedIds + characterId
                }
            },
            onDelete = {
                showDeleteDialog = false
                onDeleteCustomCharacters(deleteDialogSelectedIds)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun EditCharacterDialog(
    customCharacters: List<CharacterDefinition>,
    selectedCharacterId: String?,
    onCharacterSelected: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_edit_character_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                customCharacters.forEach { character ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCharacterSelected(character.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedCharacterId == character.id,
                            onClick = { onCharacterSelected(character.id) }
                        )
                        Text(text = character.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedCharacterId != null,
                onClick = { selectedCharacterId?.let(onEdit) }
            ) {
                Text(stringResource(R.string.action_edit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DeleteCharactersDialog(
    customCharacters: List<CharacterDefinition>,
    selectedCharacterIds: Set<String>,
    onToggleCharacter: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_delete_characters_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                customCharacters.forEach { character ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCharacter(character.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = character.id in selectedCharacterIds,
                            onCheckedChange = { onToggleCharacter(character.id) }
                        )
                        Text(text = character.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedCharacterIds.isNotEmpty(),
                onClick = onDelete
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ImageShapeSelector(
    selected: CharacterImageShape,
    onSelected: (CharacterImageShape) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CharacterImageShape.entries.forEach { imageShape ->
            val label = when (imageShape) {
                CharacterImageShape.ORIGINAL -> stringResource(R.string.image_shape_original)
                CharacterImageShape.ROUND -> stringResource(R.string.image_shape_round)
            }
            if (imageShape == selected) {
                Button(onClick = { onSelected(imageShape) }) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = { onSelected(imageShape) }) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun CharacterDropdown(
    characters: List<CharacterDefinition>,
    selectedCharacter: CharacterDefinition,
    onCharacterSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedCharacter.displayName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            characters.forEach { character ->
                DropdownMenuItem(
                    text = { Text(character.displayName) },
                    onClick = {
                        expanded = false
                        onCharacterSelected(character.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ImagePickerRow(
    label: String,
    uri: Uri?,
    existingImage: CharacterImageSource?,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    val existingFileName = existingImage?.displayFileName()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ImagePreviewThumbnail(uri = uri, existingImage = existingImage)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label)
            Text(
                text = uri?.lastPathSegment
                    ?: existingFileName
                    ?: stringResource(R.string.main_no_file),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (uri != null || existingImage != null) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.action_clear))
                }
            }
            OutlinedButton(onClick = onPick) {
                Text(
                    if (uri == null && existingFileName == null) {
                        stringResource(R.string.action_choose)
                    } else {
                        stringResource(R.string.action_replace)
                    }
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewThumbnail(
    uri: Uri?,
    existingImage: CharacterImageSource?
) {
    val context = LocalContext.current
    val previewKey = uri?.toString() ?: existingImage?.key

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (previewKey != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    GifImageView(viewContext).apply {
                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { view ->
                    if (view.tag != previewKey) {
                        view.setImageDrawable(
                            if (uri != null) {
                                loadCharacterDrawable(context, uri)
                            } else {
                                existingImage?.let { loadCharacterDrawable(context, it) }
                            }
                        )
                        view.tag = previewKey
                    }
                }
            )
        }
    }
}

private fun CharacterImageSource.displayFileName(): String {
    val path = when (this) {
        is CharacterImageSource.Asset -> this.path
        is CharacterImageSource.FilePath -> this.originalFileName ?: this.path
    }
    return path.replace('\\', '/').substringAfterLast('/')
}

private fun Float.formatOneDecimal(): String {
    return "%1.1f".format(this)
}

private fun Float.formatScale(): String {
    return "%1.2f".format(this)
        .trimEnd('0')
        .trimEnd('.')
}
