package com.voranixextremo.plugin.visualfx;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Zonas de humo/niebla (cajas o esferas) donde quien tenga el mod VoranixVisualFX pierde
 * visibilidad de verdad, calculada en vivo del lado del cliente contra la posición de su cámara
 * (no hay escaneo periódico del servidor: ver el README de ese repo para el porqué). Este
 * manager solo es dueño de LA LISTA de zonas y de avisarle al mod cuándo cambia - el cálculo de
 * densidad/color en sí vive enteramente en SmokeZoneRegistry del otro lado.
 *
 * El plugin le manda a cada jugador SOLO las zonas de SU mundo actual (nunca la lista completa
 * de todos los mundos): al conectarse (apenas registra el canal, con un reintento a los 1.5s por
 * las dudas) y cada vez que cambia de mundo, se le manda "borrá todo" + las zonas de donde está
 * parado ahora. Las zonas se guardan en fogzones.yml y sobreviven un reinicio del servidor.
 */
public final class FogZoneManager implements Listener {

    public enum Tipo {CAJA, ESFERA}

    /**
     * Una zona - caja y esfera son la misma clase, con un campo de tipo que dice cómo
     * interpretar p0..p5 (ver SmokeZone.java del otro repo, es el mismo esquema).
     * CAJA: p0,p1,p2 = esquina mínima; p3,p4,p5 = esquina máxima.
     * ESFERA: p0,p1,p2 = centro; p3 = radio (p4,p5 sin usar).
     * Los 4 campos de crecimiento están reservados para la Fase 3 (máquina de humo) - hoy
     * siempre van en false/0.
     */
    public record Zona(String nombre, String mundo, Tipo tipo,
                        double p0, double p1, double p2, double p3, double p4, double p5,
                        int[] color, float cercana, float lejana, float margen,
                        boolean creciendoActivo, long tickInicio, float velocidadBloquesPorSegundo, float radioMaximo) {
    }

    /** Alto mínimo de una zona caja, en bloques, aunque pos1/pos2 se hayan marcado casi a la misma altura. */
    private static final double ALTURA_MINIMA = 6.0;
    /** Margen por defecto si no se especifica uno al crear la zona. 20 bloques daba niebla sin nube visible. */
    private static final float MARGEN_DEFECTO = 4f;
    private static final long PARTICULAS_TICKS = 4L;
    private static final int PARTICULAS_HUMO_POR_ZONA = 10;
    private static final int PARTICULAS_COLOR_POR_ZONA = 4;
    private static final String CANAL = "voranixvisualfx:main";
    /** Respaldo si PlayerRegisterChannelEvent no llega a tiempo (o nunca, si no tiene el mod). */
    private static final long REINTENTO_SYNC_TICKS = 30L;

    private final VoranixExtremoPlugin plugin;
    private final VisualFxBridge bridge;
    private final File archivo;
    private final Map<String, Zona> zonas = new LinkedHashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private BukkitTask tareaParticulas;

    public FogZoneManager(VoranixExtremoPlugin plugin, VisualFxBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.archivo = new File(plugin.getDataFolder(), "fogzones.yml");
        cargar();
    }

    public void start() {
        if (tareaParticulas != null) {
            tareaParticulas.cancel();
        }
        tareaParticulas = Bukkit.getScheduler().runTaskTimer(plugin, this::animarHumo, 0L, PARTICULAS_TICKS);
    }

    public void stop() {
        if (tareaParticulas != null) {
            tareaParticulas.cancel();
            tareaParticulas = null;
        }
    }

    /** /fx niebla pos1 - marca la esquina 1 en la posición actual del jugador. */
    public void marcarPos1(Player jugador) {
        pos1.put(jugador.getUniqueId(), jugador.getLocation());
    }

    /** /fx niebla pos2 - marca la esquina 2 en la posición actual del jugador. */
    public void marcarPos2(Player jugador) {
        pos2.put(jugador.getUniqueId(), jugador.getLocation());
    }

    public Location getPos1(Player jugador) {
        return pos1.get(jugador.getUniqueId());
    }

    public Location getPos2(Player jugador) {
        return pos2.get(jugador.getUniqueId());
    }

    /** Resultado posible de crear/crearEsfera: por qué no se pudo, o null si salió bien. */
    public enum ErrorCrear {FALTA_POS1, FALTA_POS2, MUNDOS_DISTINTOS, YA_EXISTE}

