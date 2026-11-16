package com.voranixextremo.plugin.lives;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Las vidas de cada jugador en SU pantalla, arriba a la derecha: un cristal morado por vida y,
 * por cada vida perdida, el mismo cristal gris y roto (a la derecha de los que le quedan).
 *
 * Sin mods: es el título de una boss bar propia de cada jugador, con la fuente
 * voranixextremo:vidas del resource pack. La boss bar sale arriba al centro, así que el shader de
 * texto del pack (rendertype_text.vsh) reconoce el color (80, 92, 40) y la corre a la esquina; la
 * barra en sí no se ve porque el pack deja transparente la boss bar blanca (textures/gui/bars.png).
 * Sin el pack se vería un recuadro en vez de cada cristal, por eso solo tiene sentido con el pack.
 *
 * El título se arma de derecha a izquierda y con ancho total 0 (ver los espacios de vidas.json):
 * así el último cristal siempre queda pegado al borde, tenga el jugador las vidas que tenga.
 *
 * Las vidas que le quedan tienen un aura animada (48 cuadros, vida_llena_0..47.png): cada
 * CUADRO_TICKS se vuelve a mandar el título con el cuadro siguiente, cada cristal desfasado del
 * de al lado para que no latan todos juntos. Se apaga con lives.hud-animado: false.
 */
public final class LivesHud implements Listener {

    private static final Key FUENTE = Key.key("voranixextremo", "vidas");
    /** Tiene que coincidir con el color que busca rendertype_text.vsh. */
    private static final TextColor MARCA = TextColor.color(80, 92, 40);
    /** Primer cuadro del aura; los 48 cuadros van seguidos (0xE120..0xE14F). */
    private static final char LLENA = (char) 0xE120;
    private static final int CUADROS = 48;
    /** Cuántos cuadros se adelanta cada cristal respecto del de su izquierda. */
    private static final int DESFASE = 6;
    /** Un cuadro por tick (20 por segundo): el aura da una vuelta cada 2,4 segundos. */
    private static final long CUADRO_TICKS = 1L;
    private static final char ROTA = (char) 0xE101;
    /** -15: del punto de partida al lugar del cristal de más a la derecha. */
    private static final char PRIMER_PASO = (char) 0xE110;
    /** -34: después de dibujar un cuadro (que avanza 19), al lugar del siguiente a la izquierda. */
    private static final char PASO = (char) 0xE111;
    /** +15 y -19: para devolver el cursor a 0 al final (ancho total 0). */
    private static final char VOLVER = (char) 0xE112;
    private static final char AJUSTE_FINAL = (char) 0xE113;
    /** Unos segundos después de entrar, así queda debajo de las otras boss bars en vez de empujarlas. */
    private static final long RETRASO_AL_ENTRAR = 60L;

    private final VoranixExtremoPlugin plugin;
    private final LivesManager lives;
    private final BukkitAudiences audiences;
    private final Map<UUID, BossBar> barras = new HashMap<>();
    private final BukkitTask animacion;
    private int cuadro;

    public LivesHud(VoranixExtremoPlugin plugin, LivesManager lives) {
        this.plugin = plugin;
        this.lives = lives;
        this.audiences = BukkitAudiences.create(plugin);
        this.animacion = Bukkit.getScheduler().runTaskTimer(plugin, this::siguienteCuadro, CUADRO_TICKS, CUADRO_TICKS);
    }

    private boolean activo() {
        return plugin.getConfig().getBoolean("lives.hud", true);
    }

    private void siguienteCuadro() {
        if (barras.isEmpty() || !plugin.getConfig().getBoolean("lives.hud-animado", true)) {
            return;
        }
        cuadro = (cuadro + 1) % CUADROS;
        for (Map.Entry<UUID, BossBar> entrada : barras.entrySet()) {
            Player jugador = Bukkit.getPlayer(entrada.getKey());
            if (jugador != null) {
                entrada.getValue().name(titulo(lives.getLives(jugador), lives.getMaxLives(), cuadro));
            }
        }
    }

    /** Vuelve a dibujar las vidas de este jugador (si está conectado). */
    public void actualizar(OfflinePlayer jugador) {
        Player online = jugador.getPlayer();
        if (online == null || !online.isOnline()) {
            return;
        }
        if (!activo()) {
            ocultar(online);
            return;
        }
        Component titulo = titulo(lives.getLives(online), lives.getMaxLives(), cuadro);
        BossBar barra = barras.get(online.getUniqueId());
        if (barra == null) {
            barra = BossBar.bossBar(titulo, 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            barras.put(online.getUniqueId(), barra);
            audiences.player(online).showBossBar(barra);
        } else {
            barra.name(titulo);
        }
    }

    /** Para después de un reload (puede haber cambiado lives.max o lives.hud). */
    public void actualizarTodos() {
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            actualizar(jugador);
        }
    }

    private void ocultar(Player jugador) {
        BossBar barra = barras.remove(jugador.getUniqueId());
        if (barra != null) {
            audiences.player(jugador).hideBossBar(barra);
        }
    }

    private static Component titulo(int vidas, int maximo, int cuadro) {
        int total = Math.max(maximo, vidas);
        int rotas = Math.max(0, total - Math.max(vidas, 0));
        StringBuilder texto = new StringBuilder();
        texto.append(PRIMER_PASO);
        // de derecha a izquierda: primero las rotas, después las que le quedan
        for (int i = 0; i < total; i++) {
            if (i > 0) {
                texto.append(PASO);
            }
            int desdeLaIzquierda = total - 1 - i;
            texto.append(i < rotas ? ROTA : (char) (LLENA + (cuadro + desdeLaIzquierda * DESFASE) % CUADROS));
        }
        texto.append(String.valueOf(VOLVER).repeat(total)).append(AJUSTE_FINAL);
        return Component.text(texto.toString()).font(FUENTE).color(MARCA);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player jugador = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> actualizar(jugador), RETRASO_AL_ENTRAR);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        barras.remove(event.getPlayer().getUniqueId());
    }

    public void close() {
        animacion.cancel();
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            ocultar(jugador);
        }
        audiences.close();
    }
}
