package com.voranixextremo.plugin.worldevents;

import org.bukkit.Material;

/** Un ítem extra que puede soltar un mob durante la Luna Roja: "chance" de 0.0 a 1.0. */
public record BonusDrop(Material material, double chance, int min, int max) {
}
