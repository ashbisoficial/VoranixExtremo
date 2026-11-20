package com.voranixextremo.plugin.ruleta;

import com.voranixextremo.plugin.util.Messages;
import com.google.gson.Gson;
import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ruleta de colores en pantalla (traída del plugin Ruleta_Custom, con las animaciones de
 * "Ruleta del fénix"): aparece agrandándose mientras ya empieza a girar, gira (con desenfoque cuando
 * va rápido), se tiñe del color ganador, tiembla y se va achicándose y girando. No manda mensajes:
 * al caer suena y ejecuta los comandos del color.
 *
 * La ruleta son varias letras encimadas del resource pack resourcepack/Ruleta (disco, aro, fuego,
 * tinte, flecha), cada capa de un "color" que no es un color: le dice al shader del pack
 * (rendertype_text.vsh) cuánto agrandarla y girarla (ver {@link #ajuste}). Por eso el disco gira
 * suave y la entrada y la salida son la misma ruleta en vivo, sin cuadros pregrabados.
 * Se configura en config.yml → ruleta-colores.
 */
public final class RuletaManager {

    private static final Key FONT = Key.key("ruleta", "ruleta");
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Title.Times SEGUIR = Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO);
    /** Duración de cada fase del final, en ticks (como en el HTML: 0,8 s, 1,1 s y 0,75 s). */
    private static final int TENIR = 16, TEMBLOR = 22, SALIDA = 15;
    /** Lo que tarda en aparecer, en ticks; el giro arranca antes de que termine (en GIRO_INICIO). */
    private static final int ENTRADA = 20, GIRO_INICIO = 8;

    /** Lo que trae el resource pack; lo escribe tools/ruleta/GenerarRuleta.java. */
    private record Layout(int posiciones, int disco, int fijo, int borroso, List<Double> borrosoGrados,
                          int fuego, int fuegoFrames, int fuegoTicks, int flecha, int flechaFrames, List<Boolean> flechaPiezas,
                          int tinte, int tinteNiveles, int mover, int juntar, int atras, int saltar2, int saltar4,
                          List<String> segmentos) {}

    private record RuletaColor(String id, String nombre, List<String> comandos) {}

    private final VoranixExtremoPlugin plugin;
    /** Spigot no trae Adventure: esto manda titles, sonidos y mensajes de Adventure a los jugadores. */
    private final BukkitAudiences audiences;
    private final Layout layout;
    private final List<RuletaColor> colores = new ArrayList<>();
    /** Color de cada segmento, en el orden en que pasan bajo la flecha. */
    private final List<Integer> segmentos = new ArrayList<>();

    private int duracion, vueltas, resultadoTicks;
    private Sound sonidoTick, sonidoFinal;

    /** Dónde quedó el disco (en posiciones: layout.posiciones() = una vuelta). */
    private double posicionActual = 0;
    private BukkitTask task;

    public RuletaManager(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        this.audiences = BukkitAudiences.create(plugin);
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
                // comandos solo si están en el config.yml del servidor: si no, Bukkit los sacaría del
                // config.yml que trae el jar y un color sin comandos igual lanzaría efectos
                List<String> comandos = sec.contains(id + ".comandos", true) ? sec.getStringList(id + ".comandos") : List.of();
                colores.add(new RuletaColor(id.toLowerCase(), sec.getString(id + ".nombre", id), comandos));
            }
        }
        segmentos.clear();
        List<String> ids = colores.stream().map(RuletaColor::id).toList();
        for (String s : layout.segmentos()) {
            int i = ids.indexOf(s);
            if (i < 0) {
                // el pack trae un color que la config no tiene: se agrega con su nombre tal cual
                colores.add(new RuletaColor(s, s.toUpperCase(), List.of()));
                i = colores.size() - 1;
            }
            segmentos.add(i);
        }
    }

    /** Colores que de verdad están en la ruleta (los que se pueden pedir). */
    public List<String> coloresEnRuleta() {
        return layout.segmentos();
    }

    public boolean isSpinning() {
        return task != null;
    }

    /** Empieza a girar. Devuelve un mensaje de error o null si arrancó. */
    public Component spin(String id) {
        if (isSpinning()) return msg("ya-girando");

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int segmento = id.equals("random") ? rnd.nextInt(segmentos.size()) : layout.segmentos().indexOf(id);
        if (segmento < 0) return msg("color-invalido").replaceText(r("{color}", Component.text(id)));
        RuletaColor color = colores.get(segmentos.get(segmento));

        int vuelta = layout.posiciones();
        int porColor = vuelta / segmentos.size();
        // termina justo en el centro del color: el tinte del pack está hecho con el disco ahí
        int destino = segmento * porColor;
        double desde = Math.floorMod(Math.round(posicionActual), vuelta);
        double[] pos = recorrido(desde, Math.floorMod((int) (destino - desde), vuelta) + (double) vuelta * vueltas, porColor);
        int finGiro = GIRO_INICIO + pos.length - 1;

        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int tick = 0;
            int ultimoCruce = cruce(desde, porColor);
            int flecha = 0;

            @Override
            public void run() {
                if (tick <= finGiro) {
                    girar();
                } else {
                    int r = tick - finGiro - 1;
                    int temblor = TENIR + resultadoTicks, salida = temblor + TEMBLOR, fin = salida + SALIDA;
                    if (r >= fin) {
                        stop(true);
                        return;
                    }
                    // se tiñe del color ganador; después tiembla latiendo, cada vez más fuerte, y se va
                    int nivel = Math.min(layout.tinteNiveles() - 2, r * (layout.tinteNiveles() - 1) / TENIR);
                    int mover = 0;
                    double escala = 1, giro = 0;
                    if (r >= salida) {
                        // se achica girando (antes se agranda un poquito, como tomando impulso)
                        double p = (r - salida + 1.0) / SALIDA;
                        escala = Math.max(0, 1 - easeInBack(p));
                        giro = 120 * p * p;
                        nivel = layout.tinteNiveles() - 1;
                    } else if (r >= temblor) {
                        int e = r - temblor;
                        if (Math.sin(e * 1.7) > 0) nivel = layout.tinteNiveles() - 1;
                        int amp = e < TEMBLOR / 2 ? 1 : 2;
                        mover = ThreadLocalRandom.current().nextInt(-amp, amp + 1);
                    }
                    show(new Capas(escala, giro)
                            .capa(layout.disco(), 4, posicionActual)
                            .capa(layout.fijo(), 4, 0)
                            .capa(fuego(), 2, 0)
                            .capa(layout.tinte() + 4 * (segmento * layout.tinteNiveles() + nivel), 2, 0)
                            .flecha(0), mover);
                }
                tick++;
            }

            /** Entrada y giro, que se pisan: la ruleta todavía está apareciendo cuando el disco arranca. */
            private void girar() {
                double escala = 1, giro = 0;
                if (tick < ENTRADA) {
                    // aparece desde un punto, se pasa un poco de tamaño y se endereza
                    double p = (tick + 1.0) / ENTRADA;
                    escala = easeOutBack(p);
                    giro = -25 * (1 - inOut(p));
                }
                int s = tick - GIRO_INICIO;
                double p = s < 0 ? desde : pos[s];
                posicionActual = p;

                // cada vez que una línea pasa por la flecha: "tic" y la gema se inclina
                int c = cruce(p, porColor);
                if (c != ultimoCruce) {
                    ultimoCruce = c;
                    flecha = layout.flechaFrames() - 1;
                    if (sonidoTick != null) audiences.players().playSound(sonidoTick);
                } else if (flecha > 0) flecha--;

                // rápido se usa el disco desenfocado: el desenfoque crece con la velocidad
                double grados = s > 0 ? Math.abs(p - pos[s - 1]) * 360.0 / vuelta : 0;
                int borroso = -1;
                for (int i = 0; i < layout.borrosoGrados().size(); i++) {
                    if (grados >= 1.6 * layout.borrosoGrados().get(i)) borroso = i;
                }
                Capas capas = new Capas(escala, giro);
                if (borroso >= 0) capas.capa(layout.borroso() + 4 * borroso, 2, p);
                else capas.capa(layout.disco(), 4, p);
                capas.capa(layout.fijo(), 4, 0).capa(fuego(), 2, 0).flecha(flecha);
                show(capas, 0);
                if (s == pos.length - 1) caer(color);
            }
        }, 0L, 1L);
        return null;
    }

    /** Fotograma actual de las llamas (es un loop que corre solo, gire o no la ruleta). */
    private int fuego() {
        // Spigot no tiene getCurrentTick: se cuenta en ticks de 50 ms desde el reloj
        long tick = System.currentTimeMillis() / 50;
        int f = (int) ((tick / layout.fuegoTicks()) % layout.fuegoFrames());
        return layout.fuego() + 4 * f;
    }

    /**
     * El color de texto de una capa, que el shader lee como ajuste: el giro en 1024 pasos por vuelta
     * (los 8 bits altos en el rojo y los 2 bajos en el verde, que va de 93 a 96: así el shader sabe que
     * es la ruleta) y el tamaño en el azul (200 = normal).
     */
    private static TextColor ajuste(double grados, double escala) {
        int paso = Math.floorMod((int) Math.round(grados * 1024 / 360.0), 1024);
        int azul = (int) Math.max(0, Math.min(255, Math.round(escala * 200)));
        return TextColor.color(paso >> 2, 93 + (paso & 3), azul);
    }

    /**
     * Arma las capas encimadas del title. Cada capa es una imagen cortada en piezas (4 x 4 las de
     * 1024 px, 2 x 2 las de 512): van fila por fila, pegadas con "juntar", y "atrás" vuelve al comienzo
     * de la fila siguiente (o de la capa siguiente). Todas miden lo mismo en pantalla.
     */
    private final class Capas {
        private final TextComponent.Builder title = Component.text().font(FONT);
        private final double escala, giro;
        private boolean vacia = true;

        Capas(double escala, double giro) {
            this.escala = escala;
            this.giro = giro;
        }

        /** Una capa, girada `posicion` (en posiciones del disco) además del giro de toda la ruleta. */
        Capas capa(int c, int piezas, double posicion) {
            return capa(c, piezas, posicion, null);
        }

        private Capas capa(int c, int piezas, double posicion, List<Boolean> usadas) {
            StringBuilder sb = new StringBuilder();
            if (!vacia) sb.appendCodePoint(layout.atras());
            for (int f = 0; f < piezas; f++) {
                if (f > 0) sb.appendCodePoint(layout.atras());
                for (int k = 0; k < piezas; k++) {
                    if (k > 0) sb.appendCodePoint(layout.juntar());
                    int q = f * piezas + k;
                    if (usadas != null && !usadas.get(q)) sb.appendCodePoint(piezas == 4 ? layout.saltar4() : layout.saltar2());
                    else sb.appendCodePoint(c + q);
                }
            }
            vacia = false;
            double grados = giro + posicion * 360.0 / layout.posiciones();
            title.append(Component.text(sb.toString()).color(ajuste(grados, escala)));
            return this;
        }

        /** La gema de arriba, inclinada `frame` (0 = derecha). Solo trae las piezas donde hay algo. */
        Capas flecha(int frame) {
            return capa(layout.flecha() + 16 * frame, 4, 0, layout.flechaPiezas());
        }

        /** El title, corrido `mover` (-2..2) hacia la derecha sin cambiar el ancho total (para el temblor). */
        Component armar(int mover) {
            if (mover == 0) return title.build();
            return Component.text().font(FONT)
                    .append(Component.text(new String(Character.toChars(layout.mover() + 2 + mover))))
                    .append(title.build())
                    .append(Component.text(new String(Character.toChars(layout.mover() + 2 - mover))))
                    .build();
        }
    }

    /**
     * Posición del disco (en posiciones, sin envolver) para cada tick del giro:
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

    /** Cuando el disco se detiene: suena y se ejecutan los comandos del color (sin mensajes en el chat). */
    private void caer(RuletaColor color) {
        if (sonidoFinal != null) audiences.players().playSound(sonidoFinal);
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
        if (clearTitle) audiences.players().clearTitle();
        return true;
    }

    /** Para al apagar el plugin: detiene el giro y suelta lo de Adventure. */
    public void close() {
        stop(false);
        audiences.close();
    }

    /** Manda un mensaje de Adventure (MiniMessage, colores) a un jugador o a la consola. */
    public void enviar(CommandSender sender, Component mensaje) {
        audiences.sender(sender).sendMessage(mensaje);
    }

    /** Muestra la ruleta en el title (el shader le quita la sombra, ver {@link #ajuste}). */
    private void show(Capas capas, int mover) {
        audiences.players().showTitle(Title.title(capas.armar(mover), Component.empty(), SEGUIR));
    }

    public Component msg(String key) {
        return parse(Messages.texto("ruleta.prefijo", "") + Messages.texto("ruleta." + key, key));
    }

    /** Lee los §x§r§r§g§g§b§b que deja ColorUtils para los colores hex. */
    private static final LegacyComponentSerializer LEGACY_HEX = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR).hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    /** Acepta MiniMessage (<red>) o códigos clásicos con & y hex (&c, &#ff8a1c, {#ff8a1c}) vía ColorUtils. */
    static Component parse(String s) {
        if (s == null || s.isEmpty()) return Component.empty();
        if ((s.indexOf('&') >= 0 || s.contains("{#")) && s.indexOf('<') < 0) return LEGACY_HEX.deserialize(ColorUtils.color(s));
        return MM.deserialize(s);
    }

    private static TextReplacementConfig r(String literal, Component with) {
        return TextReplacementConfig.builder().matchLiteral(literal).replacement(with).build();
    }

    private static double smooth(double x) { return x * x * (3 - 2 * x); }
    private static double inOut(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }
    private static double easeInOutSine(double x) { return 0.5 - 0.5 * Math.cos(Math.PI * x); }
    private static double easeInOutCubic(double x) { return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2; }
    /** 0 → 1 pasándose hasta ~1,1 y volviendo (el "rebote" al aparecer). */
    private static double easeOutBack(double x) { double c1 = 1.7, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); }
    /** 0 → 1 empezando un poco para atrás (al irse se agranda apenas antes de achicarse). */
    private static double easeInBack(double x) { double c1 = 1.4, c3 = c1 + 1; return c3 * x * x * x - c1 * x * x; }

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
