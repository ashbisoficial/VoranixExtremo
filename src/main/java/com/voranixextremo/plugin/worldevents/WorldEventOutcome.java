package com.voranixextremo.plugin.worldevents;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Un evento mundial configurado en config.yml -> world-events.outcomes.<id>. Qué campos
 * importan depende de "type" (ver WorldEventType).
 */
public class WorldEventOutcome {

    private final String id;
    private final WorldEventType type;
    private final String displayName;
    private final int weight;
    private final int durationSeconds;
    private final double mobHealthMultiplier;
    private final double mobDamageMultiplier;
    private final List<BonusDrop> bonusDrops;
    private final String command;

    private WorldEventOutcome(String id, WorldEventType type, String displayName, int weight, int durationSeconds,
                               double mobHealthMultiplier, double mobDamageMultiplier, List<BonusDrop> bonusDrops, String command) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.weight = weight;
        this.durationSeconds = durationSeconds;
        this.mobHealthMultiplier = mobHealthMultiplier;
        this.mobDamageMultiplier = mobDamageMultiplier;
        this.bonusDrops = bonusDrops;
        this.command = command;
    }

    public static WorldEventOutcome fromConfig(String id, ConfigurationSection section) {
        String typeName = section.getString("type");
        if (typeName == null) {
            throw new IllegalArgumentException("falta 'type'");
        }
        WorldEventType type;
        try {
            type = WorldEventType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("type inválido: " + typeName);
        }

        String displayName = section.getString("display-name", id);
        int weight = Math.max(1, section.getInt("weight", 10));
        int durationSeconds = Math.max(0, section.getInt("duration-seconds", 300));
        double healthMultiplier = section.getDouble("mob-health-multiplier", 1.0);
        double damageMultiplier = section.getDouble("mob-damage-multiplier", 1.0);
        String command = section.getString("command");

        List<BonusDrop> bonusDrops = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("bonus-drops")) {
            Object materialName = raw.get("material");
            if (materialName == null) {
                continue;
            }
            Material material = Material.matchMaterial(String.valueOf(materialName).toUpperCase());
            if (material == null) {
                continue;
            }
            double chance = raw.get("chance") instanceof Number n ? n.doubleValue() : 0.0;
            int min = raw.get("min") instanceof Number n ? n.intValue() : 1;
            int max = raw.get("max") instanceof Number n ? n.intValue() : min;
            bonusDrops.add(new BonusDrop(material, chance, min, Math.max(min, max)));
        }

        return new WorldEventOutcome(id.toLowerCase(), type, displayName, weight, durationSeconds,
                healthMultiplier, damageMultiplier, bonusDrops, command);
    }

    public String getId() {
        return id;
    }

    public WorldEventType getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public double getMobHealthMultiplier() {
        return mobHealthMultiplier;
    }

    public double getMobDamageMultiplier() {
        return mobDamageMultiplier;
    }

    public List<BonusDrop> getBonusDrops() {
        return bonusDrops;
    }

    public String getCommand() {
        return command;
    }
}
