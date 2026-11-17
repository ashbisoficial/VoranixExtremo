package com.voranixextremo.plugin.lives;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;

/**
 * La animación del Ojo cuando muere alguien: en la pantalla de todos, con su sonido. Antes la hacía
 * el script muerte_ojo de VoranixConditions; ahora va acá, sin depender de otro plugin.
 *
 * Usa lo que trae el resource pack (el combinado del VPS, que incluye el pack OjoMuerte en HD):
 *   - fuente ojo:muerte: 120 cuadros de 1024x768 px renderizados del modelo ojo_grande.bbmodel,
 *     cada uno en 4x3 piezas de 256 px (ver tools/ojo/RenderizarOjo.js); cada fila se escribe
 *     PIEZA JUNTAR PIEZA JUNTAR PIEZA JUNTAR PIEZA, y entre filas ATRAS vuelve al comienzo, con
 *     PIEZA(i,q) = U+E200 + 12*i + q;
 *   - sonido ojo:muerte;
 *   - el color (84, 92, 44): el shader (rendertype_text.vsh) le quita la sombra y lo agranda
 *     hasta casi todo el alto de la pantalla.
 * Se manda un título nuevo por tick, sin desvanecerse entre cuadros.
 */
public final class OjoMuerte {

    private static final Key FUENTE = Key.key("ojo", "muerte");
    /** Tiene que coincidir con el color que busca rendertype_text.vsh (lo agranda y le quita la sombra). */
    private static final TextColor SIN_SOMBRA = TextColor.color(84, 92, 44);
    private static final int CUADROS = 120;
    /** Piezas de cada cuadro: 4 de ancho por 3 de alto. */
    private static final int COLUMNAS = 4, FILAS = 3;
    private static final int PIEZAS = 0xE200;
    private static final char JUNTAR = (char) 0xE1F0;
    private static final char ATRAS = (char) 0xE1F1;
    /** Cada cuadro dura 3 ticks y se va en 2: si se atrasa un tick no parpadea. */
    private static final Title.Times TIEMPOS = Title.Times.times(Duration.ZERO, Duration.ofMillis(150), Duration.ofMillis(100));

    private final VoranixExtremoPlugin plugin;
    private final BukkitAudiences audiences;
    private BukkitTask animacion;

    public OjoMuerte(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        this.audiences = BukkitAudiences.create(plugin);
    }

    /** Muestra el Ojo a todos; si ya se estaba mostrando (otra muerte) empieza de nuevo. */
    public void mostrar(String textoBarra) {
        detener();
        float volumen = (float) plugin.getConfig().getDouble("death-effects.ojo.volumen", 1.0);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), "ojo:muerte", SoundCategory.MASTER, volumen, 1f);
        }
        if (textoBarra != null && !textoBarra.isEmpty()) {
            audiences.players().sendActionBar(Component.text(textoBarra));
        }
        int[] cuadro = {0};
        animacion = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (cuadro[0] >= CUADROS) {
                detener();
                return;
            }
            audiences.players().showTitle(Title.title(cuadro(cuadro[0]++), Component.empty(), TIEMPOS));
        }, 0L, 1L);
    }

    private static Component cuadro(int i) {
        int base = PIEZAS + COLUMNAS * FILAS * i;
        StringBuilder texto = new StringBuilder();
        for (int q = 0; q < COLUMNAS * FILAS; q++) {
            if (q > 0) texto.append(q % COLUMNAS == 0 ? ATRAS : JUNTAR);
            texto.append((char) (base + q));
        }
        return Component.text(texto.toString()).font(FUENTE).color(SIN_SOMBRA);
    }

    private void detener() {
        if (animacion != null) {
            animacion.cancel();
            animacion = null;
        }
    }

    public void close() {
        detener();
        audiences.close();
    }
}
