package com.voranixextremo.plugin.kills;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.economy.BankService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * "Trofeos" al matar a otro jugador en combate (PVP): un mini monumento con la cabeza
 * de la víctima montada sobre un muro, y monedas físicas que van directo al inventario
 * del asesino (no se tiran al piso). No se aplica a muertes por caída, lava, mobs, etc.
 * — solo a asesinatos entre jugadores.
 */
public class PvpKillRewards {

    private final VoranixExtremoPlugin plugin;
    private final BankService bankService;

    public PvpKillRewards(VoranixExtremoPlugin plugin, BankService bankService) {
        this.plugin = plugin;
        this.bankService = bankService;
    }

    public void drop(Player victim, Player killer, Location location) {
        if (!plugin.getConfig().getBoolean("pvp-kill-rewards.enabled", true)) {
            return;
        }

        World world = location.getWorld();
        if (world == null) {
            return;
        }

        if (plugin.getConfig().getBoolean("pvp-kill-rewards.head-drop", true)) {
            placeTrophy(victim, location);
        }

        if (plugin.getConfig().getBoolean("pvp-kill-rewards.coin-reward.enabled", true)) {
            double value = plugin.getConfig().getDouble("pvp-kill-rewards.coin-reward.value", 50);
            bankService.breakIntoCoins(value).ifPresent(coins -> giveToInventory(killer, coins));
        }
    }

    /** Pone un muro con la cabeza de la víctima montada encima, en el lugar de la muerte. */
    private void placeTrophy(OfflinePlayer victim, Location location) {
        Block wallBlock = location.getBlock();
        wallBlock.setType(resolveWallMaterial());

        Block headBlock = wallBlock.getRelative(0, 1, 0);
        headBlock.setType(Material.PLAYER_HEAD);
        if (headBlock.getState() instanceof Skull skull) {
            skull.setOwningPlayer(victim);
            skull.update(true);
        }
    }

    private Material resolveWallMaterial() {
        String name = plugin.getConfig().getString("pvp-kill-rewards.trophy-wall-material", "DEEPSLATE_BRICK_WALL");
        Material material = Material.matchMaterial(name.toUpperCase());
        return material != null ? material : Material.DEEPSLATE_BRICK_WALL;
    }

    private void giveToInventory(Player killer, List<ItemStack> coins) {
        for (ItemStack coin : coins) {
            Map<Integer, ItemStack> leftovers = killer.getInventory().addItem(coin);
            for (ItemStack leftover : leftovers.values()) {
                killer.getWorld().dropItemNaturally(killer.getLocation(), leftover);
            }
        }
    }
}
