package com.voranixextremo.plugin.teamsystem;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Muestra el nombre del team debajo del nombre de cada integrante.
 *
 * Es un TextDisplay que existe solo en el cliente de cada jugador (paquetes con PacketEvents),
 * montado sobre el integrante. No es una entidad real: si lo fuera, Spigot 1.20.1 no dejaría
 * teletransportar al jugador (con pasajeros, Player#teleport no hace nada).
 *
 * Cuando el servidor le manda a alguien el "aparecer" de un jugador con team, se le agrega el
 * texto; cuando le manda el "borrar", se borra también el texto. El dueño no ve el suyo.
 */
public class TeamNametag implements Listener, PacketListener {

    private static final Vector3d ORIGEN = new Vector3d(0, 0, 0);

    private final VoranixExtremoPlugin plugin;
    private final TeamManager teams;
    /** En 1.20.2 se agregó un campo antes de los del texto: desde ahí los índices corren uno. */
    private final int desfase;

    private final AtomicInteger siguienteId = new AtomicInteger(1_900_000_000);
    /** id de la entidad falsa de cada jugador */
    private final Map<UUID, Integer> idFalso = new ConcurrentHashMap<>();
    /** entity id real → jugador */
    private final Map<Integer, UUID> jugadorPorId = new ConcurrentHashMap<>();
    /** texto que hay que mostrar bajo cada jugador (si no está, no se muestra nada) */
    private final Map<UUID, String> textos = new ConcurrentHashMap<>();
    /** qué jugadores (entity id) tiene cargados cada jugador en su cliente */
    private final Map<UUID, Set<Integer>> cargados = new ConcurrentHashMap<>();
    private BukkitTask tarea;

    public TeamNametag(VoranixExtremoPlugin plugin, TeamManager teams) {
        this.plugin = plugin;
        this.teams = teams;
        boolean nuevo = PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_20_2);
        this.desfase = nuevo ? 1 : 0;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
        for (Player p : Bukkit.getOnlinePlayers()) jugadorPorId.put(p.getEntityId(), p.getUniqueId());
        // revisa cada medio segundo: invisibilidad, espectador, muerte, cambios de team
        tarea = Bukkit.getScheduler().runTaskTimer(plugin, this::actualizarTodos, 10L, 10L);
    }

    public void stop() {
        if (tarea != null) tarea.cancel();
        for (UUID dueño : new ArrayList<>(textos.keySet())) ocultarAnteTodos(dueño);
        textos.clear();
    }

    /** Vuelve a armar todos los textos (por ejemplo después de /voranixextremo reload). */
    public void refrescarTodo() {
        for (UUID dueño : new ArrayList<>(textos.keySet())) ocultarAnteTodos(dueño);
        textos.clear();
        actualizarTodos();
    }

    private void actualizarTodos() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            jugadorPorId.put(p.getEntityId(), p.getUniqueId());
            actualizar(p.getUniqueId());
        }
    }

    /** Recalcula el texto de un jugador y, si cambió, lo actualiza en todos los que lo ven. */
    public void actualizar(UUID uuid) {
        Player dueño = Bukkit.getPlayer(uuid);
        String nuevo = dueño == null ? null : textoPara(dueño);
        String viejo = nuevo == null ? textos.remove(uuid) : textos.put(uuid, nuevo);
        if (dueño == null || (nuevo == null ? viejo == null : nuevo.equals(viejo))) return;
        int idReal = dueño.getEntityId();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(dueño) || !cargadosDe(viewer).contains(idReal)) continue;
            if (viejo != null) enviar(viewer, new WrapperPlayServerDestroyEntities(idFalsoDe(uuid)));
            if (nuevo != null) mostrar(viewer, dueño, idReal, nuevo);
        }
    }

    private String textoPara(Player p) {
        Team team = teams.getTeam(p.getUniqueId());
        if (team == null || p.isDead() || p.getGameMode() == GameMode.SPECTATOR
                || p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return null;
        }
        return teams.getTextoNametag(team);
    }

    // ------------------------------------------------------------------ paquetes

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)) return;
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            alCargar(viewer, new WrapperPlayServerSpawnPlayer(event).getEntityId(), event);
        } else if (desfase == 1 && event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            // desde 1.20.2 los jugadores aparecen con SPAWN_ENTITY; en 1.20.1 ni se lee (usan SPAWN_PLAYER)
            WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(event);
            if (jugadorPorId.containsKey(spawn.getEntityId())) alCargar(viewer, spawn.getEntityId(), event);
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            alBorrar(viewer, event);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            alCambiarPasajeros(viewer, event);
        }
    }

    /** El servidor le muestra un jugador a viewer: se le agrega el texto justo después. */
    private void alCargar(Player viewer, int idReal, PacketSendEvent event) {
        UUID dueño = jugadorPorId.get(idReal);
        if (dueño == null || dueño.equals(viewer.getUniqueId())) return;
        cargadosDe(viewer).add(idReal);
        String texto = textos.get(dueño);
        Player jugador = Bukkit.getPlayer(dueño);
        if (texto != null && jugador != null) {
            event.getTasksAfterSend().add(() -> mostrar(viewer, jugador, idReal, texto));
        }
    }

    /** El servidor le borra jugadores a viewer: se borran también sus textos. */
    private void alBorrar(Player viewer, PacketSendEvent event) {
        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(event);
        Set<Integer> suyos = cargadosDe(viewer);
        List<Integer> falsos = new ArrayList<>();
        for (int id : destroy.getEntityIds()) {
            if (!suyos.remove(id)) continue;
            UUID dueño = jugadorPorId.get(id);
            if (dueño != null && textos.containsKey(dueño)) falsos.add(idFalsoDe(dueño));
        }
        if (falsos.isEmpty()) return;
        int[] ids = Arrays.copyOf(destroy.getEntityIds(), destroy.getEntityIds().length + falsos.size());
        for (int i = 0; i < falsos.size(); i++) ids[destroy.getEntityIds().length + i] = falsos.get(i);
        destroy.setEntityIds(ids);
        event.markForReEncode(true);
    }

    /** Si el servidor cambia los pasajeros de un jugador con team, se le vuelve a sumar el texto. */
    private void alCambiarPasajeros(Player viewer, PacketSendEvent event) {
        WrapperPlayServerSetPassengers pasajeros = new WrapperPlayServerSetPassengers(event);
        UUID dueño = jugadorPorId.get(pasajeros.getEntityId());
        if (dueño == null || !textos.containsKey(dueño) || !cargadosDe(viewer).contains(pasajeros.getEntityId())) return;
        int falso = idFalsoDe(dueño);
        int[] actuales = pasajeros.getPassengers();
        for (int id : actuales) if (id == falso) return;
        int[] nuevos = Arrays.copyOf(actuales, actuales.length + 1);
        nuevos[actuales.length] = falso;
        pasajeros.setPassengers(nuevos);
        event.markForReEncode(true);
    }

    /** Crea el texto en el cliente de viewer y lo monta sobre el jugador. */
    private void mostrar(Player viewer, Player dueño, int idReal, String texto) {
        int falso = idFalsoDe(dueño.getUniqueId());
        Location loc = dueño.getLocation();
        enviar(viewer, new WrapperPlayServerSpawnEntity(falso, Optional.of(UUID.randomUUID()), EntityTypes.TEXT_DISPLAY,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()), 0f, 0f, 0f, 0, Optional.of(ORIGEN)));
        List<EntityData<?>> datos = new ArrayList<>();
        datos.add(new EntityData<>(10 + desfase, EntityDataTypes.VECTOR3F, new Vector3f(0f, (float) teams.getAlturaNametag(), 0f)));
        datos.add(new EntityData<>(14 + desfase, EntityDataTypes.BYTE, (byte) 3)); // siempre de frente (billboard CENTER)
        datos.add(new EntityData<>(22 + desfase, EntityDataTypes.ADV_COMPONENT,
                LegacyComponentSerializer.legacySection().deserialize(texto)));
        datos.add(new EntityData<>(24 + desfase, EntityDataTypes.INT, 0x40000000)); // fondo como el del nametag
        enviar(viewer, new WrapperPlayServerEntityMetadata(falso, datos));
        enviar(viewer, new WrapperPlayServerSetPassengers(idReal, new int[]{falso}));
    }

    private void ocultarAnteTodos(UUID dueño) {
        Player jugador = Bukkit.getPlayer(dueño);
        if (jugador == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (cargadosDe(viewer).contains(jugador.getEntityId())) {
                enviar(viewer, new WrapperPlayServerDestroyEntities(idFalsoDe(dueño)));
            }
        }
    }

    private void enviar(Player viewer, com.github.retrooper.packetevents.wrapper.PacketWrapper<?> paquete) {
        if (viewer.isOnline()) PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, paquete);
    }

    private int idFalsoDe(UUID uuid) {
        return idFalso.computeIfAbsent(uuid, k -> siguienteId.getAndIncrement());
    }

    private Set<Integer> cargadosDe(Player viewer) {
        return cargados.computeIfAbsent(viewer.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
    }

    // ------------------------------------------------------------------ entrar / salir

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        jugadorPorId.put(p.getEntityId(), p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> actualizar(p.getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        jugadorPorId.remove(p.getEntityId());
        cargados.remove(p.getUniqueId());
        textos.remove(p.getUniqueId());
    }
}