    /** Caja: usa las dos posiciones que el jugador marcó con /fx niebla pos1 y pos2. */
    public ErrorCrear crear(Player jugador, String nombre, int[] color, float cercana, float lejana, Float margen) {
        Location esquina1 = pos1.get(jugador.getUniqueId());
        Location esquina2 = pos2.get(jugador.getUniqueId());
        if (esquina1 == null) {
            return ErrorCrear.FALTA_POS1;
        }
        if (esquina2 == null) {
            return ErrorCrear.FALTA_POS2;
        }
        if (esquina1.getWorld() == null || esquina2.getWorld() == null
                || !esquina1.getWorld().equals(esquina2.getWorld())) {
            return ErrorCrear.MUNDOS_DISTINTOS;
        }
        String clave = nombre.toLowerCase(Locale.ROOT);
        if (zonas.containsKey(clave)) {
            return ErrorCrear.YA_EXISTE;
        }
        double minY = Math.min(esquina1.getY(), esquina2.getY());
        double maxY = Math.max(esquina1.getY(), esquina2.getY());
        // si las dos esquinas se marcaron caminando (alturas parecidas), la caja queda tan fina
        // que un escalón del terreno te deja afuera sin darte cuenta - se garantiza un mínimo.
        if (maxY - minY < ALTURA_MINIMA) {
            double centro = (minY + maxY) / 2;
            minY = centro - ALTURA_MINIMA / 2;
            maxY = centro + ALTURA_MINIMA / 2;
        }
        Zona zona = new Zona(nombre, esquina1.getWorld().getName(), Tipo.CAJA,
                Math.min(esquina1.getX(), esquina2.getX()), minY, Math.min(esquina1.getZ(), esquina2.getZ()),
                Math.max(esquina1.getX(), esquina2.getX()), maxY, Math.max(esquina1.getZ(), esquina2.getZ()),
                color, cercana, lejana, margen != null ? margen : MARGEN_DEFECTO,
                false, 0L, 0f, 0f);
        agregarYAvisar(clave, zona);
        return null;
    }

    /** Esfera: centrada en la posición actual del jugador. */
    public ErrorCrear crearEsfera(Player jugador, String nombre, double radio, int[] color, float cercana, float lejana, Float margen) {
        String clave = nombre.toLowerCase(Locale.ROOT);
        if (zonas.containsKey(clave)) {
            return ErrorCrear.YA_EXISTE;
        }
        Location centro = jugador.getLocation();
        Zona zona = new Zona(nombre, centro.getWorld().getName(), Tipo.ESFERA,
                centro.getX(), centro.getY(), centro.getZ(), radio, 0, 0,
                color, cercana, lejana, margen != null ? margen : MARGEN_DEFECTO,
                false, 0L, 0f, 0f);
        agregarYAvisar(clave, zona);
        return null;
    }

    private void agregarYAvisar(String clave, Zona zona) {
        zonas.put(clave, zona);
        guardar();
        bridge.enviarZonaATodos(zona);
    }

    /** true si se borró; false si no existía. */
    public boolean borrar(String nombre) {
        Zona quitada = zonas.remove(nombre.toLowerCase(Locale.ROOT));
        if (quitada == null) {
            return false;
        }
        guardar();
        bridge.enviarRemocionATodos(quitada.nombre());
        return true;
    }

    public Collection<Zona> listar() {
        return zonas.values();
    }

    public void reload() {
        zonas.clear();
        cargar();
    }

    /** "Borrá todo" + las zonas del mundo donde está parado ESE jugador ahora mismo. */
    private void resincronizar(Player jugador) {
        bridge.enviarLimpiarA(jugador);
        String mundoActual = jugador.getWorld().getName();
        for (Zona zona : zonas.values()) {
            if (zona.mundo().equals(mundoActual)) {
                bridge.enviarZonaA(jugador, zona);
            }
        }
    }

    /** El cliente avisa que registró el canal (con el mod puesto) - ahí ya le puede llegar cualquier paquete. */
    @EventHandler
    public void onRegistrarCanal(PlayerRegisterChannelEvent event) {
        if (event.getChannel().equals(CANAL)) {
            resincronizar(event.getPlayer());
        }
    }

