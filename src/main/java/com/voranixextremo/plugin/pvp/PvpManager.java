package com.voranixextremo.plugin.pvp;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public class PvpManager {

    private final VoranixExtremoPlugin plugin;
    private boolean pvpEnabled;

    public PvpManager(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        this.pvpEnabled = plugin.getConfig().getBoolean("pvp.enabled-by-default", true);
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean enabled) {
        this.pvpEnabled = enabled;
        playToggleSound();
    }

    public boolean toggle() {
        setPvpEnabled(!pvpEnabled);
        return pvpEnabled;
    }

    private void playToggleSound() {
        String soundName = plugin.getConfig().getString("pvp.sound.name", "ENTITY_ENDER_DRAGON_GROWL");
        float volume = (float) plugin.getConfig().getDouble("pvp.sound.volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("pvp.sound.pitch", 1.0);

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Sonido de PVP inválido en config.yml: " + soundName);
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}
