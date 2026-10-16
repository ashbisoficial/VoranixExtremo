package com.voranixextremo.plugin.shops;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.List;

/**
 * /chef spawn <tienda> — aparece un aldeano chef en la posición del jugador.
 * /chef remove — elimina el chef más cercano (8 bloques).
 */
public class ChefCommand implements CommandExecutor, TabCompleter {

    private final VoranixExtremoPlugin plugin;
    private final ShopManager shopManager;
    private final ChefVillagerManager chefVillagerManager;

    public ChefCommand(VoranixExtremoPlugin plugin, ShopManager shopManager, ChefVillagerManager chefVillagerManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.chefVillagerManager = chefVillagerManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voranixextremo.chef.admin")) {
            Messages.send(sender, plugin, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Messages.send(sender, plugin, "only-players");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("Uso: /chef <spawn <tienda>|remove>");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("spawn")) {
            if (args.length < 2) {
                sender.sendMessage("Uso: /chef spawn <tienda>. Disponibles: " + String.join(", ", shopManager.getShopTypes().keySet()));
                return true;
            }
            ShopType shop = shopManager.getShopType(args[1]);
            if (shop == null) {
                sender.sendMessage("Tienda desconocida. Disponibles: " + String.join(", ", shopManager.getShopTypes().keySet()));
                return true;
            }
            chefVillagerManager.spawn(shop, player.getLocation());
            sender.sendMessage("Apareció " + shop.getDisplayName() + ".");
            return true;
        }

        if (sub.equals("remove")) {
            Entity nearest = findNearestChef(player);
            if (nearest == null) {
                sender.sendMessage("No hay ningún chef cerca (8 bloques).");
                return true;
            }
            nearest.remove();
            sender.sendMessage("Chef eliminado.");
            return true;
        }

        sender.sendMessage("Uso: /chef <spawn <tienda>|remove>");
        return true;
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("spawn", "remove");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return new ArrayList<>(shopManager.getShopTypes().keySet());
        }
        return List.of();
    }
}
