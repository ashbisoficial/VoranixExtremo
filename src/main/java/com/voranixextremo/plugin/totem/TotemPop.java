package com.voranixextremo.plugin.totem;

import com.voranixextremo.plugin.util.Messages;
import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;

/**
 * Avisa en el chat cuando un jugador "popea" un tótem de la inmortalidad.
 * El mensaje se edita en messages.yml → totem-pop (%usuario% = nombre del jugador).
 */
public class TotemPop implements Listener {

    private final VoranixExtremoPlugin plugin;

    public TotemPop(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
    }

    // MONITOR + ignoreCancelled: solo avisa si el tótem de verdad se usó (otro plugin pudo cancelarlo)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemPop(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        String mensaje = Messages.texto("totem-pop", "");
        if (mensaje.isBlank()) {
            return;
        }
        Bukkit.broadcastMessage(ColorUtils.color(mensaje.replace("%usuario%", player.getName())));
    }
}
