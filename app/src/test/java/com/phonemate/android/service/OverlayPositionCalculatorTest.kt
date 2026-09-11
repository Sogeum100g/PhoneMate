package com.phonemate.android.service

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionCalculatorTest {
    @Test
    fun `bottom right stays bottom right from portrait to landscape and back`() {
        val portraitWidth = 1080
        val portraitHeight = 2200
        val landscapeWidth = 2400
        val landscapeHeight = 960
        val overlayWidth = 200
        val overlayHeight = 300
        val portraitBottomRight = (portraitWidth - overlayWidth) to (portraitHeight - overlayHeight)

        val landscapePosition = mapOverlayPositionByRatio(
            x = portraitBottomRight.first,
            y = portraitBottomRight.second,
            overlayWidth = overlayWidth,
            overlayHeight = overlayHeight,
            oldScreenWidth = portraitWidth,
            oldAvailableHeight = portraitHeight,
            newScreenWidth = landscapeWidth,
            newAvailableHeight = landscapeHeight
        )

        assertEquals(
            (landscapeWidth - overlayWidth) to (landscapeHeight - overlayHeight),
            landscapePosition
        )

        val restoredPortraitPosition = mapOverlayPositionByRatio(
            x = landscapePosition.first,
            y = landscapePosition.second,
            overlayWidth = overlayWidth,
            overlayHeight = overlayHeight,
            oldScreenWidth = landscapeWidth,
            oldAvailableHeight = landscapeHeight,
            newScreenWidth = portraitWidth,
            newAvailableHeight = portraitHeight
        )

        assertEquals(portraitBottomRight, restoredPortraitPosition)
    }

    @Test
    fun `relative position is preserved between orientations`() {
        val mappedPosition = mapOverlayPositionByRatio(
            x = 220,
            y = 950,
            overlayWidth = 200,
            overlayHeight = 300,
            oldScreenWidth = 1080,
            oldAvailableHeight = 2200,
            newScreenWidth = 2400,
            newAvailableHeight = 960
        )

        assertEquals(550 to 330, mappedPosition)
    }

    @Test
    fun `out of bounds old position is clamped before ratio mapping`() {
        val mappedPosition = mapOverlayPositionByRatio(
            x = 5000,
            y = -100,
            overlayWidth = 200,
            overlayHeight = 300,
            oldScreenWidth = 1080,
            oldAvailableHeight = 2200,
            newScreenWidth = 2400,
            newAvailableHeight = 960
        )

        assertEquals(2200 to 0, mappedPosition)
    }
}
