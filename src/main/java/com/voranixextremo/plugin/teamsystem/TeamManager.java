package com.voranixextremo.plugin.teamsystem;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private static final Pattern TAG_VALIDO = Pattern.compile("[A-Za-z0-9]+");

    /** Un color que se puede elegir en el menú del team (team.yml → colores). */
    public record ColorOpcion(String id, String nombre, String codigo, Material material) { }

    private final VoranixExtremoPlugin plugin;
    private final File configFile;
    private final File dataFile;
    private YamlConfiguration config;

    private final Map<String, Team> teams = new ConcurrentHashMap<>();
    private final Map<UUID, String> teamDe = new ConcurrentHashMap<>();
    /** invitado → (team → momento en que expira) */
    private final Map<UUID, Map<String, Long>> invitaciones = new ConcurrentHashMap<>();
    private final Set<UUID> enChatDeTeam = ConcurrentHashMap.newKeySet();
    /** Skin (URL de textures.minecraft.net) de cada jugador con team, para su cabeza en el menú aunque no esté conectado. */
    private final Map<UUID, String> skins = new ConcurrentHashMap<>();
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
            actualizarConfigVieja();
        }
    }

    /**
     * Un team.yml de antes de los tags y colores: si todavía tiene los textos que traía el plugin, se
     * cambian por los nuevos (con %tag% y %color%) para que el color del team se vea. Lo que el
     * usuario haya cambiado no se toca.
     */
    private void actualizarConfigVieja() {
        Map<String, Object> viejos = new LinkedHashMap<>();
        viejos.put("nametag.lineas", List.of("&f%player%", "&b%team%"));
        viejos.put("nametag.lineas#2", List.of("&f%player%", "%color%%team% &8[%color%%tag%&8]"));
        viejos.put("chat.formato", "&8[&bTeam &f%team%&8] &f%player%&7: &f%message%");
        viejos.put("mensajes.creado", "&aCreaste el team &f%team%&a.");
        viejos.put("mensajes.info-titulo", "&bTeam &f%team% &8(%cantidad%/%max%)");
        viejos.put("mensajes.lista-linea", "&8- &f%team% &7(%cantidad%/%max%)");
        boolean cambio = false;
        for (Map.Entry<String, Object> e : viejos.entrySet()) {
            String key = e.getKey().replaceAll("#\\d+$", ""); // "#2": otro texto viejo de la misma clave
            Object actual = e.getValue() instanceof List ? config.getStringList(key) : config.getString(key);
            if (e.getValue().equals(actual) && config.isSet(key)) {
                config.set(key, config.getDefaults().get(key));
                cambio = true;
            }
        }
        if (config.getStringList("ayuda").contains(" &b/team create &f<nombre> &8- &7Crea tu propio team")) {
            config.set("ayuda", config.getDefaults().getStringList("ayuda"));
            cambio = true;
        }
        if (!cambio) return;
        try {
            config.save(configFile);
            plugin.getLogger().info("team.yml actualizado con los tags y colores de los teams.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo actualizar team.yml", e);
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

    /**
     * Si el plugin dibuja su propio texto sobre la cabeza (nametag.modo: texto). En el modo vanilla
     * (el de por defecto) se usa el nombre normal con el prefijo del tab ([VRX] Jugador): ver TabOrden.
     */
    public boolean isNametagEnabled() {
        String modo = config.getString("nametag.modo"); // sin valor por defecto: así usa el del team.yml del plugin
        return "texto".equalsIgnoreCase(modo) && config.getBoolean("nametag.enabled", true);
    }

    public double getAlturaNametag() {
        // el texto queda montado a 1,35 bloques (3/4 del alto del jugador) y el nombre ocupa de 2,07 a 2,3:
        // con 0,45 el texto (0,25 de alto) va de 1,80 a 2,05, justo debajo del nombre
        return config.getDouble("nametag.altura", 0.45);
    }

    /**
     * Separación entre líneas del texto sobre el jugador (cada línea es un texto con su propio fondo).
     * Es justo el alto del fondo de una línea (11 px de texto x 0,025), así los fondos quedan
     * pegados, sin hueco y sin montarse, y cada uno conserva su ancho.
     */
    public double getEspacioLineas() {
        return ALTO_LINEA;
    }

    private static final double ALTO_LINEA = 0.275;

    /** Texto bajo el nombre para un team (ya con colores), o null si está desactivado. */
    public String getTextoNametag(Player player, Team team) {
        if (!isNametagEnabled()) {
            return null;
        }
        // el nombre normal se esconde (ver TabOrden), así que el texto trae el nombre del jugador
        List<String> lineas = config.getStringList("nametag.lineas");
        if (lineas.isEmpty()) lineas = List.of("&8[%color%%tag%&8] &f%player%");
        List<String> coloreadas = new ArrayList<>();
        for (String linea : lineas) {
            coloreadas.add(ColorUtils.color(aplicar(linea.replace("%player%", player.getName()), team)));
        }
        return String.join("\n", coloreadas);
    }

    /** Formato del chat del team, con %team%, %tag% y %color% ya puestos (sin colores aún). */
    public String getFormatoChat(Team team) {
        return aplicar(config.getString("chat.formato", "&8[%color%%tag%&8] &f%player%&7: &f%message%"), team);
    }

    /** Reemplaza %team%, %tag%, %color% y %nombre-color% por los del team. */
    public String aplicar(String texto, Team team) {
        return texto.replace("%team%", team.getName()).replace("%tag%", team.getTag())
                .replace("%nombre-color%", nombreDeColor(team.getColor())).replace("%color%", team.getColor());
    }

    // ------------------------------------------------------------------ tab (team.yml → tab)

    public boolean isTabEnabled() {
        return config.getBoolean("tab.enabled", true);
    }

    public boolean isTabLiderPrimero() {
        return config.getBoolean("tab.lider-primero", true);
    }

    public boolean isTabSinTeamAlFinal() {
        return config.getBoolean("tab.sin-team-al-final", true);
    }

    public boolean isTabColorNombre() {
        return config.getBoolean("tab.color-nombre", true);
    }

    /** Texto de team.yml → tab (prefijo, sufijo), sin colores aún. */
    public String getTab(String key) {
        String texto = config.getString("tab." + key); // sin valor por defecto: así usa el del team.yml del plugin
        return texto == null ? "" : texto;
    }

    /**
     * Los teams en el orden en que salen en el tab (team.yml → tab.orden):
     *   nombre      por nombre, de la A a la Z
     *   miembros    los que tienen más integrantes primero
     *   conectados  los que tienen más integrantes conectados primero
     * En un empate, por nombre.
     */
    public List<Team> getTeamsOrdenados() {
        List<Team> lista = new ArrayList<>(teams.values());
        Comparator<Team> porNombre = Comparator.comparing(Team::getKey);
        String orden = config.getString("tab.orden", "nombre").toLowerCase();
        Comparator<Team> comparador = switch (orden) {
            case "miembros" -> Comparator.comparingInt(Team::size).reversed().thenComparing(porNombre);
            case "conectados" -> Comparator.<Team>comparingInt(t -> getConectados(t).size()).reversed().thenComparing(porNombre);
            default -> porNombre;
        };
        lista.sort(comparador);
        return lista;
    }

    /** Posición del team en el orden del tab (1 = primero), o 0 si no está. */
    public int getPosicion(Team team) {
        List<Team> lista = getTeamsOrdenados();
        for (int i = 0; i < lista.size(); i++) if (lista.get(i).getKey().equals(team.getKey())) return i + 1;
        return 0;
    }

    /** Texto de team.yml → placeholder (sufijo, prefijo, sin-team), sin colores aún. */
    public String getPlaceholder(String key) {
        String texto = config.getString("placeholder." + key); // sin valor por defecto: así usa el del team.yml del plugin
        return texto == null ? "" : texto;
    }

    /** Texto de team.yml → gui (sin colores aún). */
    public String gui(String path) {
        String texto = config.getString("gui." + path); // sin valor por defecto: así usa el del team.yml del plugin
        return texto == null ? "" : texto;
    }

    public List<String> guiLista(String path) {
        return config.getStringList("gui." + path);
    }

    // ------------------------------------------------------------------ colores

    /** Los colores que se pueden elegir (team.yml → colores), en orden. */
    public List<ColorOpcion> getColores() {
        ConfigurationSection seccion = config.getConfigurationSection("colores");
        if ((seccion == null || seccion.getKeys(false).isEmpty()) && config.getDefaults() != null) {
            seccion = config.getDefaults().getConfigurationSection("colores");
        }
        if (seccion == null) return List.of();
        List<ColorOpcion> opciones = new ArrayList<>();
        for (String id : seccion.getKeys(false)) {
            ConfigurationSection s = seccion.getConfigurationSection(id);
            if (s == null) continue;
            Material material = Material.matchMaterial(s.getString("material", "WHITE_WOOL"));
            opciones.add(new ColorOpcion(id.toLowerCase(), s.getString("nombre", id), s.getString("codigo", "&f"),
                    material == null || !material.isItem() ? Material.WHITE_WOOL : material));
        }
        return opciones;
    }

    /** El color por su id o su nombre (sin importar mayúsculas), o null. */
    public ColorOpcion getColor(String id) {
        for (ColorOpcion opcion : getColores()) {
            if (opcion.id().equalsIgnoreCase(id) || opcion.nombre().equalsIgnoreCase(id)) return opcion;
        }
        return null;
    }

    /** Nombre del color (por ejemplo "Rojo") según su código; si no está en la lista, el código. */
    public String nombreDeColor(String codigo) {
        for (ColorOpcion opcion : getColores()) if (opcion.codigo().equalsIgnoreCase(codigo)) return opcion.nombre();
        return codigo.replace("&", "");
    }

    public String getColorDefecto() {
        return config.getString("team.color-defecto", "&b");
    }

    /** Cambia el color del team, lo guarda y actualiza el texto sobre cada integrante. */
    public void cambiarColor(Team team, ColorOpcion color) {
        team.setColor(color.codigo());
        save();
        for (UUID uuid : team.getMembers()) alCambiar.accept(uuid);
    }

    /** Mensaje de team.yml → mensajes, con el prefijo y los colores ya puestos. */
    public String msg(String key, String... reemplazos) {
        return ColorUtils.color(crudo(key, reemplazos));
    }

    /** Igual que msg, con %team%, %tag% y %color% del team (antes de poner los colores). */
    public String msg(Team team, String key, String... reemplazos) {
        return ColorUtils.color(aplicar(crudo(key, reemplazos), team));
    }

    private String crudo(String key, String... reemplazos) {
        String mensaje = config.getString("mensajes." + key); // sin valor por defecto: así usa el del team.yml del plugin
        String texto = config.getString("mensajes.prefijo", "") + (mensaje == null ? key : mensaje);
        for (int i = 0; i + 1 < reemplazos.length; i += 2) {
            texto = texto.replace(reemplazos[i], reemplazos[i + 1]);
        }
        return texto;
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

    /** null si el tag es válido; si no, el mensaje de error. */
    public String validarTag(String tag) {
        int min = config.getInt("team.tag-min", 2), max = config.getInt("team.tag-max", 4);
        if (tag.length() < min || tag.length() > max || !TAG_VALIDO.matcher(tag).matches()) {
            return msg("tag-invalido", "%min%", String.valueOf(min), "%max%", String.valueOf(max));
        }
        for (Team team : teams.values()) {
            if (team.getTag().equalsIgnoreCase(tag)) return msg("tag-usado", "%tag%", tag, "%team%", team.getName());
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

    public Team crear(String nombre, String tag, UUID lider) {
        Team team = new Team(nombre, tag, getColorDefecto(), lider);
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

    // ------------------------------------------------------------------ skins (cabezas del menú)

    public String getSkin(UUID uuid) {
        return skins.get(uuid);
    }

    /** Guarda la skin de un jugador (solo si tiene team y cambió). */
    public void setSkin(UUID uuid, String url) {
        if (url == null || getTeam(uuid) == null || url.equals(skins.get(uuid))) return;
        skins.put(uuid, url);
        save();
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
        skins.clear();
        ConfigurationSection guardadas = data.getConfigurationSection("skins");
        if (guardadas != null) {
            for (String uuid : guardadas.getKeys(false)) {
                try {
                    skins.put(UUID.fromString(uuid), guardadas.getString(uuid));
                } catch (IllegalArgumentException ignored) {
                    // uuid mal escrito: se ignora
                }
            }
        }
        ConfigurationSection seccion = data.getConfigurationSection("teams");
        if (seccion == null) return;
        for (String key : seccion.getKeys(false)) {
            ConfigurationSection s = seccion.getConfigurationSection(key);
            if (s == null) continue;
            try {
                String nombre = s.getString("nombre", key);
                // los teams de antes no tenían tag: se usan las primeras letras del nombre
                String letras = nombre.replace("_", "");
                String tag = s.getString("tag", letras.substring(0, Math.min(3, letras.length())).toUpperCase());
                Team team = new Team(nombre, tag.isEmpty() ? nombre : tag, s.getString("color", getColorDefecto()),
                        UUID.fromString(s.getString("lider", "")));
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
            data.set(base + ".tag", team.getTag());
            data.set(base + ".color", team.getColor());
            data.set(base + ".lider", team.getLeader().toString());
            List<String> miembros = new ArrayList<>();
            for (UUID uuid : team.getMembers()) miembros.add(uuid.toString());
            data.set(base + ".miembros", miembros);
        }
        for (Map.Entry<UUID, String> skin : skins.entrySet()) {
            if (teamDe.containsKey(skin.getKey())) data.set("skins." + skin.getKey(), skin.getValue());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar teams-data.yml", e);
        }
    }
}
