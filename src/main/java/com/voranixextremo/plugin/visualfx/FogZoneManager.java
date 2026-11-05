package com.voranixextremo.plugin.visualfx;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Zonas del mapa (cajas, como la selección de WorldEdit: dos esquinas) donde hay niebla de
 * verdad (ver FogZonePacket). Cada 10 ticks (medio segundo) chequea en qué zona está cada
 * jugador conectado y le manda el paquete correspondiente SOLO cuando cambia de zona (entra,
 * sale o pasa de una zona a otra) - no reenvía nada mientras se queda quieto adentro de la
 * misma. Las zonas se guardan en fogzones.yml y sobreviven un reinicio del servidor.
 */
public final class FogZoneManager implements Listener {

    /** Una caja (como la selección de WorldEdit): todo lo que cae entre las dos esquinas, en ese mundo. */
    public record Zona(String nombre, String mundo, double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ,
                        int[] color, float cercana, float lejana, double fundidoSegundos) {

        boolean contiene(Location loc) {
            if (!mundo.equals(loc.getWorld().getName())) {
                return false;
            }
            double x = loc.getX(), y = loc.getY(), z = loc.getZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    /** Alto mínimo de una zona, en bloques, aunque pos1/pos2 se hayan marcado casi a la misma altura. */
    private static final double ALTURA_MINIMA = 6.0;

    private final VoranixExtremoPlugin plugin;
    private final VisualFxBridge bridge;
    private final File archivo;
    private final Map<String, Zona> zonas = new LinkedHashMap<>();
    private final Map<UUID, String> zonaActual = new HashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private BukkitTask tarea;

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
    }

    public void stop() {
        if (tarea != null) {
            tarea.cancel();
            tarea = null;
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
            Zona dentro = zonaDe(jugador.getLocation());
            String anterior = zonaActual.get(jugador.getUniqueId());
            String actual = dentro != null ? dentro.nombre() : null;
            if (Objects.equals(anterior, actual)) {
                continue;
            }
            if (dentro != null) {
                bridge.nieblaZona(jugador, dentro.color(), dentro.cercana(), dentro.lejana(), dentro.fundidoSegundos());
                zonaActual.put(jugador.getUniqueId(), actual);
            } else {
                Zona previa = anterior != null ? zonas.get(anterior.toLowerCase(Locale.ROOT)) : null;
                bridge.quitarNieblaZona(jugador, previa != null ? previa.fundidoSegundos() : 1.0);
                zonaActual.remove(jugador.getUniqueId());
            }
        }
    }

    private Zona zonaDe(Location loc) {
        for (Zona zona : zonas.values()) {
            if (zona.contiene(loc)) {
                return zona;
            }
        }
        return null;
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
