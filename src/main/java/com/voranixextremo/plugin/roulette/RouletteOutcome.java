package com.voranixextremo.plugin.roulette;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

/**
 * Un suceso configurado en config.yml -> roulette.outcomes.<id>. Qué campos importan
 * depende de "type" (ver OutcomeType); los que no aplican quedan en su valor por defecto.
 */
public class RouletteOutcome {

    private final String id;
    private final OutcomeCategory category;
    private final OutcomeType type;
    private final String displayName;
    private final int weight;

    private final PotionEffectType effectType;
    private final int durationTicks;
    private final int amplifier;
    private final double amount;
    private final String command;
    private final String taskDescription;
    private final int taskDurationSeconds;
    private final double taskRewardCoins;
    private final double taskPenaltyCoins;

    private RouletteOutcome(String id, OutcomeCategory category, OutcomeType type, String displayName, int weight,
                             PotionEffectType effectType, int durationTicks, int amplifier, double amount, String command,
                             String taskDescription, int taskDurationSeconds, double taskRewardCoins, double taskPenaltyCoins) {
        this.id = id;
        this.category = category;
        this.type = type;
        this.displayName = displayName;
        this.weight = weight;
        this.effectType = effectType;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.amount = amount;
        this.command = command;
        this.taskDescription = taskDescription;
        this.taskDurationSeconds = taskDurationSeconds;
        this.taskRewardCoins = taskRewardCoins;
        this.taskPenaltyCoins = taskPenaltyCoins;
    }

    public static RouletteOutcome fromConfig(String id, ConfigurationSection section) {
        OutcomeCategory category;
        try {
            category = OutcomeCategory.valueOf(section.getString("category", "BUENO").toUpperCase());
        } catch (IllegalArgumentException ex) {
            category = OutcomeCategory.BUENO;
        }

        String typeName = section.getString("type");
        if (typeName == null) {
            throw new IllegalArgumentException("falta 'type'");
        }
        OutcomeType type;
        try {
            type = OutcomeType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("type inválido: " + typeName);
        }

        String displayName = section.getString("display-name", id);
        int weight = Math.max(1, section.getInt("weight", 10));

        PotionEffectType effectType = section.contains("effect")
                ? PotionEffectType.getByName(section.getString("effect", "").toUpperCase())
                : null;
        int duration = section.getInt("duration", 0);
        int amplifier = section.getInt("amplifier", 0);
        double amount = section.getDouble("amount", 0);
        String command = section.getString("command");
        String taskDescription = section.getString("description", "");
        int taskDuration = section.getInt("duration-seconds", 300);
        double rewardCoins = section.getDouble("reward-coins", 0);
        double penaltyCoins = section.getDouble("penalty-coins", 0);

        return new RouletteOutcome(id.toLowerCase(), category, type, displayName, weight,
                effectType, duration, amplifier, amount, command,
                taskDescription, taskDuration, rewardCoins, penaltyCoins);
    }

    public String getId() {
        return id;
    }

    public OutcomeCategory getCategory() {
        return category;
    }

    public OutcomeType getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }

    public PotionEffectType getEffectType() {
        return effectType;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public int getAmplifier() {
        return amplifier;
    }

    public double getAmount() {
        return amount;
    }

    public String getCommand() {
        return command;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public int getTaskDurationSeconds() {
        return taskDurationSeconds;
    }

    public double getTaskRewardCoins() {
        return taskRewardCoins;
    }

    public double getTaskPenaltyCoins() {
        return taskPenaltyCoins;
    }
}
