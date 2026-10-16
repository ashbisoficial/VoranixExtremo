package com.voranixextremo.plugin.kills;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.economy.BankService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * "Trofeos" que caen al suelo cuando un jugador mata a otro en combate (PVP): la
 * cabeza de la víctima (con su skin) y, opcionalmente, monedas físicas como botín.
 * No se aplica a muertes por caída, lava, mobs, etc. — solo a asesinatos entre jugadores.
 */
public class PvpKillRewards {

    private final VoranixExtremoPlugin plugin;
    private final BankService bankService;

    public PvpKillRewards(VoranixExtremoPlugin plugin, BankService bankService) {
        this.plugin = plugin;
        this.bankService = bankService;
    }

    public void drop(Player victim, Location location) {
        if (!plugin.getConfig().getBoolean("pvp-kill-rewards.enabled", true)) {
            return;
        }

        World world = location.getWorld();
        if (world == null) {
            return;
        }

        if (plugin.getConfig().getBoolean("pvp-kill-rewards.head-drop", true)) {
            world.dropItemNaturally(location, createHead(victim));
        }

        if (plugin.getConfig().getBoolean("pvp-kill-rewards.coin-reward.enabled", true)) {
            double value = plugin.getConfig().getDouble("pvp-kill-rewards.coin-reward.value", 50);
            bankService.breakIntoCoins(value).ifPresent(coins ->
                    coins.forEach(coin -> world.dropItemNaturally(location, coin)));
        }
    }

    private ItemStack createHead(OfflinePlayer victim) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(victim);
            meta.setDisplayName(ChatColor.YELLOW + "Cabeza de " + victim.getName());
            head.setItemMeta(meta);
        }
        return head;
    }
}
