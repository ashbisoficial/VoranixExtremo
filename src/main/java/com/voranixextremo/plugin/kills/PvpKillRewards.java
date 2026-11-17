package com.voranixextremo.plugin.kills;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/**
 * "Trofeos" al matar a otro jugador en combate (PVP): un mini monumento con la cabeza
 * de la víctima montada sobre un muro, y la recompensa: los comandos de
 * pvp-kill-rewards.commands, ejecutados por consola. No se aplica a muertes por caída, lava, mobs, etc.
 * — solo a asesinatos entre jugadores.
 */
public class PvpKillRewards {

    private final VoranixExtremoPlugin plugin;

    public PvpKillRewards(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
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

        ejecutarComandos(victim, killer);
    }

    /** La recompensa: los comandos de pvp-kill-rewards.commands, por consola (permisos completos). */
    private void ejecutarComandos(Player victim, Player killer) {
        for (String comando : plugin.getConfig().getStringList("pvp-kill-rewards.commands")) {
            if (comando == null || comando.isBlank()) continue;
            String cmd = (comando.startsWith("/") ? comando.substring(1) : comando)
                    .replace("%killer%", killer.getName())
                    .replace("%victim%", victim.getName());
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Recompensa de kill: falló el comando '" + cmd + "'", e);
            }
        }
    }

    /** Pone un muro con la cabeza de la víctima montada encima, en el lugar de la muerte,
     * y le suma una breve animación de partículas + sonido para que se note el momento. */
    private void placeTrophy(OfflinePlayer victim, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        Block wallBlock = location.getBlock();
        wallBlock.setType(resolveWallMaterial());

        Block headBlock = wallBlock.getRelative(0, 1, 0);
        headBlock.setType(Material.PLAYER_HEAD);
        if (headBlock.getState() instanceof Skull skull) {
            skull.setOwningPlayer(victim);
            skull.update(true);
        }

        Location headCenter = headBlock.getLocation().add(0.5, 0.3, 0.5);
        world.playSound(headCenter, resolveTrophySound(), 1.0F, 1.0F);
        playTrophyRevealAnimation(world, headCenter);
    }

    private Material resolveWallMaterial() {
        String name = plugin.getConfig().getString("pvp-kill-rewards.trophy-wall-material", "DEEPSLATE_BRICK_WALL");
        Material material = Material.matchMaterial(name.toUpperCase());
        return material != null ? material : Material.DEEPSLATE_BRICK_WALL;
    }

    private Sound resolveTrophySound() {
        String name = plugin.getConfig().getString("pvp-kill-rewards.trophy-sound", "ENTITY_WITHER_AMBIENT");
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Sonido de trofeo inválido en config.yml: " + name);
            return Sound.ENTITY_WITHER_AMBIENT;
        }
    }

    /** Partículas de alma girando alrededor de la cabeza durante unos segundos, como
     * "revelación" del trofeo recién puesto — después queda el monumento solo, estático. */
    private void playTrophyRevealAnimation(World world, Location headCenter) {
        int durationTicks = plugin.getConfig().getInt("pvp-kill-rewards.trophy-particle-duration-ticks", 60);

        BukkitTask[] holder = new BukkitTask[1];
        int[] elapsed = {0};
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (elapsed[0] >= durationTicks) {
                holder[0].cancel();
                return;
            }
            double angle = Math.toRadians(elapsed[0] * 24.0);
            double radius = 0.6;
            Location point = headCenter.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            world.spawnParticle(Particle.SOUL, point, 1, 0, 0, 0, 0);
            elapsed[0] += 2;
        }, 0L, 2L);
    }
}
