package com.voranixextremo.plugin.economy;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Una denominación de moneda configurada en config.yml -> economy.coins.<id>.
 * El material base + custom-model-data determina qué textura usa el resource pack.
 */
public class CoinType {

    private final String id;
    private final Material material;
    private final Integer customModelData;
    private final String displayName;
    private final double value;

    private CoinType(String id, Material material, Integer customModelData, String displayName, double value) {
        this.id = id;
        this.material = material;
        this.customModelData = customModelData;
        this.displayName = displayName;
        this.value = value;
    }

    public static CoinType fromConfig(String id, ConfigurationSection section) {
        String materialName = section.getString("material");
        if (materialName == null) {
            throw new IllegalArgumentException("falta 'material'");
        }
        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null) {
            throw new IllegalArgumentException("material inválido: " + materialName);
        }
        if (!section.contains("value")) {
            throw new IllegalArgumentException("falta 'value'");
        }

        double value = section.getDouble("value");
        Integer customModelData = section.contains("custom-model-data") ? section.getInt("custom-model-data") : null;
        String displayName = section.getString("display-name", id);
        return new CoinType(id.toLowerCase(), material, customModelData, displayName, value);
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public Integer getCustomModelData() {
        return customModelData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getValue() {
        return value;
    }
}
