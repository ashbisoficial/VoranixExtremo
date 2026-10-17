package com.voranixextremo.plugin.lives;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class LivesManager {

    private final VoranixExtremoPlugin plugin;
    private final File dataFile;
    private final YamlConfiguration dataConfig;
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final int maxLives;
    private final GameMode eliminationGameMode;

    public LivesManager(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        this.maxLives = Math.max(1, plugin.getConfig().getInt("lives.max", 5));
        this.eliminationGameMode = parseGameMode(plugin.getConfig().getString("lives.elimination-gamemode", "SPECTATOR"));

        this.dataFile = new File(plugin.getDataFolder(), "lives.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        load();
    }

    private static GameMode parseGameMode(String name) {
        try {
            return GameMode.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            return GameMode.SPECTATOR;
        }
    }

    private void load() {
        for (String key : dataConfig.getKeys(false)) {
            try {
                lives.put(UUID.fromString(key), dataConfig.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // Clave corrupta o que no es un UUID: se ignora.
            }
        }
    }

    public void save() {
        for (Map.Entry<UUID, Integer> entry : lives.entrySet()) {
            dataConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar lives.yml", e);
        }
    }

    public int getMaxLives() {
        return maxLives;
    }

    public GameMode getEliminationGameMode() {
        return eliminationGameMode;
    }

    public int getLives(OfflinePlayer player) {
        return lives.computeIfAbsent(player.getUniqueId(), id -> maxLives);
    }

    public boolean isEliminated(OfflinePlayer player) {
        return getLives(player) <= 0;
    }

    public void setLives(OfflinePlayer player, int amount) {
        lives.put(player.getUniqueId(), Math.max(0, amount));
    }

    public void resetLives(OfflinePlayer player) {
        setLives(player, maxLives);
        if (player.isOnline() && player.getPlayer() != null && player.getPlayer().getGameMode() == eliminationGameMode) {
            player.getPlayer().setGameMode(GameMode.SURVIVAL);
        }
    }

    /**
     * Resta una vida al jugador y lo elimina (cambia su gamemode) si llega a 0.
     *
     * @return las vidas restantes tras la resta.
     */
    public int loseLife(Player player) {
        int remaining = Math.max(0, getLives(player) - 1);
        setLives(player, remaining);

        if (remaining <= 0) {
            player.setGameMode(eliminationGameMode);
        }
        return remaining;
    }

    /** Elimina al jugador directamente (vidas a 0), sin importar cuántas tenía. Para
     * sucesos que eliminan de una, como fallar un Momento Revill. */
    public void eliminate(Player player) {
        setLives(player, 0);
        player.setGameMode(eliminationGameMode);
    }
}
