package com.voranixextremo.plugin.lives;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashSet;
import java.util.Set;

/**
 * Tabla lateral (arriba a la derecha de la pantalla) con las vidas de todos los jugadores
 * conectados, ordenadas solas de mayor a menor (así funciona el sidebar de Minecraft).
 * Usa el scoreboard principal del servidor: no pisa nada del team system (ese va por paquetes
 * de PacketEvents, no toca Scoreboard).
 */
public final class LivesScoreboard implements Listener {

    private static final String OBJETIVO = "voranix_vidas";

    private final VoranixExtremoPlugin plugin;
    private final LivesManager livesManager;
    private BukkitTask tarea;
    private boolean activo;
    private String titulo;

    public LivesScoreboard(VoranixExtremoPlugin plugin, LivesManager livesManager) {
        this.plugin = plugin;
        this.livesManager = livesManager;
    }

    public void start() {
        reload();
        if (tarea != null) {
            tarea.cancel();
        }
        // cada 20 ticks (1 s) alcanza: las vidas no cambian más seguido que eso y así se
        // refleja cualquier cambio (muerte, /vidas set, Momento Revill...) sin enganchar
        // esta clase a cada lugar del código que pueda tocar una vida.
        tarea = Bukkit.getScheduler().runTaskTimer(plugin, this::actualizar, 0L, 20L);
    }

    public void stop() {
        if (tarea != null) {
            tarea.cancel();
            tarea = null;
        }
        Objective objetivo = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(OBJETIVO);
        if (objetivo != null) {
            objetivo.unregister();
        }
    }

    /** Vuelve a leer lives.scoreboard del config.yml. */
    public void reload() {
        this.activo = plugin.getConfig().getBoolean("lives.scoreboard.enabled", true);
        this.titulo = ColorUtils.color(plugin.getConfig().getString("lives.scoreboard.titulo", "&c❤ &lVIDAS"));
        if (!activo) {
            Objective objetivo = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(OBJETIVO);
            if (objetivo != null) {
                objetivo.unregister();
            }
        }
    }

    private void actualizar() {
        if (!activo) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objetivo = board.getObjective(OBJETIVO);
        if (objetivo == null) {
            objetivo = board.registerNewObjective(OBJETIVO, Criteria.DUMMY, titulo);
            objetivo.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else if (!objetivo.getDisplayName().equals(titulo)) {
            objetivo.setDisplayName(titulo);
        }

        Set<String> vigentes = new HashSet<>();
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            String nombre = jugador.getName();
            vigentes.add(nombre);
            objetivo.getScore(nombre).setScore(livesManager.getLives(jugador));
        }
        // saca del scoreboard a quien ya no está conectado
        for (String entrada : board.getEntries()) {
            if (!vigentes.contains(entrada)) {
                board.resetScores(entrada);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (activo) {
            actualizar();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (activo) {
            Bukkit.getScoreboardManager().getMainScoreboard().resetScores(event.getPlayer().getName());
        }
    }
}
