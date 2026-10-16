package com.voranixextremo.plugin.lives;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final LivesManager livesManager;

    public PlayerJoinListener(LivesManager livesManager) {
        this.livesManager = livesManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (livesManager.isEliminated(event.getPlayer())) {
            event.getPlayer().setGameMode(livesManager.getEliminationGameMode());
        }
    }
}
