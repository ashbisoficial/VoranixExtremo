package com.voranixextremo.plugin.util;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Le ofrece a cada jugador el resource pack con las texturas de las monedas
 * (y cualquier otra textura del proyecto) apenas se conecta, si hay una URL
 * configurada en resourcepack.url.
 */
public class ResourcePackListener implements Listener {

    private final VoranixExtremoPlugin plugin;

    public ResourcePackListener(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String url = plugin.getConfig().getString("resourcepack.url", "");
        if (url == null || url.isBlank()) {
            return;
        }
        event.getPlayer().setResourcePack(url);
    }
}
