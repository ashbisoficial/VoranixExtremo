package com.voranixextremo.plugin.shops;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.economy.CoinEconomy;
import com.voranixextremo.plugin.economy.CoinItemFactory;
import com.voranixextremo.plugin.economy.CoinType;
import com.voranixextremo.plugin.economy.EconomyManager;
import com.voranixextremo.plugin.food.FoodManager;
import com.voranixextremo.plugin.food.FoodType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawnea los aldeanos "chef" y les arma el comercio en monedas a partir de lo que
 * vende cada ShopType. No hay un modelo/skin de chef de verdad (Bukkit no permite
 * reskinear la entidad); se usa un Villager vanilla con la profesión que más se parezca
 * (BUTCHER trae puesto un delantal) y un nombre visible.
 */
public class ChefVillagerManager {

    private final EconomyManager economyManager;
    private final CoinItemFactory coinItemFactory;
    private final FoodManager foodManager;
    private final NamespacedKey shopKey;

    public ChefVillagerManager(VoranixExtremoPlugin plugin, EconomyManager economyManager,
                                CoinItemFactory coinItemFactory, FoodManager foodManager) {
        this.economyManager = economyManager;
        this.coinItemFactory = coinItemFactory;
        this.foodManager = foodManager;
        this.shopKey = new NamespacedKey(plugin, "shop_id");
    }

    public Villager spawn(ShopType shop, Location location) {
        Villager villager = location.getWorld().spawn(location, Villager.class);
        villager.setProfession(shop.getProfession());
        villager.setCustomName(ChatColor.translateAlternateColorCodes('&', shop.getDisplayName()));
        villager.setCustomNameVisible(true);
        villager.setInvulnerable(true);
        villager.setPersistent(true);
        villager.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, shop.getId());
        villager.setRecipes(buildRecipes(shop));
        return villager;
    }

    public List<MerchantRecipe> buildRecipes(ShopType shop) {
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (String foodId : shop.getSells()) {
            FoodType food = foodManager.getFoodType(foodId);
            if (food == null || food.getPrice() < 0) {
                continue;
            }
            CoinType denomination = CoinEconomy.bestDenominationFor(food.getPrice(), economyManager);
            if (denomination == null) {
                continue;
            }
            int count = CoinEconomy.countIn(food.getPrice(), denomination);
            ItemStack cost = coinItemFactory.create(denomination, count);
            ItemStack result = foodManager.createFoodItem(food);

            MerchantRecipe recipe = new MerchantRecipe(result, 0, food.getTradeMaxUses(), true, food.getTradeXp(), 0.05F);
            recipe.addIngredient(cost);
            recipes.add(recipe);
        }
        return recipes;
    }

    /** Devuelve el id de la tienda si esta entidad es uno de nuestros chefs, o null si no lo es. */
    public String getShopId(Entity entity) {
        return entity.getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
    }
}
