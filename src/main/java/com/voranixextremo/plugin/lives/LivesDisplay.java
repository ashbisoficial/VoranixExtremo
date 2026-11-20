package com.voranixextremo.plugin.lives;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Muestra las vidas de cada jugador junto a su nombre, sobre la cabeza y en el tab, con el icono
 * de cristal del pack (fuente voranixextremo:vidas, glifo ): " <icono> <vidas>".
 *
 * Cada jugador va en un team propio que el servidor le manda a cada cliente con PacketEvents. No se
 * usa el scoreboard de Spigot: su Team solo acepta texto plano, y el icono necesita la fuente del
 * pack. Sin el pack el icono se ve como un cuadro vacío, pero el número sigue saliendo.
 *
 * Se actualiza cada vez que cambian las vidas (LivesManager.setAlCambiar), y se crea o borra cuando
 * alguien entra o sale.
 */
public final class LivesDisplay implements Listener {

    private static final Key FUENTE = Key.key("voranixextremo", "vidas");
    private static final String ICONO = "";
    /** Nombre del team en el cliente: "vx_" + 13 caracteres del UUID (el máximo es 16). */
    private static final String PREFIJO = "vx_";

    private final VoranixExtremoPlugin plugin;
    private final LivesManager lives;

    public LivesDisplay(VoranixExtremoPlugin plugin, LivesManager lives) {
        this.plugin = plugin;
        this.lives = lives;
    }

    /** Registra el listener y manda los teams de todos a todos (jugadores ya conectados). */
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player dueño : Bukkit.getOnlinePlayers()) {
                crear(viewer, dueño);
            }
        }
    }

    /** Vuelve a mandar el sufijo de este jugador a todos los que lo ven (cambió su número de vidas). */
    public void actualizar(OfflinePlayer jugador) {
        Player dueño = jugador.getPlayer();
        if (dueño == null || !activo()) {
            return;
        }
        WrapperPlayServerTeams update = new WrapperPlayServerTeams(
                nombreTeam(dueño.getUniqueId()), WrapperPlayServerTeams.TeamMode.UPDATE, info(dueño), Collections.emptyList());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            enviar(viewer, update);
        }
    }

    private boolean activo() {
        return plugin.getConfig().getBoolean("lives.mostrar-en-nombre", true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player nuevo = event.getPlayer();
        // un tick después, cuando el cliente ya tiene su mundo y el tab
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!nuevo.isOnline() || !activo()) {
                return;
            }
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                crear(viewer, nuevo);
                crear(nuevo, viewer);
            }
            actualizar(nuevo);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player saliendo = event.getPlayer();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(saliendo)) {
                continue;
            }
            WrapperPlayServerTeams borrar = new WrapperPlayServerTeams(
                    nombreTeam(saliendo.getUniqueId()), WrapperPlayServerTeams.TeamMode.REMOVE, (WrapperPlayServerTeams.ScoreBoardTeamInfo) null, Collections.emptyList());
            enviar(viewer, borrar);
        }
    }

    /** Crea en el cliente de viewer el team de dueño, con su nombre dentro y el sufijo de vidas. */
    private void crear(Player viewer, Player dueño) {
        if (!activo()) {
            return;
        }
        WrapperPlayServerTeams crear = new WrapperPlayServerTeams(
                nombreTeam(dueño.getUniqueId()), WrapperPlayServerTeams.TeamMode.CREATE, info(dueño), List.of(dueño.getName()));
        enviar(viewer, crear);
    }

    private WrapperPlayServerTeams.ScoreBoardTeamInfo info(Player dueño) {
        Component sufijo = Component.text(" ")
                .append(Component.text(ICONO).font(FUENTE))
                .append(Component.text(" " + lives.getLives(dueño), NamedTextColor.LIGHT_PURPLE));
        return new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.empty(), Component.empty(), sufijo,
                WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                WrapperPlayServerTeams.CollisionRule.ALWAYS,
                NamedTextColor.WHITE,
                WrapperPlayServerTeams.OptionData.NONE);
    }

    private static String nombreTeam(UUID uuid) {
        return PREFIJO + uuid.toString().replace("-", "").substring(0, 13);
    }

    private static void enviar(Player viewer, com.github.retrooper.packetevents.wrapper.PacketWrapper<?> paquete) {
        if (viewer.isOnline()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, paquete);
        }
    }
}
