package com.phonemate.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StateClassifierTest {
    private val classifier = StateClassifier()

    @Test
    fun classifiesDefaultBoundaries() {
        assertEquals(CharacterState.LYING, classifier.classify(0))
        assertEquals(CharacterState.LYING, classifier.classify(39))
        assertEquals(CharacterState.STANDING, classifier.classify(40))
        assertEquals(CharacterState.STANDING, classifier.classify(59))
        assertEquals(CharacterState.WALKING, classifier.classify(60))
        assertEquals(CharacterState.WALKING, classifier.classify(79))
        assertEquals(CharacterState.RUNNING, classifier.classify(80))
        assertEquals(CharacterState.RUNNING, classifier.classify(100))
    }

    @Test
    fun normalizesInvalidThresholds() {
        val thresholds = ResourceThresholds(
            standingPercent = -10,
            walkingPercent = 1,
            runningPercent = 1
        ).normalize()

        assertEquals(ResourceThresholds(1, 2, 3), thresholds)
    }

    @Test
    fun normalizesNetworkThresholdsUpToTheNetworkMax() {
        val thresholds = ResourceThresholds(
            standingPercent = 600,
            walkingPercent = 700,
            runningPercent = 800
        ).normalize(ResourceType.NETWORK.thresholdMax())

        assertEquals(ResourceThresholds(498, 499, 500), thresholds)
    }

    @Test
    fun clampsUsagePercentBeforeClassification() {
        assertEquals(CharacterState.LYING, classifier.classify(-50))
        assertEquals(CharacterState.RUNNING, classifier.classify(140))
    }

    @Test
    fun classifiesBatteryLevelDirectlySoAFullerChargeIsMoreActive() {
        val settings = AppSettings()

        assertEquals(
            CharacterState.RUNNING,
            classifier.classify(resourceType = ResourceType.BATTERY, usagePercent = 90, settings = settings)
        )
        assertEquals(
            CharacterState.LYING,
            classifier.classify(resourceType = ResourceType.BATTERY, usagePercent = 10, settings = settings)
        )
    }

    @Test
    fun classifiesNetworkSpeedDirectlySoAFasterConnectionIsMoreActive() {
        val settings = AppSettings()

        // Defaults are anchored to YouTube's recommended sustained speeds (720p/1080p/1440p).
        assertEquals(
            CharacterState.LYING,
            classifier.classify(resourceType = ResourceType.NETWORK, usagePercent = 2, settings = settings)
        )
        assertEquals(
            CharacterState.STANDING,
            classifier.classify(resourceType = ResourceType.NETWORK, usagePercent = 4, settings = settings)
        )
        assertEquals(
            CharacterState.WALKING,
            classifier.classify(resourceType = ResourceType.NETWORK, usagePercent = 8, settings = settings)
        )
        assertEquals(
            CharacterState.RUNNING,
            classifier.classify(resourceType = ResourceType.NETWORK, usagePercent = 20, settings = settings)
        )
    }

    @Test
    fun networkThresholdsSupportValuesAboveTheOldHundredCap() {
        val settings = AppSettings(
            networkThresholds = ResourceThresholds(standingPercent = 50, walkingPercent = 150, runningPercent = 300)
        )

        // A 222Mbps reading used to be clamped to 100 before comparison, making thresholds above
        // 100 unreachable. It should now compare directly against the (also uncapped) thresholds.
        assertEquals(
            CharacterState.WALKING,
            classifier.classify(resourceType = ResourceType.NETWORK, usagePercent = 222, settings = settings)
        )
        assertEquals(
            CharacterState.RUNNING,
            classifier.classify(resourceType = ResourceType.NETWORK, usagePercent = 350, settings = settings)
        )
    }
}

