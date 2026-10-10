package com.melta.app.battery.overlay

import com.melta.app.core.prefs.ChargeEffect

/** Picks a charging effect that matches the companion's type when the user leaves it on Auto. */
object EffectPicker {
    fun resolve(effect: ChargeEffect, types: List<String>): ChargeEffect {
        if (effect != ChargeEffect.AUTO) return effect
        for (type in types) {
            when (type) {
                "electric" -> return ChargeEffect.BOLTS
                "fire", "dragon" -> return ChargeEffect.FLAMES
                "water" -> return ChargeEffect.BUBBLES
                "grass", "bug" -> return ChargeEffect.LEAVES
                "fairy", "psychic" -> return ChargeEffect.HEARTS
                "ice" -> return ChargeEffect.SNOW
            }
        }
        return ChargeEffect.SPARKS
    }
}
