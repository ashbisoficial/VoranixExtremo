package com.voranixextremo.plugin.ruleta;

import com.google.gson.Gson;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ruleta de colores en pantalla (traída del plugin Ruleta_Custom, con las animaciones de
 * "Ruleta del fénix"): aparece con un rebote, gira (con desenfoque cuando va rápido), se tiñe
 * del color ganador, tiembla y desaparece girando. La ruleta son varias letras encimadas del
 * resource pack resourcepack/Ruleta: disco + aro + fuego (animado) + tinte + flecha (se inclina).
 * Se configura en config.yml → ruleta-colores.
 */
public final class RuletaManager {

    private static final Key FONT = Key.key("ruleta", "ruleta");
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Title.Times SEGUIR = Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO);
    /** Duración de cada fase del final, en ticks (como en el HTML: 0,8 s, 1,1 s y 0,75 s). */
    private static final int TENIR = 16, TEMBLOR = 22, SALIDA = 15;
    /** Lo que dura la entrada (1,2 s), en ticks. */
    private static final int ENTRADA = 24;
    /**
     * A partir de esta velocidad (fotogramas del disco por tick) se usa el disco desenfocado: entre
     * 1 y 2 fotogramas por tick el disco nítido avanza a saltos disparejos y se nota "lagueado".
     */
    private static final double VELOCIDAD_BORROSA = 1.4;

    /** Lo que trae el resource pack; lo escribe tools/ruleta/GenerarRuleta.java. */
    private record Layout(int disco, int discoFrames, int borroso, int borrosoFrames, int fijo,
                          int fuego, int fuegoFrames, int fuegoTicks, int flecha, int flechaFrames,
                          int tinte, int tinteNiveles, int entrada, int entradaFrames, int salida, int salidaFrames,
                          int mover, int juntar, int atras, List<String> segmentos) {}

    private record RuletaColor(String id, String nombre, String mensaje, List<String> comandos) {}

    private final VoranixExtremoPlugin plugin;
    private final Layout layout;
    private final List<RuletaColor> colores = new ArrayList<>();
    /** Color de cada segmento, en el orden en que pasan bajo la flecha. */
    private final List<Integer> segmentos = new ArrayList<>();

    private int duracion, vueltas, resultadoTicks;
    private Sound sonidoTick, sonidoFinal;

    private int frameActual = 0;
    private BukkitTask task;

    public RuletaManager(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        try (var in = new InputStreamReader(plugin.getResource("ruleta-layout.json"), StandardCharsets.UTF_8)) {
            this.layout = new Gson().fromJson(in, Layout.class);
        } catch (Exception e) {
            throw new IllegalStateException("El jar no trae ruleta-layout.json", e);
        }
        load();
    }

    /** Sección ruleta-colores del config.yml (vacía si alguien la borró). */
    ConfigurationSection config() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("ruleta-colores");
        return c != null ? c : new MemoryConfiguration();
    }

    public void load() {
        ConfigurationSection c = config();
        duracion = Math.max(40, c.getInt("duracion-ticks", 140));
        vueltas = Math.max(1, c.getInt("vueltas", 4));
        resultadoTicks = (int) (c.getDouble("resultado-segundos", 0.5) * 20);
        sonidoTick = sound(c.getString("sonidos.tick", "minecraft:block.note_block.hat"), 0.6f, 1.6f);
        sonidoFinal = sound(c.getString("sonidos.final", "minecraft:entity.player.levelup"), 1f, 1f);

        colores.clear();
        ConfigurationSection sec = c.getConfigurationSection("colores");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                colores.add(new RuletaColor(id.toLowerCase(), sec.getString(id + ".nombre", id),
                        sec.getString(id + ".mensaje", null), sec.getStringList(id + ".comandos")));
            }
        }
        segmentos.clear();
        List<String> ids = colores.stream().map(RuletaColor::id).toList();
        for (String s : layout.segmentos()) {
            int i = ids.indexOf(s);
            if (i < 0) {
                // el pack trae un color que la config no tiene: se agrega con su nombre tal cual
                colores.add(new RuletaColor(s, s.toUpperCase(), null, List.of()));
                i = colores.size() - 1;
            }
            segmentos.add(i);
        }
        frameActual %= layout.discoFrames();
    }

    /** Colores que de verdad están en la ruleta (los que se pueden pedir). */
    public List<String> coloresEnRuleta() {
        return layout.segmentos();
    }

    public boolean isSpinning() {
        return task != null;
    }

    /** Empieza a girar. Devuelve un mensaje de error o null si arrancó. */
    public Component spin(String id, String mensaje) {
        if (isSpinning()) return msg("ya-girando");

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int segmento = id.equals("random") ? rnd.nextInt(segmentos.size()) : layout.segmentos().indexOf(id);
        if (segmento < 0) return msg("color-invalido").replaceText(r("{color}", Component.text(id)));
        RuletaColor color = colores.get(segmentos.get(segmento));

        int frames = layout.discoFrames();
        int porColor = frames / segmentos.size();
        // termina justo en el centro del color: el tinte y la salida del pack están hechos con el disco ahí
        int destino = segmento * porColor;
        int desde = frameActual;
        double[] pos = recorrido(desde, Math.floorMod(destino - desde, frames) + frames * vueltas, porColor);
        int entrada = ENTRADA;

        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int tick = 0;
            int ultimoCruce = cruce(desde, porColor);
            int flecha = 0;

            @Override
            public void run() {
                if (tick < entrada) {
                    // aparece chica, se pasa un poco de tamaño y se endereza
                    show(new Capas().una(layout.entrada() + tick * layout.entradaFrames() / ENTRADA), 0);
                } else if (tick - entrada < pos.length) {
                    int s = tick - entrada;
                    double p = pos[s];
                    int frame = Math.floorMod((int) Math.round(p), frames);
                    frameActual = frame;

                    // cada vez que una línea pasa por la flecha: "tic" y la gema se inclina
                    int c = cruce(p, porColor);
                    if (c != ultimoCruce) {
                        ultimoCruce = c;
                        flecha = layout.flechaFrames() - 1;
                        if (sonidoTick != null) Bukkit.getOnlinePlayers().forEach(pl -> pl.playSound(sonidoTick, Sound.Emitter.self()));
                    } else if (flecha > 0) flecha--;

                    // rápido se usa el disco desenfocado (va a media resolución, pero el desenfoque lo disimula)
                    double velocidad = s > 0 ? Math.abs(p - pos[s - 1]) : 0;
                    Capas capas = new Capas();
                    if (velocidad >= VELOCIDAD_BORROSA) capas.una(layout.borroso()
                            + Math.floorMod((int) Math.round(p * layout.borrosoFrames() / frames), layout.borrosoFrames()));
                    else capas.cuatro(layout.disco() + 4 * frame);
                    capas.cuatro(layout.fijo()).cuatro(fuego()).cuatro(layout.flecha() + 4 * flecha);
                    show(capas, 0);
                    if (s == pos.length - 1) anunciar(color, mensaje);
                } else {
                    int r = tick - entrada - pos.length;
                    int temblor = TENIR + resultadoTicks, salida = temblor + TEMBLOR, fin = salida + SALIDA;
                    if (r >= fin) {
                        stop(true);
                        return;
                    }
                    if (r >= salida) {
                        // se achica girando y se desvanece
                        int f = Math.min(layout.salidaFrames() - 1, (r - salida) * layout.salidaFrames() / SALIDA);
                        show(new Capas().una(layout.salida() + segmento * layout.salidaFrames() + f), 0);
                    } else {
                        // se tiñe del color ganador; después tiembla latiendo, cada vez más fuerte
                        int nivel = Math.min(layout.tinteNiveles() - 2, r * (layout.tinteNiveles() - 1) / TENIR);
                        int mover = 0;
                        if (r >= temblor) {
                            int e = r - temblor;
                            if (Math.sin(e * 1.7) > 0) nivel = layout.tinteNiveles() - 1;
                            int amp = e < TEMBLOR / 2 ? 1 : 2;
                            mover = ThreadLocalRandom.current().nextInt(-amp, amp + 1);
                        }
                        Capas capas = new Capas().cuatro(layout.disco() + 4 * frameActual).cuatro(layout.fijo()).cuatro(fuego())
                                .cuatro(layout.tinte() + 4 * (segmento * layout.tinteNiveles() + nivel)).cuatro(layout.flecha());
                        show(capas, mover);
                    }
                }
                tick++;
            }
        }, 0L, 1L);
        return null;
    }

    /** Fotograma actual de las llamas (es un loop que corre solo, gire o no la ruleta). */
    private int fuego() {
        int f = (int) ((Bukkit.getCurrentTick() / layout.fuegoTicks()) % layout.fuegoFrames());
        return layout.fuego() + 4 * f;
    }

    /**
     * Arma las capas encimadas del title. Las de 4 piezas (2x2) van arriba-izq, arriba-der y luego
     * vuelven atrás para la fila de abajo; las de una pieza (media resolución) ocupan lo mismo.
     * Cada capa nueva empieza volviendo al inicio.
     */
    private final class Capas {
        private final StringBuilder sb = new StringBuilder();

        Capas cuatro(int c) {
            if (!sb.isEmpty()) sb.appendCodePoint(layout.atras());
            sb.appendCodePoint(c).appendCodePoint(layout.juntar()).appendCodePoint(c + 1).appendCodePoint(layout.atras())
                    .appendCodePoint(c + 2).appendCodePoint(layout.juntar()).appendCodePoint(c + 3);
            return this;
        }

        Capas una(int c) {
            if (!sb.isEmpty()) sb.appendCodePoint(layout.atras());
            sb.appendCodePoint(c);
            return this;
        }

        /** Texto final, corrido `mover` (-2..2) hacia la derecha sin cambiar el ancho total (para el temblor). */
        String texto(int mover) {
            if (mover == 0) return sb.toString();
            return new StringBuilder().appendCodePoint(layout.mover() + 2 + mover).append(sb)
                    .appendCodePoint(layout.mover() + 2 - mover).toString();
        }
    }

    /**
     * Posición del disco (en fotogramas, sin envolver) para cada tick del giro:
     * se echa un poco hacia atrás, acelera, frena largo, se pasa un poquito y vuelve al centro del color.
     */
    private double[] recorrido(double desde, double total, int porColor) {
        int atras = 8, asentar = 14, giro = Math.max(10, duracion - atras - asentar);
        double retroceso = porColor * 0.6, pasada = porColor * 0.35;
        double[] curva = curvaGiro(giro);
        double[] pos = new double[atras + giro + asentar + 1];
        int i = 0;
        for (int t = 0; t < atras; t++, i++) pos[i] = desde - retroceso * easeInOutSine((t + 1.0) / atras);
        for (int t = 1; t <= giro; t++, i++) pos[i] = desde - retroceso + (total + retroceso + pasada) * curva[t];
        for (int t = 1; t <= asentar; t++, i++) pos[i] = desde + total + pasada - pasada * easeInOutCubic((double) t / asentar);
        pos[i] = desde + total;
        return pos;
    }

    /** Avance acumulado 0..1 del giro principal: arranca suave, llega a toda velocidad y frena despacio. */
    private static double[] curvaGiro(int ticks) {
        int n = 2000;
        double[] acum = new double[n + 1];
        for (int k = 1; k <= n; k++) {
            double u = (k - 0.5) / n;
            double arranque = u < 0.1 ? smooth(u / 0.1) : 1;
            acum[k] = acum[k - 1] + arranque * Math.pow(1 - u, 2.2);
        }
        double[] out = new double[ticks + 1];
        for (int t = 0; t <= ticks; t++) out[t] = acum[(int) Math.round((double) t / ticks * n)] / acum[n];
        return out;
    }

    /** Número de la línea divisoria que está más cerca de haber pasado por la flecha. */
    private static int cruce(double pos, int porColor) {
        return (int) Math.floor((pos + porColor / 2.0) / porColor);
    }

    private void anunciar(RuletaColor color, String mensaje) {
        Component nombre = parse(color.nombre());
        if (sonidoFinal != null) Bukkit.getOnlinePlayers().forEach(p -> p.playSound(sonidoFinal, Sound.Emitter.self()));
        String texto = mensaje != null ? mensaje
                : color.mensaje() != null ? color.mensaje()
                : config().getString("mensajes.broadcast", "{color}");
        Component broadcast = parse(texto).replaceText(r("{color}", nombre));
        Component prefix = parse(config().getString("mensajes.prefijo-broadcast", ""));
        Bukkit.broadcast(prefix.append(broadcast));
        ejecutarComandos(color);
    }

    /** Corre, por consola (permisos completos), los comandos configurados para el color que salió. */
    private void ejecutarComandos(RuletaColor color) {
        for (String comando : color.comandos()) {
            if (comando == null || comando.isBlank()) continue;
            String cmd = comando.startsWith("/") ? comando.substring(1) : comando;
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (Exception e) {
                plugin.getLogger().warning("Ruleta color '" + color.id() + "': falló el comando '" + cmd + "': " + e.getMessage());
            }
        }
    }

    /** Detiene el giro. Devuelve true si estaba girando. */
    public boolean stop(boolean clearTitle) {
        if (task == null) return false;
        task.cancel();
        task = null;
        if (clearTitle) Bukkit.getOnlinePlayers().forEach(Player::clearTitle);
        return true;
    }

    /** Muestra la ruleta en el title (sin sombra). */
    private void show(Capas capas, int mover) {
        Component wheel = Component.text(capas.texto(mover)).font(FONT).shadowColor(ShadowColor.none());
        Title title = Title.title(wheel, Component.empty(), SEGUIR);
        for (Player p : Bukkit.getOnlinePlayers()) p.showTitle(title);
    }

    public Component msg(String key) {
        String prefix = config().getString("mensajes.prefijo", "");
        return parse(prefix + config().getString("mensajes." + key, key));
    }

    /** Acepta MiniMessage (<red>) o códigos clásicos (&c). */
    static Component parse(String s) {
        if (s == null || s.isEmpty()) return Component.empty();
        if (s.indexOf('&') >= 0 && s.indexOf('<') < 0) return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
        return MM.deserialize(s);
    }

    private static TextReplacementConfig r(String literal, Component with) {
        return TextReplacementConfig.builder().matchLiteral(literal).replacement(with).build();
    }

    private static double smooth(double x) { return x * x * (3 - 2 * x); }
    private static double easeInOutSine(double x) { return 0.5 - 0.5 * Math.cos(Math.PI * x); }
    private static double easeInOutCubic(double x) { return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2; }

    private Sound sound(String key, float volume, float pitch) {
        if (key == null || key.isBlank() || key.equalsIgnoreCase("none")) return null;
        try {
            return Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch);
        } catch (Exception e) {
            plugin.getLogger().warning("Sonido inválido: " + key);
            return null;
        }
    }
}