    /** Respaldo por si el evento de arriba no llega a tiempo (o no llega, si no tiene el mod - ahí no pasa nada igual). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player jugador = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (jugador.isOnline()) {
                resincronizar(jugador);
            }
        }, REINTENTO_SYNC_TICKS);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        resincronizar(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pos1.remove(event.getPlayer().getUniqueId());
        pos2.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Partículas de humo/niebla DE VERDAD en el aire, rellenando cada zona - esto es lo que hace
     * que se vea "como una bomba de humo" desde afuera, aunque no se tenga el mod instalado (son
     * partículas vanilla, Bukkit las manda solas a quien esté cerca). Sin relación con la
     * densidad de cada jugador - eso lo calcula el cliente solo. Esto es temporal: la Fase 4
     * reemplaza esto por una partícula custom del mod, más grande y generada del lado cliente.
     */
    private void animarHumo() {
        if (zonas.isEmpty()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Zona zona : zonas.values()) {
            World mundo = Bukkit.getWorld(zona.mundo());
            if (mundo == null) {
                continue;
            }
            Particle.DustOptions tinte = new Particle.DustOptions(
                    Color.fromRGB(zona.color()[0], zona.color()[1], zona.color()[2]), 1.8f);
            for (int i = 0; i < PARTICULAS_HUMO_POR_ZONA; i++) {
                double[] punto = puntoAleatorioEnZona(zona, rnd);
                mundo.spawnParticle(Particle.SMOKE_LARGE, punto[0], punto[1], punto[2], 1, 0, 0, 0, 0.01);
            }
            for (int i = 0; i < PARTICULAS_COLOR_POR_ZONA; i++) {
                double[] punto = puntoAleatorioEnZona(zona, rnd);
                mundo.spawnParticle(Particle.REDSTONE, punto[0], punto[1], punto[2], 1, 0, 0, 0, 0, tinte);
            }
        }
    }

    /** Un punto al azar dentro del volumen de la zona (caja: uniforme; esfera: uniforme en volumen, no solo en la superficie). */
    private static double[] puntoAleatorioEnZona(Zona zona, ThreadLocalRandom rnd) {
        if (zona.tipo() == Tipo.CAJA) {
            double x = zona.p0() + rnd.nextDouble() * (zona.p3() - zona.p0());
            double y = zona.p1() + rnd.nextDouble() * (zona.p4() - zona.p1());
            double z = zona.p2() + rnd.nextDouble() * (zona.p5() - zona.p2());
            return new double[]{x, y, z};
        }
        double radio = zona.p3() * Math.cbrt(rnd.nextDouble());
        double theta = rnd.nextDouble() * Math.PI * 2;
        double phi = Math.acos(2 * rnd.nextDouble() - 1);
        double x = zona.p0() + radio * Math.sin(phi) * Math.cos(theta);
        double y = zona.p1() + radio * Math.sin(phi) * Math.sin(theta);
        double z = zona.p2() + radio * Math.cos(phi);
        return new double[]{x, y, z};
    }

    private void cargar() {
        if (!archivo.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(archivo);
        ConfigurationSection seccion = yaml.getConfigurationSection("zonas");
        if (seccion == null) {
            return;
        }
        for (String clave : seccion.getKeys(false)) {
            ConfigurationSection z = seccion.getConfigurationSection(clave);
            if (z == null) {
                continue;
            }
            int[] color = {z.getInt("color.r", 200), z.getInt("color.g", 200), z.getInt("color.b", 200)};
            Tipo tipo = "ESFERA".equalsIgnoreCase(z.getString("tipo", "CAJA")) ? Tipo.ESFERA : Tipo.CAJA;
            zonas.put(clave, new Zona(z.getString("nombre", clave), z.getString("mundo", "world"), tipo,
                    z.getDouble("p0"), z.getDouble("p1"), z.getDouble("p2"),
                    z.getDouble("p3"), z.getDouble("p4"), z.getDouble("p5"),
                    color, (float) z.getDouble("cercana", 2), (float) z.getDouble("lejana", 18),
                    (float) z.getDouble("margen", MARGEN_DEFECTO),
                    z.getBoolean("creciendoActivo", false), z.getLong("tickInicio", 0),
                    (float) z.getDouble("velocidad", 0), (float) z.getDouble("radioMaximo", 0)));
        }
    }

    private void guardar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Zona zona : zonas.values()) {
            String base = "zonas." + zona.nombre().toLowerCase(Locale.ROOT) + ".";
            yaml.set(base + "nombre", zona.nombre());
            yaml.set(base + "mundo", zona.mundo());
            yaml.set(base + "tipo", zona.tipo().name());
            yaml.set(base + "p0", zona.p0());
            yaml.set(base + "p1", zona.p1());
            yaml.set(base + "p2", zona.p2());
            yaml.set(base + "p3", zona.p3());
            yaml.set(base + "p4", zona.p4());
            yaml.set(base + "p5", zona.p5());
            yaml.set(base + "color.r", zona.color()[0]);
            yaml.set(base + "color.g", zona.color()[1]);
            yaml.set(base + "color.b", zona.color()[2]);
            yaml.set(base + "cercana", zona.cercana());
            yaml.set(base + "lejana", zona.lejana());
            yaml.set(base + "margen", zona.margen());
            yaml.set(base + "creciendoActivo", zona.creciendoActivo());
            yaml.set(base + "tickInicio", zona.tickInicio());
            yaml.set(base + "velocidad", zona.velocidadBloquesPorSegundo());
            yaml.set(base + "radioMaximo", zona.radioMaximo());
        }
        try {
            yaml.save(archivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar fogzones.yml", e);
        }
    }
}
