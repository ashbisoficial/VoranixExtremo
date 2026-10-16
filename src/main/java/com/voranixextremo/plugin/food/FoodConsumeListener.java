package com.voranixextremo.plugin.food;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Corrige el hambre/saturación/efectos tras comer una comida o beber una bebida de
 * VoranixExtremo. Vanilla ya aplica su propia nutrición según el material base (y, para
 * las bebidas, los efectos de PotionMeta); esto se ejecuta un tick después para
 * sobrescribir el hambre/saturación con los valores exactos configurados — así el
 * material base elegido (por el ítem/animación) no importa para el resultado final.
 */
public class FoodConsumeListener implements Listener {

    private final VoranixExtremoPlugin plugin;
    private final FoodManager foodManager;

    public FoodConsumeListener(VoranixExtremoPlugin plugin, FoodManager foodManager) {
        this.plugin = plugin;
        this.foodManager = foodManager;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        String foodId = foodManager.getFoodId(item);
        if (foodId != null) {
            FoodType type = foodManager.getFoodType(foodId);
            if (type != null) {
                scheduleNutritionFix(player, type.getHunger(), type.getSaturationModifier(), type.getEffects());
            }
            return;
        }

        String drinkId = foodManager.getDrinkId(item);
        if (drinkId != null) {
            DrinkType type = foodManager.getDrinkType(drinkId);
            if (type != null) {
                // Los efectos de la bebida ya los aplica Minecraft solo (vienen en el PotionMeta);
                // acá solo hace falta corregir el hambre, que una poción no toca por sí misma.
                scheduleNutritionFix(player, type.getHunger(), type.getSaturationModifier(), List.of());
            }
        }
    }

    private void scheduleNutritionFix(Player player, int hunger, float saturationModifier, List<ConfiguredEffect> effects) {
        int preFoodLevel = player.getFoodLevel();
        float preSaturation = player.getSaturation();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int newFoodLevel = Math.min(20, preFoodLevel + hunger);
            float restoredSaturation = hunger * saturationModifier * 2.0F;
            float newSaturation = Math.min(newFoodLevel, preSaturation + restoredSaturation);

            player.setFoodLevel(newFoodLevel);
            player.setSaturation(newSaturation);

            for (ConfiguredEffect effect : effects) {
                player.addPotionEffect(effect.toPotionEffect());
            }
        });
    }
}
