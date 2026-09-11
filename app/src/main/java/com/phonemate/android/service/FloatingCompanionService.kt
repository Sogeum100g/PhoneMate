package com.phonemate.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.phonemate.android.MainActivity
import com.phonemate.android.R
import com.phonemate.android.data.CharacterRepository
import com.phonemate.android.data.SettingsRepository
import com.phonemate.android.domain.AppSettings
import com.phonemate.android.domain.CharacterDefinition
import com.phonemate.android.domain.CharacterState
import com.phonemate.android.domain.StateClassifier
import com.phonemate.android.monitor.AndroidResourceMonitor
import com.phonemate.android.monitor.BatteryMonitor
import com.phonemate.android.monitor.NetworkMonitor
import com.phonemate.android.overlay.CompanionOverlayContent
import com.phonemate.android.overlay.CompanionOverlayMenuContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatingCompanionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settingsState = mutableStateOf(AppSettings())
    private val usagePercentState = mutableIntStateOf(0)
    private val characterState = mutableStateOf(CharacterState.LYING)
    private val charactersState = mutableStateOf<List<CharacterDefinition>>(emptyList())
    private val overlayViewTreeOwner = OverlayViewTreeOwner()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var characterRepository: CharacterRepository
    private lateinit var resourceMonitor: AndroidResourceMonitor
    private val classifier = StateClassifier()

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var settingsJob: Job? = null
    private var refreshJob: Job? = null
    private var previousScreenWidthPx: Int? = null
    private var previousAvailableHeightPx: Int? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        characterRepository = CharacterRepository(this)
        resourceMonitor = AndroidResourceMonitor(
            batteryMonitor = BatteryMonitor(this),
            networkMonitor = NetworkMonitor()
        )
        overlayViewTreeOwner.attach()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            else -> startCompanion()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        removeOverlay()
        overlayViewTreeOwner.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlayView == null) {
            return
        }

        val oldScreenWidth = previousScreenWidthPx ?: resources.displayMetrics.widthPixels
        val oldAvailableHeight = previousAvailableHeightPx ?: availableOverlayHeightPx()
        val newScreenWidth = resources.displayMetrics.widthPixels
        val newAvailableHeight = availableOverlayHeightPx()
        previousScreenWidthPx = newScreenWidth
        previousAvailableHeightPx = newAvailableHeight

        // The menu position is derived from the overlay position, so it is no longer valid after
        // the overlay moves to the same relative position in the new orientation.
        removeSettingsMenu()
        repositionOverlayForScreenChange(
            oldScreenWidth = oldScreenWidth,
            oldAvailableHeight = oldAvailableHeight,
            newScreenWidth = newScreenWidth,
            newAvailableHeight = newAvailableHeight
        )
    }

    private fun repositionOverlayForScreenChange(
        oldScreenWidth: Int,
        oldAvailableHeight: Int,
        newScreenWidth: Int,
        newAvailableHeight: Int
    ) {
        val params = overlayParams ?: return
        val (newX, newY) = mapOverlayPositionByRatio(
            x = params.x,
            y = params.y,
            overlayWidth = params.width,
            overlayHeight = params.height,
            oldScreenWidth = oldScreenWidth,
            oldAvailableHeight = oldAvailableHeight,
            newScreenWidth = newScreenWidth,
            newAvailableHeight = newAvailableHeight
        )
        if (newX == params.x && newY == params.y) {
            return
        }

        params.x = newX
        params.y = newY
        overlayView?.let { view ->
            runCatching { windowManager?.updateViewLayout(view, params) }
        }
        serviceScope.launch {
            settingsRepository.updateOverlayPosition(newX, newY)
        }
    }

    /**
     * Clamps a stored overlay position against the screen's current bounds when the overlay is
     * first created. Live screen-size changes preserve the position ratio instead.
     */
    private fun clampPositionToScreen(x: Int, y: Int, width: Int, height: Int): Pair<Int, Int> {
        val maxX = (resources.displayMetrics.widthPixels - width).coerceAtLeast(0)
        val maxY = (availableOverlayHeightPx() - height).coerceAtLeast(0)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
    }

    private fun startCompanion() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        createNotificationChannel()
        val foregroundStarted = runCatching {
            startForegroundCompat(buildNotification())
        }.isSuccess

        if (!foregroundStarted) {
            stopSelf()
            return
        }

        serviceScope.launch {
            settingsState.value = settingsRepository.settings.first()
            charactersState.value = characterRepository.loadCharacters()
            ensureOverlay()
            startSettingsCollection()
            startRefreshLoop()
        }
    }

    private fun ensureOverlay() {
        if (overlayView != null) {
            return
        }

        val settings = settingsState.value
        val overlayWidth = settings.scaledOverlayWidth()
        val overlayHeight = settings.scaledOverlayHeight()
        val (initialX, initialY) = clampPositionToScreen(
            settings.overlayX,
            settings.overlayY,
            overlayWidth,
            overlayHeight
        )
        val params = WindowManager.LayoutParams(
            overlayWidth,
            overlayHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        val overlayComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(overlayViewTreeOwner)
            setViewTreeViewModelStoreOwner(overlayViewTreeOwner)
            setViewTreeSavedStateRegistryOwner(overlayViewTreeOwner)
            setContent {
                MaterialTheme {
                    val selectedCharacter = charactersState.value.firstOrNull {
                        it.id == settingsState.value.characterId
                    } ?: CharacterRepository.BuiltInBlob

                    CompanionOverlayContent(
                        settings = settingsState.value,
                        usagePercent = usagePercentState.intValue,
                        characterState = characterState.value,
                        character = selectedCharacter
                    )
                }
            }
        }

        val overlayContainer = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(overlayViewTreeOwner)
            setViewTreeViewModelStoreOwner(overlayViewTreeOwner)
            setViewTreeSavedStateRegistryOwner(overlayViewTreeOwner)
            addView(
                overlayComposeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                createOverlayTouchHandle(),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        val manager = windowManager ?: return
        val overlayAdded = runCatching {
            manager.addView(overlayContainer, params)
        }.isSuccess

        if (!overlayAdded) {
            stopSelf()
            return
        }

        overlayView = overlayContainer
        overlayParams = params
        previousScreenWidthPx = resources.displayMetrics.widthPixels
        previousAvailableHeightPx = availableOverlayHeightPx()
        isRunning = true

        if (initialX != settings.overlayX || initialY != settings.overlayY) {
            serviceScope.launch {
                settingsRepository.updateOverlayPosition(initialX, initialY)
            }
        }
    }

    private fun startSettingsCollection() {
        if (settingsJob != null) {
            return
        }

        settingsJob = serviceScope.launch {
            settingsRepository.settings.collect { settings ->
                settingsState.value = settings
                charactersState.value = characterRepository.loadCharacters()
                updateOverlayLayout(settings)
            }
        }
    }

    private fun startRefreshLoop() {
        if (refreshJob != null) {
            return
        }

        refreshJob = serviceScope.launch {
            while (isActive) {
                refreshResource()
                delay(RESOURCE_REFRESH_MILLIS)
            }
        }
    }

    private suspend fun refreshResource() {
        val settings = settingsState.value
        val usage = resourceMonitor.getUsagePercent(settings.selectedResourceType)
        usagePercentState.intValue = usage
        characterState.value = classifier.classify(
            resourceType = settings.selectedResourceType,
            usagePercent = usage,
            settings = settings
        )
    }

    private fun updateOverlayLayout(settings: AppSettings) {
        val params = overlayParams ?: return
        params.x = settings.overlayX
        params.y = settings.overlayY
        params.width = settings.scaledOverlayWidth()
        params.height = settings.scaledOverlayHeight()
        overlayView?.let { view ->
            runCatching { windowManager?.updateViewLayout(view, params) }
        }
    }

    private fun moveOverlayBy(dx: Int, dy: Int) {
        val params = overlayParams ?: return
        params.x += dx
        params.y += dy
        overlayView?.let { view ->
            runCatching { windowManager?.updateViewLayout(view, params) }
        }
        serviceScope.launch {
            settingsRepository.updateOverlayPosition(params.x, params.y)
        }
    }

    private fun createOverlayTouchHandle(): View {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var dragArmed = false
        var dragging = false
        var longPressTriggered = false
        var downRawX = 0f
        var downRawY = 0f
        var lastRawX = 0f
        var lastRawY = 0f
        var longPressRunnable: Runnable? = null

        return View(this).apply {
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragArmed = true
                        dragging = false
                        longPressTriggered = false
                        downRawX = event.rawX
                        downRawY = event.rawY
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        longPressRunnable = Runnable {
                            if (dragArmed && !dragging) {
                                longPressTriggered = true
                                showSettingsMenu()
                            }
                        }.also { runnable ->
                            view.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragArmed || longPressTriggered) {
                            return@setOnTouchListener true
                        }

                        val totalDx = event.rawX - downRawX
                        val totalDy = event.rawY - downRawY
                        if (!dragging && totalDx * totalDx + totalDy * totalDy < touchSlop * touchSlop) {
                            return@setOnTouchListener true
                        }

                        if (!dragging) {
                            longPressRunnable?.let(view::removeCallbacks)
                            longPressRunnable = null
                            dragging = true
                        }

                        moveOverlayBy(
                            dx = (event.rawX - lastRawX).roundToInt(),
                            dy = (event.rawY - lastRawY).roundToInt()
                        )
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let(view::removeCallbacks)
                        longPressRunnable = null
                        dragArmed = false
                        dragging = false
                        longPressTriggered = false
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun removeOverlay() {
        settingsJob?.cancel()
        refreshJob?.cancel()
        settingsJob = null
        refreshJob = null

        removeSettingsMenu()
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        overlayParams = null
    }

    private fun showSettingsMenu() {
        if (menuView != null) {
            removeSettingsMenu()
            return
        }

        val manager = windowManager ?: return
        val settings = settingsState.value
        val currentOverlayParams = overlayParams
        val menuWidth = dpToPx(MENU_WIDTH_DP)
        val gap = dpToPx(MENU_GAP_DP)
        val screenWidth = resources.displayMetrics.widthPixels
        val overlayX = currentOverlayParams?.x ?: settings.overlayX
        val overlayY = currentOverlayParams?.y ?: settings.overlayY
        val overlayWidth = currentOverlayParams?.width ?: settings.scaledOverlayWidth()
        val rightX = overlayX + overlayWidth + gap
        val leftX = overlayX - menuWidth - gap
        val maxX = (screenWidth - menuWidth).coerceAtLeast(0)
        val x = if (rightX <= maxX) {
            rightX
        } else {
            leftX.coerceIn(0, maxX)
        }
        val maxY = (availableOverlayHeightPx() - dpToPx(MENU_MAX_HEIGHT_DP)).coerceAtLeast(0)
        val y = overlayY.coerceIn(0, maxY)

        val params = WindowManager.LayoutParams(
            menuWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        val menuComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(overlayViewTreeOwner)
            setViewTreeViewModelStoreOwner(overlayViewTreeOwner)
            setViewTreeSavedStateRegistryOwner(overlayViewTreeOwner)
            setContent {
                MaterialTheme {
                    CompanionOverlayMenuContent(
                        settings = settingsState.value,
                        characters = charactersState.value,
                        onVisibilityChanged = { showResourceBar, showSpeechBubble, showImageBorder, useLightText ->
                            serviceScope.launch {
                                settingsRepository.updateVisibility(
                                    showResourceBar = showResourceBar,
                                    showSpeechBubble = showSpeechBubble,
                                    showImageBorder = showImageBorder,
                                    useLightText = useLightText
                                )
                            }
                        },
                        onResourceSelected = { resourceType ->
                            serviceScope.launch {
                                settingsRepository.updateSelectedResource(resourceType)
                            }
                        },
                        onThresholdsChanged = { resourceType, thresholds ->
                            serviceScope.launch {
                                settingsRepository.updateThresholds(resourceType, thresholds)
                            }
                        },
                        onCharacterSelected = { characterId ->
                            serviceScope.launch {
                                settingsRepository.updateCharacter(characterId)
                            }
                        },
                        onImageShapeSelected = { imageShape ->
                            serviceScope.launch {
                                settingsRepository.updateCharacterImageShape(imageShape)
                            }
                        },
                        onAnimationSpeedSelected = { speed ->
                            serviceScope.launch {
                                settingsRepository.updateAnimationSpeed(speed)
                            }
                        },
                        onCharacterScaleSelected = { scale ->
                            serviceScope.launch {
                                settingsRepository.updateCharacterScale(scale)
                            }
                        },
                        onResetSize = ::resetOverlaySize,
                        onOpenAppSettings = {
                            removeSettingsMenu()
                            openAppSettings()
                        },
                        onDismiss = ::removeSettingsMenu
                    )
                }
            }
        }

        val menuContainer = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(overlayViewTreeOwner)
            setViewTreeViewModelStoreOwner(overlayViewTreeOwner)
            setViewTreeSavedStateRegistryOwner(overlayViewTreeOwner)
            addView(
                menuComposeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                createMenuDragHandle(),
                FrameLayout.LayoutParams(
                    (menuWidth - dpToPx(MENU_CLOSE_TOUCH_WIDTH_DP)).coerceAtLeast(0),
                    dpToPx(MENU_HEADER_DRAG_HEIGHT_DP),
                    Gravity.TOP or Gravity.START
                )
            )
        }

        runCatching {
            manager.addView(menuContainer, params)
        }.onSuccess {
            menuView = menuContainer
            menuParams = params
        }
    }

    private fun createMenuDragHandle(): View {
        val touchSlop = ViewConfiguration.get(this@FloatingCompanionService).scaledTouchSlop
        var dragArmed = false
        var dragging = false
        var downRawX = 0f
        var downRawY = 0f
        var lastRawX = 0f
        var lastRawY = 0f

        return View(this).apply {
            setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragArmed = true
                    dragging = false
                    downRawX = event.rawX
                    downRawY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragArmed) {
                        return@setOnTouchListener false
                    }

                    val totalDx = event.rawX - downRawX
                    val totalDy = event.rawY - downRawY
                    if (!dragging && totalDx * totalDx + totalDy * totalDy < touchSlop * touchSlop) {
                        return@setOnTouchListener true
                    }

                    dragging = true
                    moveSettingsMenuBy(
                        dx = (event.rawX - lastRawX).roundToInt(),
                        dy = (event.rawY - lastRawY).roundToInt()
                    )
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (!dragArmed) {
                        return@setOnTouchListener false
                    }

                    dragArmed = false
                    dragging = false
                    true
                }
                else -> false
            }
            }
        }
    }

    private fun moveSettingsMenuBy(dx: Int, dy: Int) {
        val view = menuView ?: return
        val params = menuParams ?: return
        val maxX = (resources.displayMetrics.widthPixels - dpToPx(MENU_WIDTH_DP)).coerceAtLeast(0)
        val maxY = (availableOverlayHeightPx() - dpToPx(MENU_MAX_HEIGHT_DP)).coerceAtLeast(0)

        params.x = (params.x + dx).coerceIn(0, maxX)
        params.y = (params.y + dy).coerceIn(0, maxY)
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun resetOverlaySize() {
        val settings = settingsState.value
        val params = overlayParams
        val x = params?.x ?: settings.overlayX
        val y = params?.y ?: settings.overlayY

        serviceScope.launch {
            settingsRepository.updateCharacterScale(AppSettings.DEFAULT_CHARACTER_SCALE)
            settingsRepository.updateOverlayPlacement(
                x = x,
                y = y,
                width = AppSettings.DEFAULT_OVERLAY_WIDTH,
                height = AppSettings.DEFAULT_OVERLAY_HEIGHT
            )
        }
    }

    private fun removeSettingsMenu() {
        menuView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        menuView = null
        menuParams = null
    }

    private fun openAppSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        @Volatile
        var isRunning = false
            private set

        const val ACTION_START = "com.phonemate.android.action.START_OVERLAY"
        const val ACTION_STOP = "com.phonemate.android.action.STOP_OVERLAY"
        private const val NOTIFICATION_CHANNEL_ID = "phonemate_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val RESOURCE_REFRESH_MILLIS = 3_000L
        private const val MENU_WIDTH_DP = 304
        private const val MENU_GAP_DP = 8
        private const val MENU_MAX_HEIGHT_DP = 560
        private const val MENU_HEADER_DRAG_HEIGHT_DP = 56
        private const val MENU_CLOSE_TOUCH_WIDTH_DP = 96

        fun startIntent(context: Context): Intent {
            return Intent(context, FloatingCompanionService::class.java).setAction(ACTION_START)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, FloatingCompanionService::class.java).setAction(ACTION_STOP)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    private fun availableOverlayHeightPx(): Int {
        return (
            resources.displayMetrics.heightPixels -
                systemDimensionPx("status_bar_height") -
                systemDimensionPx("navigation_bar_height")
            ).coerceAtLeast(0)
    }

    private fun systemDimensionPx(name: String): Int {
        val resourceId = resources.getIdentifier(name, "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    private class OverlayViewTreeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle = lifecycleRegistry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry =
            savedStateRegistryController.savedStateRegistry
        private var attached = false
        private var destroyed = false

        fun attach() {
            if (attached) {
                return
            }

            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            attached = true
        }

        fun destroy() {
            if (!attached || destroyed) {
                return
            }

            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            viewModelStore.clear()
            destroyed = true
        }
    }
}
