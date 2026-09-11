package com.phonemate.android.domain

import org.junit.Assert.assertSame
import org.junit.Test

class CharacterDefinitionTest {
    @Test
    fun `missing states fall back to the only registered image`() {
        val image = CharacterImageSource.Asset("only.gif")
        val character = CharacterDefinition(
            id = "one-image",
            displayName = "One image",
            isCustom = true,
            sleepingImage = image,
            standingImage = null,
            walkingImage = null,
            runningImage = null
        )

        CharacterState.entries.forEach { state ->
            assertSame(image, character.imageFor(state))
        }
    }

    @Test
    fun `registered image remains active until a higher state supplies another image`() {
        val sleeping = CharacterImageSource.Asset("sleeping.gif")
        val walking = CharacterImageSource.Asset("walking.gif")
        val character = CharacterDefinition(
            id = "partial",
            displayName = "Partial",
            isCustom = true,
            sleepingImage = sleeping,
            standingImage = null,
            walkingImage = walking,
            runningImage = null
        )

        assertSame(sleeping, character.imageFor(CharacterState.LYING))
        assertSame(sleeping, character.imageFor(CharacterState.STANDING))
        assertSame(walking, character.imageFor(CharacterState.WALKING))
        assertSame(walking, character.imageFor(CharacterState.RUNNING))
    }
}
