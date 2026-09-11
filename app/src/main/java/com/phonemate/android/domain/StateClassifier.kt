package com.phonemate.android.domain

class StateClassifier {
    fun classify(
        resourceType: ResourceType,
        usagePercent: Int,
        settings: AppSettings
    ): CharacterState {
        return classify(usagePercent, settings.thresholdsFor(resourceType), resourceType.thresholdMax())
    }

    fun classify(
        usagePercent: Int,
        thresholds: ResourceThresholds = ResourceThresholds.Default,
        maxValue: Int = 100
    ): CharacterState {
        val normalized = thresholds.normalize(maxValue)
        val usage = usagePercent.coerceIn(0, maxValue)
        return when {
            usage < normalized.standingPercent -> CharacterState.LYING
            usage < normalized.walkingPercent -> CharacterState.STANDING
            usage < normalized.runningPercent -> CharacterState.WALKING
            else -> CharacterState.RUNNING
        }
    }
}

