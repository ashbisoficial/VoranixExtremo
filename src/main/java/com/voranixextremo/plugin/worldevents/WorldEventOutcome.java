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
    private final EventCategory category;
    private final String displayName;
    private final int weight;
    private final int durationSeconds;
    private final double mobHealthMultiplier;
    private final double mobDamageMultiplier;
    private final List<BonusDrop> bonusDrops;
    private final String command;
    private final String objectiveDescription;
    private final RevillObjectiveType objectiveType;
    private final double objectiveY;
    private final int meteorIntervalTicks;
    private final double meteorYield;
    private final boolean meteorBreaksBlocks;

    private WorldEventOutcome(String id, WorldEventType type, EventCategory category, String displayName, int weight,
                               int durationSeconds, double mobHealthMultiplier, double mobDamageMultiplier,
                               List<BonusDrop> bonusDrops, String command, String objectiveDescription,
                               RevillObjectiveType objectiveType, double objectiveY, int meteorIntervalTicks,
                               double meteorYield, boolean meteorBreaksBlocks) {
        this.id = id;
        this.type = type;
        this.category = category;
        this.displayName = displayName;
        this.weight = weight;
        this.durationSeconds = durationSeconds;
        this.mobHealthMultiplier = mobHealthMultiplier;
        this.mobDamageMultiplier = mobDamageMultiplier;
        this.bonusDrops = bonusDrops;
        this.command = command;
        this.objectiveDescription = objectiveDescription;
        this.objectiveType = objectiveType;
        this.objectiveY = objectiveY;
        this.meteorIntervalTicks = meteorIntervalTicks;
        this.meteorYield = meteorYield;
        this.meteorBreaksBlocks = meteorBreaksBlocks;
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

        EventCategory category;
        try {
            category = EventCategory.valueOf(section.getString("category", "NEUTRAL").toUpperCase());
        } catch (IllegalArgumentException ex) {
            category = EventCategory.NEUTRAL;
        }

        String displayName = section.getString("display-name", id);
        int weight = Math.max(1, section.getInt("weight", 10));
        int durationSeconds = Math.max(0, section.getInt("duration-seconds", 300));
        double healthMultiplier = section.getDouble("mob-health-multiplier", 1.0);
        double damageMultiplier = section.getDouble("mob-damage-multiplier", 1.0);
        String command = section.getString("command");

        String objectiveDescription = section.getString("objective-description", "");
        RevillObjectiveType objectiveType = null;
        if (section.contains("objective-type")) {
            try {
                objectiveType = RevillObjectiveType.valueOf(section.getString("objective-type", "").toUpperCase());
            } catch (IllegalArgumentException ex) {
                objectiveType = null;
            }
        }
        double objectiveY = section.getDouble("objective-y", 0);
        int meteorIntervalTicks = Math.max(20, section.getInt("meteor-interval-ticks", 60));
        double meteorYield = section.getDouble("meteor-yield", 1.5);
        boolean meteorBreaksBlocks = section.getBoolean("meteor-break-blocks", false);

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

        return new WorldEventOutcome(id.toLowerCase(), type, category, displayName, weight, durationSeconds,
                healthMultiplier, damageMultiplier, bonusDrops, command, objectiveDescription, objectiveType,
                objectiveY, meteorIntervalTicks, meteorYield, meteorBreaksBlocks);
    }

    public String getId() {
        return id;
    }

    public WorldEventType getType() {
        return type;
    }

    public EventCategory getCategory() {
        return category;
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

    public String getObjectiveDescription() {
        return objectiveDescription;
    }

    public RevillObjectiveType getObjectiveType() {
        return objectiveType;
    }

    public double getObjectiveY() {
        return objectiveY;
    }

    public int getMeteorIntervalTicks() {
        return meteorIntervalTicks;
    }

    public double getMeteorYield() {
        return meteorYield;
    }

    public boolean isMeteorBreaksBlocks() {
        return meteorBreaksBlocks;
    }
}
