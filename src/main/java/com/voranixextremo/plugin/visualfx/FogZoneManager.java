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
import org.bukkit.event.player.PlayerQuitEvent;
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
 * Zonas del mapa (cajas, como la selección de WorldEdit: dos esquinas) donde hay niebla de
 * verdad (ver FogZonePacket). No es un muro de "adentro sí, afuera no": cada 10 ticks (medio
 * segundo) se calcula qué tan cerca está cada jugador conectado de la zona más próxima (1.0
 * adentro, bajando a 0.0 a los MARGEN_APROXIMACION bloques del borde) y se le manda la niebla
 * mezclada en esa proporción con "casi nada" (gris clarito, poca densidad) - así se va notando
 * de a poco según uno se acerca, como una bruma real, no de golpe. Las zonas se guardan en
 * fogzones.yml y sobreviven un reinicio del servidor.
 */
public final class FogZoneManager implements Listener {

    /** Una caja (como la selección de WorldEdit): todo lo que cae entre las dos esquinas, en ese mundo. */
    public record Zona(String nombre, String mundo, double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ,
                        int[] color, float cercana, float lejana, double fundidoSegundos) {

        /**
         * 1.0 adentro de la caja, bajando a 0.0 en el borde del margen de aproximación (fuera de
         * la caja), 0.0 más allá. Así la niebla se va notando de a poco a medida que uno se
         * acerca, en vez de aparecer de golpe al cruzar un borde exacto.
         */
        double proximidad(Location loc, double margen) {
            if (!mundo.equals(loc.getWorld().getName())) {
                return 0;
            }
            double dx = Math.max(Math.max(minX - loc.getX(), loc.getX() - maxX), 0);
            double dy = Math.max(Math.max(minY - loc.getY(), loc.getY() - maxY), 0);
            double dz = Math.max(Math.max(minZ - loc.getZ(), loc.getZ() - maxZ), 0);
            double distancia = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distancia <= 0) {
                return 1.0;
            }
            if (distancia >= margen) {
                return 0.0;
            }
            return 1.0 - distancia / margen;
        }
    }

    /** Alto mínimo de una zona, en bloques, aunque pos1/pos2 se hayan marcado casi a la misma altura. */
    private static final double ALTURA_MINIMA = 6.0;
    /** Cuántos bloques antes del borde de la zona ya se empieza a notar la niebla, cada vez más fuerte. */
    private static final double MARGEN_APROXIMACION = 20.0;
    /** A qué "no hay niebla" tira el color/distancia en el borde del margen (lejos = casi nada, blanquecino como bruma real). */
    private static final float DISTANCIA_SIN_EFECTO = 400f;
    private static final int[] COLOR_SIN_EFECTO = {230, 230, 230};
    /** Cuánto tarda en suavizarse entre dos actualizaciones (se manda una cada ACTUALIZAR_TICKS). */
    private static final double SUAVIZADO_SEGUNDOS = 0.6;
    /** Partículas de humo "de verdad" (se ven desde afuera, con o sin el mod - son 100% vanilla). */
    private static final long PARTICULAS_TICKS = 4L;
    private static final int PARTICULAS_HUMO_POR_ZONA = 10;
    private static final int PARTICULAS_COLOR_POR_ZONA = 4;

    private final VoranixExtremoPlugin plugin;
    private final VisualFxBridge bridge;
    private final File archivo;
    private final Map<String, Zona> zonas = new LinkedHashMap<>();
    private final Map<UUID, String> zonaActual = new HashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private BukkitTask tarea;
    private BukkitTask tareaParticulas;

    public FogZoneManager(VoranixExtremoPlugin plugin, VisualFxBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.archivo = new File(plugin.getDataFolder(), "fogzones.yml");
        cargar();
    }

    public void start() {
        if (tarea != null) {
            tarea.cancel();
        }
        tarea = Bukkit.getScheduler().runTaskTimer(plugin, this::actualizar, 0L, 10L);
        if (tareaParticulas != null) {
            tareaParticulas.cancel();
        }
        tareaParticulas = Bukkit.getScheduler().runTaskTimer(plugin, this::animarHumo, 0L, PARTICULAS_TICKS);
    }

    public void stop() {
        if (tarea != null) {
            tarea.cancel();
            tarea = null;
        }
        if (tareaParticulas != null) {
            tareaParticulas.cancel();
            tareaParticulas = null;
        }
        // al apagar el plugin (o recargar), a quien tuviera niebla puesta se la sacamos
        for (Map.Entry<UUID, String> entrada : zonaActual.entrySet()) {
            Player jugador = Bukkit.getPlayer(entrada.getKey());
            if (jugador != null) {
                bridge.quitarNieblaZona(jugador, 1.0);
            }
        }
        zonaActual.clear();
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

    /**
     * Resultado posible de crear(): por qué no se pudo, o null si salió bien.
     */
    public enum ErrorCrear {FALTA_POS1, FALTA_POS2, MUNDOS_DISTINTOS, YA_EXISTE}

    /** Usa las dos posiciones que el jugador marcó con /fx niebla pos1 y pos2. */
    public ErrorCrear crear(Player jugador, String nombre, int[] color, float cercana, float lejana, double fundidoSegundos) {
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
        zonas.put(clave, new Zona(nombre, esquina1.getWorld().getName(),
                Math.min(esquina1.getX(), esquina2.getX()), minY, Math.min(esquina1.getZ(), esquina2.getZ()),
                Math.max(esquina1.getX(), esquina2.getX()), maxY, Math.max(esquina1.getZ(), esquina2.getZ()),
                color, cercana, lejana, fundidoSegundos));
        guardar();
        return null;
    }

    /** true si se borró; false si no existía. */
    public boolean borrar(String nombre) {
        Zona quitada = zonas.remove(nombre.toLowerCase(Locale.ROOT));
        if (quitada == null) {
            return false;
        }
        guardar();
        for (Map.Entry<UUID, String> entrada : Map.copyOf(zonaActual).entrySet()) {
            if (entrada.getValue().equalsIgnoreCase(nombre)) {
                Player jugador = Bukkit.getPlayer(entrada.getKey());
                if (jugador != null) {
                    bridge.quitarNieblaZona(jugador, 1.0);
                }
                zonaActual.remove(entrada.getKey());
            }
        }
        return true;
    }

    public Collection<Zona> listar() {
        return zonas.values();
    }

    public void reload() {
        zonas.clear();
        cargar();
    }

    private void actualizar() {
        if (zonas.isEmpty()) {
            return;
        }
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            Location loc = jugador.getLocation();
            Zona masCercana = null;
            double mejorProximidad = 0;
            for (Zona zona : zonas.values()) {
                double p = zona.proximidad(loc, MARGEN_APROXIMACION);
                if (p > mejorProximidad) {
                    mejorProximidad = p;
                    masCercana = zona;
                }
            }
            boolean teniaAlgo = zonaActual.containsKey(jugador.getUniqueId());
            if (masCercana == null) {
                if (teniaAlgo) {
                    bridge.quitarNieblaZona(jugador, SUAVIZADO_SEGUNDOS);
                    zonaActual.remove(jugador.getUniqueId());
                }
                continue;
            }
            int[] color = mezclarColor(COLOR_SIN_EFECTO, masCercana.color(), mejorProximidad);
            float cercana = mezclar(DISTANCIA_SIN_EFECTO, masCercana.cercana(), mejorProximidad);
            float lejana = mezclar(DISTANCIA_SIN_EFECTO, masCercana.lejana(), mejorProximidad);
            bridge.nieblaZona(jugador, color, cercana, lejana, SUAVIZADO_SEGUNDOS);
            zonaActual.put(jugador.getUniqueId(), masCercana.nombre());
        }
    }

    /**
     * Partículas de humo/niebla DE VERDAD en el aire, rellenando cada zona - esto es lo que hace
     * que se vea "como una bomba de humo" desde afuera, aunque no se tenga el mod instalado (son
     * partículas vanilla, Bukkit las manda solas a quien esté cerca). No tiene nada que ver con
     * la densidad de niebla de cada jugador (actualizar()) - esto se ve siempre que la zona
     * exista, la reducción de visibilidad es aparte y solo para quien tenga el mod.
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
                double x = zona.minX() + rnd.nextDouble() * (zona.maxX() - zona.minX());
                double y = zona.minY() + rnd.nextDouble() * (zona.maxY() - zona.minY());
                double z = zona.minZ() + rnd.nextDouble() * (zona.maxZ() - zona.minZ());
                mundo.spawnParticle(Particle.SMOKE_LARGE, x, y, z, 1, 0, 0, 0, 0.01);
            }
            for (int i = 0; i < PARTICULAS_COLOR_POR_ZONA; i++) {
                double x = zona.minX() + rnd.nextDouble() * (zona.maxX() - zona.minX());
                double y = zona.minY() + rnd.nextDouble() * (zona.maxY() - zona.minY());
                double z = zona.minZ() + rnd.nextDouble() * (zona.maxZ() - zona.minZ());
                mundo.spawnParticle(Particle.REDSTONE, x, y, z, 1, 0, 0, 0, 0, tinte);
            }
        }
    }

    private static int[] mezclarColor(int[] desde, int[] hasta, double t) {
        return new int[]{
                (int) Math.round(desde[0] + (hasta[0] - desde[0]) * t),
                (int) Math.round(desde[1] + (hasta[1] - desde[1]) * t),
                (int) Math.round(desde[2] + (hasta[2] - desde[2]) * t)
        };
    }

    private static float mezclar(float desde, float hasta, double t) {
        return (float) (desde + (hasta - desde) * t);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        zonaActual.remove(event.getPlayer().getUniqueId());
        pos1.remove(event.getPlayer().getUniqueId());
        pos2.remove(event.getPlayer().getUniqueId());
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
            zonas.put(clave, new Zona(z.getString("nombre", clave), z.getString("mundo", "world"),
                    z.getDouble("minX"), z.getDouble("minY"), z.getDouble("minZ"),
                    z.getDouble("maxX"), z.getDouble("maxY"), z.getDouble("maxZ"),
                    color, (float) z.getDouble("cercana", 2), (float) z.getDouble("lejana", 18),
                    z.getDouble("fundido", 2)));
        }
    }

    private void guardar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Zona zona : zonas.values()) {
            String base = "zonas." + zona.nombre().toLowerCase(Locale.ROOT) + ".";
            yaml.set(base + "nombre", zona.nombre());
            yaml.set(base + "mundo", zona.mundo());
            yaml.set(base + "minX", zona.minX());
            yaml.set(base + "minY", zona.minY());
            yaml.set(base + "minZ", zona.minZ());
            yaml.set(base + "maxX", zona.maxX());
            yaml.set(base + "maxY", zona.maxY());
            yaml.set(base + "maxZ", zona.maxZ());
            yaml.set(base + "color.r", zona.color()[0]);
            yaml.set(base + "color.g", zona.color()[1]);
            yaml.set(base + "color.b", zona.color()[2]);
            yaml.set(base + "cercana", zona.cercana());
            yaml.set(base + "lejana", zona.lejana());
            yaml.set(base + "fundido", zona.fundidoSegundos());
        }
        try {
            yaml.save(archivo);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar fogzones.yml", e);
        }
    }
}
