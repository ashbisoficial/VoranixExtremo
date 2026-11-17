package com.voranixextremo.plugin.lives;

import com.voranixextremo.plugin.util.Messages;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Efectos "de espectáculo" que se reproducen cuando un jugador muere: partícula en el
 * punto de la muerte, sonido global, rayo decorativo (sin daño) y, para todos, la animación del
 * Ojo (con el texto arriba de la hotbar) o, si se apaga death-effects.ojo, un título grande.
 * Todo es configurable en config.yml -> death-effects.
 */
public class DeathEffects {

    // Tiempos del título en ticks (20 = 1 segundo): aparece en 0,3 s, dura 2 s y se va en 0,5 s
    private static final int TITLE_FADE_IN = 6, TITLE_STAY = 40, TITLE_FADE_OUT = 10;

    private final VoranixExtremoPlugin plugin;
    private final OjoMuerte ojo;

    public DeathEffects(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        this.ojo = new OjoMuerte(plugin);
    }

    public void close() {
        ojo.close();
    }

    public void play(Player player, boolean eliminated) {
        if (!plugin.getConfig().getBoolean("death-effects.enabled", true)) {
            return;
        }

        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        spawnParticle(world, location);
        playSound();

        if (plugin.getConfig().getBoolean("death-effects.lightning-effect", true)) {
            world.strikeLightningEffect(location);
        }

        boolean conTexto = plugin.getConfig().getBoolean("death-effects.title.enabled", true);
        if (plugin.getConfig().getBoolean("death-effects.ojo.enabled", true)) {
            // el Ojo ocupa el título: el texto va arriba de la hotbar
            ojo.mostrar(conTexto ? texto(player, eliminated) : null);
        } else if (conTexto) {
            showTitle(player, eliminated);
        }
    }

    private void spawnParticle(World world, Location location) {
        String name = plugin.getConfig().getString("death-effects.particle.name", "EXPLOSION_HUGE");
        int count = Math.max(1, plugin.getConfig().getInt("death-effects.particle.count", 1));

        try {
            Particle particle = Particle.valueOf(name.toUpperCase());
            world.spawnParticle(particle, location.clone().add(0, 1, 0), count);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Partícula de muerte inválida en config.yml: " + name);
        }
    }

    private void playSound() {
        String soundName = plugin.getConfig().getString("death-effects.sound.name", "ENTITY_GENERIC_EXPLODE");
        float volume = (float) plugin.getConfig().getDouble("death-effects.sound.volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("death-effects.sound.pitch", 1.0);

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Sonido de muerte inválido en config.yml: " + soundName);
            return;
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), sound, volume, pitch);
        }
    }

    private void showTitle(Player deadPlayer, boolean eliminated) {
        String colored = texto(deadPlayer, eliminated);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendTitle(colored, "", TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT);
        }
    }

    private String texto(Player deadPlayer, boolean eliminated) {
        return Messages.sinPrefijo(eliminated ? "eliminated-text" : "death-text", "%player%", deadPlayer.getName());
    }
}
