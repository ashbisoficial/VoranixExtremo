package com.voranixextremo.plugin.chatsystem;

import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.teamsystem.Team;
import com.voranixextremo.plugin.teamsystem.TeamManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.util.UUID;

public class Chat implements Listener {

    // Marcas temporales para %player% y %message%: así PlaceholderAPI no las confunde con las suyas
    private static final String MARCA_JUGADOR = "\u0000J\u0000";
    private static final String MARCA_MENSAJE = "\u0000M\u0000";

    private final VoranixExtremoPlugin plugin;
    private boolean enabled;
    private String format;
    private TeamManager teams;

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

    /** Para el chat del team (/team chat join); se conecta al arrancar el plugin. */
    public void setTeams(TeamManager teams) {
        this.teams = teams;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Team team = teams != null && teams.isEnChatDeTeam(player.getUniqueId()) ? teams.getTeam(player.getUniqueId()) : null;
        if (team != null) {
            // chat del team: solo lo reciben los integrantes conectados (y queda en la consola)
            event.getRecipients().clear();
            for (UUID uuid : team.getMembers()) {
                Player miembro = Bukkit.getPlayer(uuid);
                if (miembro != null) event.getRecipients().add(miembro);
            }
            event.setFormat(armarFormato(player, teams.getFormatoChat().replace("%team%", team.getName())));
        } else {
            if (!enabled || format == null || format.isBlank()) {
                return;
            }
            event.setFormat(armarFormato(player, format));
        }

        if (player.hasPermission("voranixextremo.chat.color")) {
            event.setMessage(ColorUtils.color(event.getMessage()));
        }
    }

    /** Pasa %player%, %message%, PlaceholderAPI y colores a un formato que entiende Bukkit. */
    private String armarFormato(Player player, String plantilla) {
        String texto = plantilla.replace("%player%", MARCA_JUGADOR).replace("%message%", MARCA_MENSAJE);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            texto = PlaceholderAPI.setPlaceholders(player, texto);
        }
        // El formato de Bukkit usa %1$s (jugador) y %2$s (mensaje); cualquier otro % se escapa
        return ColorUtils.color(texto).replace("%", "%%")
                .replace(MARCA_JUGADOR, "%1$s")
                .replace(MARCA_MENSAJE, "%2$s");
    }
}
