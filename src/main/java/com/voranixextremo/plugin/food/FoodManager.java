package com.voranixextremo.plugin.food;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Carga las comidas (food.items) y bebidas (food.drinks) de config.yml, y crea/reconoce
 * sus ItemStack (igual que CoinItemFactory: una etiqueta persistente identifica el ítem,
 * no su nombre/lore).
 */
public class FoodManager {

    private final VoranixExtremoPlugin plugin;
    private final NamespacedKey foodKey;
    private final NamespacedKey drinkKey;
    private final Map<String, FoodType> foodTypes = new LinkedHashMap<>();
    private final Map<String, DrinkType> drinkTypes = new LinkedHashMap<>();

    public FoodManager(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        this.foodKey = new NamespacedKey(plugin, "food_id");
        this.drinkKey = new NamespacedKey(plugin, "drink_id");
        loadFoodTypes();
        loadDrinkTypes();
    }

    private void loadFoodTypes() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("food.items");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(id);
            if (itemSection == null) {
                continue;
            }
            try {
                foodTypes.put(id.toLowerCase(), FoodType.fromConfig(id, itemSection));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Comida '" + id + "' mal configurada: " + ex.getMessage());
            }
        }
    }

    private void loadDrinkTypes() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("food.drinks");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(id);
            if (itemSection == null) {
                continue;
            }
            try {
                drinkTypes.put(id.toLowerCase(), DrinkType.fromConfig(id, itemSection));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Bebida '" + id + "' mal configurada: " + ex.getMessage());
            }
        }
    }

    public Map<String, FoodType> getFoodTypes() {
        return Collections.unmodifiableMap(foodTypes);
    }

    public Map<String, DrinkType> getDrinkTypes() {
        return Collections.unmodifiableMap(drinkTypes);
    }

    public FoodType getFoodType(String id) {
        return id == null ? null : foodTypes.get(id.toLowerCase());
    }

    public DrinkType getDrinkType(String id) {
        return id == null ? null : drinkTypes.get(id.toLowerCase());
    }

    public ItemStack createFoodItem(FoodType type) {
        ItemStack item = new ItemStack(type.getBaseMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));
            if (type.getCustomModelData() != null) {
                meta.setCustomModelData(type.getCustomModelData());
            }
            meta.getPersistentDataContainer().set(foodKey, PersistentDataType.STRING, type.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createDrinkItem(DrinkType type) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));
            if (type.getCustomModelData() != null) {
                meta.setCustomModelData(type.getCustomModelData());
            }
            meta.setBasePotionData(new PotionData(PotionType.WATER));
            meta.setColor(type.getColor());
            meta.clearCustomEffects();
            for (ConfiguredEffect effect : type.getEffects()) {
                meta.addCustomEffect(effect.toPotionEffect(), true);
            }
            meta.getPersistentDataContainer().set(drinkKey, PersistentDataType.STRING, type.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    public String getFoodId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(foodKey, PersistentDataType.STRING);
    }

    public String getDrinkId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(drinkKey, PersistentDataType.STRING);
    }
}
