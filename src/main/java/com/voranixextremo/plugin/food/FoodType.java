package com.voranixextremo.plugin.food;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Una comida vendible (ej. una Banderilla), definida en config.yml -> food.items.<id>.
 * El material base tiene que ser un alimento vanilla real (para que el clic derecho la
 * "coma"); el hambre/saturación/efectos reales los corrige FoodConsumeListener.
 */
public class FoodType {

    private final String id;
    private final Material baseMaterial;
    private final Integer customModelData;
    private final String displayName;
    private final int hunger;
    private final float saturationModifier;
    private final double price;
    private final int tradeMaxUses;
    private final int tradeXp;
    private final List<ConfiguredEffect> effects;

    private FoodType(String id, Material baseMaterial, Integer customModelData, String displayName,
                      int hunger, float saturationModifier, double price, int tradeMaxUses, int tradeXp,
                      List<ConfiguredEffect> effects) {
        this.id = id;
        this.baseMaterial = baseMaterial;
        this.customModelData = customModelData;
        this.displayName = displayName;
        this.hunger = hunger;
        this.saturationModifier = saturationModifier;
        this.price = price;
        this.tradeMaxUses = tradeMaxUses;
        this.tradeXp = tradeXp;
        this.effects = effects;
    }

    public static FoodType fromConfig(String id, ConfigurationSection section) {
        String materialName = section.getString("base-material");
        if (materialName == null) {
            throw new IllegalArgumentException("falta 'base-material'");
        }
        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null) {
            throw new IllegalArgumentException("material inválido: " + materialName);
        }

        String displayName = section.getString("display-name", id);
        Integer customModelData = section.contains("custom-model-data") ? section.getInt("custom-model-data") : null;
        int hunger = section.getInt("hunger", 0);
        float saturation = (float) section.getDouble("saturation", 0.0);
        double price = section.getDouble("price", -1);
        int tradeMaxUses = section.getInt("trade-max-uses", 16);
        int tradeXp = section.getInt("trade-xp", 5);
        List<ConfiguredEffect> effects = ConfiguredEffect.listFromConfig(section.getMapList("effects"));

        return new FoodType(id.toLowerCase(), material, customModelData, displayName, hunger, saturation,
                price, tradeMaxUses, tradeXp, effects);
    }

    public String getId() {
        return id;
    }

    public Material getBaseMaterial() {
        return baseMaterial;
    }

    public Integer getCustomModelData() {
        return customModelData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getHunger() {
        return hunger;
    }

    public float getSaturationModifier() {
        return saturationModifier;
    }

    /** Precio en unidades base (valor de la moneda Estaño). Negativo = no se vende. */
    public double getPrice() {
        return price;
    }

    public int getTradeMaxUses() {
        return tradeMaxUses;
    }

    public int getTradeXp() {
        return tradeXp;
    }

    public List<ConfiguredEffect> getEffects() {
        return effects;
    }
}
