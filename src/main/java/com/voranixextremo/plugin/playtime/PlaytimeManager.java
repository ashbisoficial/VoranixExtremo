package com.voranixextremo.plugin.playtime;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tope de tiempo jugado: todos arrancan con el mismo presupuesto (config playtime.limite-horas,
 * 3 por defecto), que se va gastando segundo a segundo mientras están conectados - así nadie se
 * adelanta farmeando más horas que el resto. Cada uno ve SOLO su propia barra (BossBar, arriba de
 * la pantalla, no es un sidebar compartido) vaciándose de verde a rojo. Al llegar a 0 pasa a
 * espectador, hasta que staff le resetee el tiempo o le agregue más con /tiempo.
 */
public final class PlaytimeManager implements Listener {

    private final VoranixExtremoPlugin plugin;
    private final File archivo;
    private final Map<UUID, Long> segundosJugados = new HashMap<>();
    private final Map<UUID, BossBar> barras = new HashMap<>();
    private long limiteSegundos;
    private BukkitTask tarea;

    public PlaytimeManager(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        this.archivo = new File(plugin.getDataFolder(), "playtime.yml");
        reloadSettings();
        cargar();
    }

    /** Vuelve a leer playtime.limite-horas (lo jugado de cada uno no se toca). */
    public void reloadSettings() {
        double horas = plugin.getConfig().getDouble("playtime.limite-horas", 3.0);
        this.limiteSegundos = Math.max(60, Math.round(horas * 3600));
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            actualizarBarra(jugador);
        }
    }

    public void start() {
        if (tarea != null) {
            tarea.cancel();
        }
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            crearBarra(jugador);
        }
        tarea = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (tarea != null) {
            tarea.cancel();
            tarea = null;
        }
        for (BossBar barra : barras.values()) {
            barra.removeAll();
        }
        barras.clear();
        guardar();
    }

    private void tick() {
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            UUID id = jugador.getUniqueId();
            long usados = segundosJugados.getOrDefault(id, 0L);
            if (usados < limiteSegundos) {
                usados++;
                segundosJugados.put(id, usados);
                if (usados >= limiteSegundos) {
                    agotar(jugador);
                }
            }
            actualizarBarra(jugador);
        }
    }

    private void agotar(Player jugador) {
        if (jugador.getGameMode() != GameMode.SPECTATOR) {
            jugador.setGameMode(GameMode.SPECTATOR);
        }
        Messages.send(jugador, plugin, "playtime-agotado");
    }

    public long getSegundosJugados(OfflinePlayer jugador) {
        return segundosJugados.getOrDefault(jugador.getUniqueId(), 0L);
    }

    public long getSegundosRestantes(OfflinePlayer jugador) {
        return Math.max(0, limiteSegundos - getSegundosJugados(jugador));
    }

    public long getLimiteSegundos() {
        return limiteSegundos;
    }

    public void setSegundosJugados(OfflinePlayer jugador, long segundos) {
        segundosJugados.put(jugador.getUniqueId(), Math.max(0, segundos));
        guardar();
        if (jugador.isOnline() && jugador.getPlayer() != null) {
            actualizarBarra(jugador.getPlayer());
        }
    }

    /** Le agrega (o saca, con un número negativo) esa cantidad de segundos al tiempo YA USADO. */
    public void agregarSegundos(OfflinePlayer jugador, long delta) {
        setSegundosJugados(jugador, getSegundosJugados(jugador) + delta);
    }

    /** Vuelve a dejarlo en 0 usados (presupuesto completo) y lo saca de espectador si estaba ahí por esto. */
    public void resetear(OfflinePlayer jugador) {
        setSegundosJugados(jugador, 0);
        if (jugador.isOnline() && jugador.getPlayer() != null && jugador.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            jugador.getPlayer().setGameMode(GameMode.SURVIVAL);
        }
    }

    private void crearBarra(Player jugador) {
        if (barras.containsKey(jugador.getUniqueId())) {
            return;
        }
        BossBar barra = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
        barra.addPlayer(jugador);
        barras.put(jugador.getUniqueId(), barra);
        actualizarBarra(jugador);
    }

    private void actualizarBarra(Player jugador) {
        BossBar barra = barras.get(jugador.getUniqueId());
        if (barra == null) {
            crearBarra(jugador);
            return;
        }
        long restantes = getSegundosRestantes(jugador);
        double progreso = limiteSegundos > 0 ? Math.max(0, Math.min(1, (double) restantes / limiteSegundos)) : 0;
        barra.setProgress(progreso);
        barra.setColor(progreso > 0.5 ? BarColor.GREEN : progreso > 0.2 ? BarColor.YELLOW : BarColor.RED);
        String plantilla = plugin.getConfig().getString("playtime.titulo", "&e⏳ &fTiempo restante: &a%tiempo%");
        barra.setTitle(ColorUtils.color(plantilla.replace("%tiempo%", formatear(restantes))));
    }

    /** "2h 14m", o "45m" si falta menos de una hora, o "0m" en 0. */
    public static String formatear(long segundos) {
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        if (horas > 0) {
            return horas + "h " + minutos + "m";
        }
        return minutos + "m";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        crearBarra(event.getPlayer());
        if (getSegundosRestantes(event.getPlayer()) <= 0) {
            agotar(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BossBar barra = barras.remove(event.getPlayer().getUniqueId());
        if (barra != null) {
            barra.removeAll();
        }
        guardar();
    }

    private void cargar() {
        if (!archivo.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(archivo);
        for (String clave : yaml.getKeys(false)) {
            try {
                segundosJugados.put(UUID.fromString(clave), yaml.getLong(clave));
            } catch (IllegalArgumentException ignored) {
                // clave corrupta o que no es un UUID: se ignora
            }
        }
    }

    public void guardar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entrada : segundosJugados.entrySet()) {
            yaml.set(entrada.getKey().toString(), entrada.getValue());
        }
        try {
            yaml.save(archivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar playtime.yml", e);
        }
    }
}
