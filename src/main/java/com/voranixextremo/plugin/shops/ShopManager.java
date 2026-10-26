package com.voranixextremo.plugin.shops;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class ShopManager {

    private final VoranixExtremoPlugin plugin;
    private final Map<String, ShopType> shopTypes = new LinkedHashMap<>();

    public ShopManager(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Vuelve a leer las tiendas de shops en config.yml (los chefs que ya están aparecidos mantienen sus precios). */
    public void reload() {
        shopTypes.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shops");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection shopSection = section.getConfigurationSection(id);
            if (shopSection == null) {
                continue;
            }
            try {
                shopTypes.put(id.toLowerCase(), ShopType.fromConfig(id, shopSection));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Tienda '" + id + "' mal configurada: " + ex.getMessage());
            }
        }
    }

    public Map<String, ShopType> getShopTypes() {
        return Collections.unmodifiableMap(shopTypes);
    }

    public ShopType getShopType(String id) {
        return id == null ? null : shopTypes.get(id.toLowerCase());
    }
}
