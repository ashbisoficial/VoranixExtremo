package com.voranixextremo.plugin.economy;

import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Crea los ItemStack de cada moneda y permite reconocerlos luego por una etiqueta
 * persistente (no por el nombre/lore, que son solo cosméticos y pueden cambiar).
 */
public class CoinItemFactory {

    private final NamespacedKey coinKey;

    public CoinItemFactory(VoranixExtremoPlugin plugin) {
        this.coinKey = new NamespacedKey(plugin, "coin_id");
    }

    public ItemStack create(CoinType type, int amount) {
        ItemStack item = new ItemStack(type.getMaterial(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.color(type.getDisplayName()));
            if (type.getCustomModelData() != null) {
                meta.setCustomModelData(type.getCustomModelData());
            }
            meta.setLore(List.of(ChatColor.GRAY + "Valor: " + ChatColor.GREEN + formatValue(type.getValue())));
            meta.getPersistentDataContainer().set(coinKey, PersistentDataType.STRING, type.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    public String getCoinId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(coinKey, PersistentDataType.STRING);
    }

    private String formatValue(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
