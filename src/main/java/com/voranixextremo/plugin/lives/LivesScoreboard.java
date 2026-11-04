package com.voranixextremo.plugin.lives;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Tabla fija arriba a la derecha de la pantalla (el sidebar nativo de Minecraft: el cliente
 * SIEMPRE lo dibuja ahí, no hay forma de moverlo a otra esquina desde el server - si en algún
 * screenshot se ve más al centro es la captura/el GUI scale, no esto) con las vidas de cada
 * jugador, SOLO las suyas: cada uno tiene su propio Scoreboard (no el compartido del server),
 * así nadie ve la lista de todos.
 */
public final class LivesScoreboard implements Listener {

    private static final String OBJETIVO = "voranix_vidas";

    private final VoranixExtremoPlugin plugin;
    private final LivesManager livesManager;
    private BukkitTask tarea;
    private boolean activo;
    private String titulo;
    private String linea;

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
        tarea = Bukkit.getScheduler().runTaskTimer(plugin, this::actualizarTodos, 0L, 20L);
    }

    public void stop() {
        if (tarea != null) {
            tarea.cancel();
            tarea = null;
        }
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            quitar(jugador);
        }
    }

    /** Vuelve a leer lives.scoreboard del config.yml y refresca a todos los conectados ya mismo. */
    public void reload() {
        this.activo = plugin.getConfig().getBoolean("lives.scoreboard.enabled", true);
        this.titulo = ColorUtils.color(plugin.getConfig().getString("lives.scoreboard.titulo", "&c❤ &lVIDAS"));
        this.linea = ColorUtils.color(plugin.getConfig().getString("lives.scoreboard.linea", "&fVidas"));
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            quitar(jugador);
            if (activo) {
                actualizar(jugador);
            }
        }
    }

    private void actualizarTodos() {
        if (!activo) {
            return;
        }
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            actualizar(jugador);
        }
    }

    private void actualizar(Player jugador) {
        Scoreboard board = jugador.getScoreboard();
        if (board == null || board.equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            jugador.setScoreboard(board);
        }
        Objective objetivo = board.getObjective(OBJETIVO);
        if (objetivo == null) {
            objetivo = board.registerNewObjective(OBJETIVO, Criteria.DUMMY, titulo);
            objetivo.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else if (!objetivo.getDisplayName().equals(titulo)) {
            objetivo.setDisplayName(titulo);
        }
        objetivo.getScore(linea).setScore(livesManager.getLives(jugador));
    }

    private void quitar(Player jugador) {
        Scoreboard board = jugador.getScoreboard();
        if (board == null) {
            return;
        }
        Objective objetivo = board.getObjective(OBJETIVO);
        if (objetivo != null) {
            objetivo.unregister();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (activo) {
            actualizar(event.getPlayer());
        }
    }
}
