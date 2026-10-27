package com.voranixextremo.plugin.teamsystem;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Guarda los teams (teams-data.yml), las invitaciones pendientes y quién está hablando en el
 * chat del team. La configuración (capacidad, mensajes, formato) está en team.yml.
 * Usa mapas concurrentes porque el chat se procesa fuera del hilo principal.
 */
public class TeamManager {

    private static final Pattern NOMBRE_VALIDO = Pattern.compile("[A-Za-z0-9_]+");

    private final VoranixExtremoPlugin plugin;
    private final File configFile;
    private final File dataFile;
    private YamlConfiguration config;

    private final Map<String, Team> teams = new ConcurrentHashMap<>();
    private final Map<UUID, String> teamDe = new ConcurrentHashMap<>();
    /** invitado → (team → momento en que expira) */
    private final Map<UUID, Map<String, Long>> invitaciones = new ConcurrentHashMap<>();
    private final Set<UUID> enChatDeTeam = ConcurrentHashMap.newKeySet();
    /** Avisa cuando cambia el team de un jugador (para actualizar el texto bajo su nombre). */
    private Consumer<UUID> alCambiar = uuid -> { };

    public TeamManager(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "team.yml");
        this.dataFile = new File(plugin.getDataFolder(), "teams-data.yml");
        reloadConfig();
        loadData();
    }

    public void setAlCambiar(Consumer<UUID> alCambiar) {
        this.alCambiar = alCambiar;
    }

    // ------------------------------------------------------------------ configuración

    /**
     * Lee team.yml (lo crea con los valores por defecto si no existe). Lo que falte en un team.yml
     * viejo (por ejemplo "ayuda", que se agregó después) se toma del team.yml que trae el plugin.
     */
    public void reloadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("team.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
        InputStream original = plugin.getResource("team.yml");
        if (original != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(original, StandardCharsets.UTF_8)));
        }
    }

    /** Las líneas de /team help, con colores y la capacidad ya puesta. */
    public List<String> getAyuda() {
        List<String> lineas = new ArrayList<>();
        for (String linea : config.getStringList("ayuda")) {
            lineas.add(ColorUtils.color(linea.replace("%max%", String.valueOf(getCapacidad()))));
        }
        return lineas;
    }

    public int getCapacidad() {
        return Math.max(1, config.getInt("team.capacidad", 3));
    }

    public int getSegundosInvitacion() {
        return Math.max(5, config.getInt("team.invitacion-segundos", 60));
    }

    public boolean isNametagEnabled() {
        return config.getBoolean("nametag.enabled", true);
    }

    public double getAlturaNametag() {
        return config.getDouble("nametag.altura", 0.6);
    }

    /** Texto bajo el nombre para un team (ya con colores), o null si está desactivado. */
    public String getTextoNametag(Team team) {
        if (!isNametagEnabled()) {
            return null;
        }
        return ColorUtils.color(config.getString("nametag.formato", "&b%team%").replace("%team%", team.getName()));
    }

    public String getFormatoChat() {
        return config.getString("chat.formato", "&8[&bTeam &f%team%&8] &f%player%&7: &f%message%");
    }

    /** Mensaje de team.yml → mensajes, con el prefijo y los colores ya puestos. */
    public String msg(String key, String... reemplazos) {
        String texto = config.getString("mensajes.prefijo", "") + config.getString("mensajes." + key, key);
        for (int i = 0; i + 1 < reemplazos.length; i += 2) {
            texto = texto.replace(reemplazos[i], reemplazos[i + 1]);
        }
        return ColorUtils.color(texto);
    }

    /** null si el nombre es válido; si no, el mensaje de error. */
    public String validarNombre(String nombre) {
        int min = config.getInt("team.nombre-min", 3), max = config.getInt("team.nombre-max", 16);
        if (nombre.length() < min || nombre.length() > max || !NOMBRE_VALIDO.matcher(nombre).matches()) {
            return msg("nombre-invalido", "%min%", String.valueOf(min), "%max%", String.valueOf(max));
        }
        if (teams.containsKey(Team.key(nombre))) {
            return msg("nombre-usado", "%team%", nombre);
        }
        return null;
    }

    // ------------------------------------------------------------------ consultas

    public Team getTeam(UUID uuid) {
        String key = teamDe.get(uuid);
        return key == null ? null : teams.get(key);
    }

    public Team getTeamByName(String nombre) {
        return teams.get(Team.key(nombre));
    }

    public Collection<Team> getTeams() {
        return teams.values();
    }

    public List<String> getNombres() {
        List<String> nombres = new ArrayList<>();
        for (Team team : teams.values()) nombres.add(team.getName());
        return nombres;
    }

    /** Integrantes conectados del team. */
    public List<Player> getConectados(Team team) {
        List<Player> conectados = new ArrayList<>();
        for (UUID uuid : team.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) conectados.add(p);
        }
        return conectados;
    }

    /** Manda un mensaje a todos los integrantes conectados. */
    public void avisar(Team team, String mensaje) {
        for (Player p : getConectados(team)) p.sendMessage(mensaje);
    }

    public static String nombreDe(UUID uuid) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        return p.getName() != null ? p.getName() : uuid.toString().substring(0, 8);
    }

    // ------------------------------------------------------------------ acciones

    public Team crear(String nombre, UUID lider) {
        Team team = new Team(nombre, lider);
        teams.put(team.getKey(), team);
        teamDe.put(lider, team.getKey());
        invitaciones.remove(lider);
        save();
        alCambiar.accept(lider);
        return team;
    }

    public void invitar(UUID invitado, Team team) {
        long expira = System.currentTimeMillis() + getSegundosInvitacion() * 1000L;
        invitaciones.computeIfAbsent(invitado, k -> new ConcurrentHashMap<>()).put(team.getKey(), expira);
    }

    public boolean tieneInvitacion(UUID invitado, Team team) {
        Map<String, Long> suyas = invitaciones.get(invitado);
        if (suyas == null) return false;
        Long expira = suyas.get(team.getKey());
        if (expira == null) return false;
        if (expira < System.currentTimeMillis()) {
            suyas.remove(team.getKey());
            return false;
        }
        return true;
    }

    public void unir(UUID uuid, Team team) {
        team.addMember(uuid);
        teamDe.put(uuid, team.getKey());
        invitaciones.remove(uuid);
        save();
        alCambiar.accept(uuid);
    }

    /**
     * Saca a alguien de su team. Si era el líder y quedan integrantes, el más antiguo pasa a ser
     * líder; si no queda nadie, el team se disuelve. Devuelve el nuevo líder, o null si no cambió.
     */
    public UUID sacar(UUID uuid) {
        Team team = getTeam(uuid);
        if (team == null) return null;
        team.removeMember(uuid);
        teamDe.remove(uuid);
        enChatDeTeam.remove(uuid);
        UUID nuevoLider = null;
        if (team.size() == 0) {
            teams.remove(team.getKey());
            quitarInvitacionesDe(team);
        } else if (team.isLeader(uuid)) {
            nuevoLider = team.getMembers().iterator().next();
            team.setLeader(nuevoLider);
        }
        save();
        alCambiar.accept(uuid);
        return nuevoLider;
    }

    public void disolver(Team team) {
        teams.remove(team.getKey());
        quitarInvitacionesDe(team);
        for (UUID uuid : team.getMembers()) {
            teamDe.remove(uuid);
            enChatDeTeam.remove(uuid);
            alCambiar.accept(uuid);
        }
        save();
    }

    private void quitarInvitacionesDe(Team team) {
        for (Map<String, Long> suyas : invitaciones.values()) suyas.remove(team.getKey());
    }

    // ------------------------------------------------------------------ chat del team

    public boolean isEnChatDeTeam(UUID uuid) {
        return enChatDeTeam.contains(uuid);
    }

    public void setEnChatDeTeam(UUID uuid, boolean activo) {
        if (activo) enChatDeTeam.add(uuid);
        else enChatDeTeam.remove(uuid);
    }

    // ------------------------------------------------------------------ guardado

    private void loadData() {
        teams.clear();
        teamDe.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection seccion = data.getConfigurationSection("teams");
        if (seccion == null) return;
        for (String key : seccion.getKeys(false)) {
            ConfigurationSection s = seccion.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Team team = new Team(s.getString("nombre", key), UUID.fromString(s.getString("lider", "")));
                for (String miembro : s.getStringList("miembros")) team.addMember(UUID.fromString(miembro));
                teams.put(team.getKey(), team);
                for (UUID uuid : team.getMembers()) teamDe.put(uuid, team.getKey());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Team '" + key + "' mal guardado en teams-data.yml: " + e.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Team team : teams.values()) {
            String base = "teams." + team.getKey();
            data.set(base + ".nombre", team.getName());
            data.set(base + ".lider", team.getLeader().toString());
            List<String> miembros = new ArrayList<>();
            for (UUID uuid : team.getMembers()) miembros.add(uuid.toString());
            data.set(base + ".miembros", miembros);
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar teams-data.yml", e);
        }
    }
}
