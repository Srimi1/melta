package com.melta.app.battery

import com.melta.app.battery.overlay.EffectPicker
import com.melta.app.core.prefs.ChargeEffect
import org.junit.Assert.assertEquals
import org.junit.Test

class EffectPickerTest {

    @Test
    fun autoMatchesType() {
        assertEquals(ChargeEffect.BOLTS, EffectPicker.resolve(ChargeEffect.AUTO, listOf("electric")))
        assertEquals(ChargeEffect.FLAMES, EffectPicker.resolve(ChargeEffect.AUTO, listOf("fire", "flying")))
        assertEquals(ChargeEffect.BUBBLES, EffectPicker.resolve(ChargeEffect.AUTO, listOf("water")))
        assertEquals(ChargeEffect.LEAVES, EffectPicker.resolve(ChargeEffect.AUTO, listOf("grass", "poison")))
        assertEquals(ChargeEffect.SPARKS, EffectPicker.resolve(ChargeEffect.AUTO, listOf("normal")))
        assertEquals(ChargeEffect.SPARKS, EffectPicker.resolve(ChargeEffect.AUTO, emptyList()))
    }

    @Test
    fun explicitChoiceWins() {
        assertEquals(ChargeEffect.HEARTS, EffectPicker.resolve(ChargeEffect.HEARTS, listOf("electric")))
        assertEquals(ChargeEffect.NONE, EffectPicker.resolve(ChargeEffect.NONE, listOf("fire")))
    }
}
