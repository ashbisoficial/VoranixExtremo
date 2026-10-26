package com.voranixextremo.plugin.shops;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

/** /chef spawn <tienda> | /chef remove — aparece o elimina un aldeano chef. */
@CommandAlias("chef")
@CommandPermission("voranixextremo.chef.admin")
@Description("Aparece o elimina un aldeano chef que vende comida por monedas.")
public class ChefCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;
    private final ShopManager shopManager;
    private final ChefVillagerManager chefVillagerManager;

    public ChefCommand(VoranixExtremoPlugin plugin, ShopManager shopManager, ChefVillagerManager chefVillagerManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.chefVillagerManager = chefVillagerManager;
    }

    @Default
    @CatchUnknown
    public void onUsage(CommandSender sender) {
        sender.sendMessage("Uso: /chef <spawn <tienda>|remove>");
    }

    @Subcommand("spawn")
    @CommandCompletion("@tiendas")
    @Syntax("<tienda>")
    public void onSpawn(Player player, String tienda) {
        ShopType shop = shopManager.getShopType(tienda);
        if (shop == null) {
            player.sendMessage("Tienda desconocida. Disponibles: " + String.join(", ", shopManager.getShopTypes().keySet()));
            return;
        }
        chefVillagerManager.spawn(shop, player.getLocation());
        player.sendMessage("Apareció " + shop.getDisplayName() + ".");
    }

    @Subcommand("remove")
    public void onRemove(Player player) {
        Entity nearest = findNearestChef(player);
        if (nearest == null) {
            player.sendMessage("No hay ningún chef cerca (8 bloques).");
            return;
        }
        nearest.remove();
        player.sendMessage("Chef eliminado.");
    }

    private Entity findNearestChef(Player player) {
        Entity nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (!(entity instanceof Villager) || chefVillagerManager.getShopId(entity) == null) {
                continue;
            }
            double distanceSquared = entity.getLocation().distanceSquared(player.getLocation());
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = entity;
            }
        }
        return nearest;
    }
}
