package com.voranixextremo.plugin.lives;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {

    private final VoranixExtremoPlugin plugin;
    private final LivesManager livesManager;
    private final DeathEffects deathEffects;

    public PlayerDeathListener(VoranixExtremoPlugin plugin, LivesManager livesManager, DeathEffects deathEffects) {
        this.plugin = plugin;
        this.livesManager = livesManager;
        this.deathEffects = deathEffects;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (livesManager.isEliminated(player)) {
            return;
        }

        int remaining = livesManager.loseLife(player);
        boolean eliminated = remaining <= 0;

        if (eliminated) {
            Messages.broadcast(plugin, "eliminated", "%player%", player.getName());
        } else {
            Messages.broadcast(plugin, "life-lost", "%player%", player.getName(), "%vidas%", String.valueOf(remaining));
        }

        deathEffects.play(player, eliminated);
    }
}
