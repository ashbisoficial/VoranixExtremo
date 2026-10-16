package com.voranixextremo.plugin.food;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Una bebida (ej. una gaseosa), definida en config.yml -> food.drinks.<id>.
 * Se construye siempre sobre un ítem de poción (Material.POTION): eso da gratis la
 * animación de "beber" y que al terminar devuelva un frasco de cristal.
 */
public class DrinkType {

    private final String id;
    private final Integer customModelData;
    private final String displayName;
    private final Color color;
    private final int hunger;
    private final float saturationModifier;
    private final List<ConfiguredEffect> effects;

    private DrinkType(String id, Integer customModelData, String displayName, Color color,
                       int hunger, float saturationModifier, List<ConfiguredEffect> effects) {
        this.id = id;
        this.customModelData = customModelData;
        this.displayName = displayName;
        this.color = color;
        this.hunger = hunger;
        this.saturationModifier = saturationModifier;
        this.effects = effects;
    }

    public static DrinkType fromConfig(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        Integer customModelData = section.contains("custom-model-data") ? section.getInt("custom-model-data") : null;
        Color color = parseColor(section.getString("color", "#FFFFFF"));
        int hunger = section.getInt("hunger", 0);
        float saturation = (float) section.getDouble("saturation", 0.0);
        List<ConfiguredEffect> effects = ConfiguredEffect.listFromConfig(section.getMapList("effects"));

        return new DrinkType(id.toLowerCase(), customModelData, displayName, color, hunger, saturation, effects);
    }

    private static Color parseColor(String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Color.fromRGB(Integer.parseInt(clean, 16));
        } catch (NumberFormatException ex) {
            return Color.WHITE;
        }
    }

    public String getId() {
        return id;
    }

    public Integer getCustomModelData() {
        return customModelData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getColor() {
        return color;
    }

    public int getHunger() {
        return hunger;
    }

    public float getSaturationModifier() {
        return saturationModifier;
    }

    public List<ConfiguredEffect> getEffects() {
        return effects;
    }
}
