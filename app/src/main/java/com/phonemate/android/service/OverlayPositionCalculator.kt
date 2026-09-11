package com.phonemate.android.service

import kotlin.math.roundToInt

/**
 * Preserves the overlay's relative position inside its movable area when the screen size changes.
 * Using the movable area (screen size minus overlay size) keeps all four edges anchored exactly.
 */
internal fun mapOverlayPositionByRatio(
    x: Int,
    y: Int,
    overlayWidth: Int,
    overlayHeight: Int,
    oldScreenWidth: Int,
    oldAvailableHeight: Int,
    newScreenWidth: Int,
    newAvailableHeight: Int
): Pair<Int, Int> {
    return mapAxisPosition(
        position = x,
        itemSize = overlayWidth,
        oldContainerSize = oldScreenWidth,
        newContainerSize = newScreenWidth
    ) to mapAxisPosition(
        position = y,
        itemSize = overlayHeight,
        oldContainerSize = oldAvailableHeight,
        newContainerSize = newAvailableHeight
    )
}

private fun mapAxisPosition(
    position: Int,
    itemSize: Int,
    oldContainerSize: Int,
    newContainerSize: Int
): Int {
    val oldMaxPosition = (oldContainerSize - itemSize).coerceAtLeast(0)
    val newMaxPosition = (newContainerSize - itemSize).coerceAtLeast(0)
    if (oldMaxPosition == 0 || newMaxPosition == 0) {
        return 0
    }

    val positionRatio = position.coerceIn(0, oldMaxPosition).toDouble() / oldMaxPosition
    return (positionRatio * newMaxPosition).roundToInt().coerceIn(0, newMaxPosition)
}
