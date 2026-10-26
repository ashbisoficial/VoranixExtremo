package com.voranixextremo.plugin.chatsystem;

import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;

/**
 * Cambia el formato del chat vanilla según chat.yml → chat.format.
 * %player% y %message% son del plugin; si PlaceholderAPI está instalado, también se
 * reemplazan sus placeholders (%luckperms_prefix%, etc.). Acepta colores & y hex (ColorUtils).
 */
public class Chat implements Listener {

    // Marcas temporales para %player% y %message%: así PlaceholderAPI no las confunde con las suyas
    private static final String MARCA_JUGADOR = "\u0000J\u0000";
    private static final String MARCA_MENSAJE = "\u0000M\u0000";

    private final VoranixExtremoPlugin plugin;
    private boolean enabled;
    private String format;

    public Chat(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /** Lee chat.yml (lo crea con los valores por defecto si no existe). */
    public void load() {
        File file = new File(plugin.getDataFolder(), "chat.yml");
        if (!file.exists()) {
            plugin.saveResource("chat.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.enabled = config.getBoolean("chat.enabled", true);
        this.format = config.getString("chat.format", "&f%player%&7: &f%message%");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!enabled || format == null || format.isBlank()) {
            return;
        }
        Player player = event.getPlayer();
        String texto = format.replace("%player%", MARCA_JUGADOR).replace("%message%", MARCA_MENSAJE);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            texto = PlaceholderAPI.setPlaceholders(player, texto);
        }
        // El formato de Bukkit usa %1$s (jugador) y %2$s (mensaje); cualquier otro % se escapa
        texto = ColorUtils.color(texto).replace("%", "%%")
                .replace(MARCA_JUGADOR, "%1$s")
                .replace(MARCA_MENSAJE, "%2$s");
        event.setFormat(texto);

        if (player.hasPermission("voranixextremo.chat.color")) {
            event.setMessage(ColorUtils.color(event.getMessage()));
        }
    }
}
