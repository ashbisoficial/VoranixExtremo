package com.voranixextremo.plugin.food;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Un efecto de poción configurado en config.yml (tipo + duración en ticks + amplificador),
 * tal como los definen las comidas/bebidas en economy.coins y food.*.
 */
public record ConfiguredEffect(PotionEffectType type, int durationTicks, int amplifier) {

    public PotionEffect toPotionEffect() {
        return new PotionEffect(type, durationTicks, amplifier, false, true, true);
    }

    public static List<ConfiguredEffect> listFromConfig(List<Map<?, ?>> rawEffects) {
        List<ConfiguredEffect> effects = new ArrayList<>();
        if (rawEffects == null) {
            return effects;
        }
        for (Map<?, ?> raw : rawEffects) {
            Object typeName = raw.get("type");
            if (typeName == null) {
                continue;
            }
            PotionEffectType type = PotionEffectType.getByName(String.valueOf(typeName).toUpperCase());
            if (type == null) {
                continue;
            }
            int duration = raw.get("duration") instanceof Number n ? n.intValue() : 0;
            int amplifier = raw.get("amplifier") instanceof Number n ? n.intValue() : 0;
            effects.add(new ConfiguredEffect(type, duration, amplifier));
        }
        return effects;
    }
}
