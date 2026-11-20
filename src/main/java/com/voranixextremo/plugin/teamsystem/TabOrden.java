package com.voranixextremo.plugin.teamsystem;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ordena el tab (la lista de jugadores) por team: los integrantes de cada team salen juntos, el
 * líder primero, y los que no tienen team al final. También puede ponerles el tag como prefijo y
 * pintar su nombre con el color del team. Se configura en team.yml → tab.
 *
 * Minecraft ordena el tab por el nombre del team del scoreboard de cada jugador (y luego por nombre),
 * así que cada jugador va a un team del scoreboard principal llamado según la posición de su team:
 * vx001a (líder del primer team), vx001b (el resto), vx002a... y vxzzzz (sin team). Estos teams solo
 * sirven para el orden: el texto sobre la cabeza lo sigue dibujando TeamNametag.
 */
public class TabOrden implements Listener {

    private static final String PREFIJO = "vx";
    private static final String SIN_TEAM = PREFIJO + "zzzz";

    private final VoranixExtremoPlugin plugin;
    private final TeamManager teams;

    public TabOrden(VoranixExtremoPlugin plugin, TeamManager teams) {
        this.plugin = plugin;
        this.teams = teams;
    }

    /** Vuelve a acomodar a todos los jugadores conectados (cuando cambia un team, alguien entra o sale, o se recarga). */
    public void actualizarTodo() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        if (!teams.isTabEnabled()) {
            limpiar();
            return;
        }
        Map<String, Integer> posiciones = new HashMap<>();
        List<Team> ordenados = teams.getTeamsOrdenados();
        for (int i = 0; i < ordenados.size(); i++) posiciones.put(ordenados.get(i).getKey(), i + 1);

        Set<String> usados = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Team team = teams.getTeam(p.getUniqueId());
            String nombre;
            if (team == null) {
                if (!teams.isTabSinTeamAlFinal()) {
                    quitar(sb, p.getName());
                    continue;
                }
                nombre = SIN_TEAM;
            } else {
                int pos = posiciones.getOrDefault(team.getKey(), 999);
                boolean lider = teams.isTabLiderPrimero() && team.isLeader(p.getUniqueId());
                nombre = PREFIJO + String.format("%03d", Math.min(pos, 999)) + (lider ? "a" : "b");
            }
            org.bukkit.scoreboard.Team sbTeam = sb.getTeam(nombre);
            if (sbTeam == null) sbTeam = sb.registerNewTeam(nombre);
            configurar(sbTeam, team);
            if (!sbTeam.hasEntry(p.getName())) sbTeam.addEntry(p.getName());
            usados.add(nombre);
        }
        // los teams del orden que ya nadie usa se borran
        for (org.bukkit.scoreboard.Team t : sb.getTeams()) {
            if (esNuestro(t.getName()) && !usados.contains(t.getName())) t.unregister();
        }
    }

    /** Prefijo, sufijo y color del nombre en el tab (de team.yml → tab). */
    private void configurar(org.bukkit.scoreboard.Team sbTeam, Team team) {
        String prefijo = team == null ? "" : ColorUtils.color(teams.aplicar(teams.getTab("prefijo"), team));
        String sufijo = team == null ? "" : ColorUtils.color(teams.aplicar(teams.getTab("sufijo"), team));
        if (!prefijo.equals(sbTeam.getPrefix())) sbTeam.setPrefix(prefijo);
        if (!sufijo.equals(sbTeam.getSuffix())) sbTeam.setSuffix(sufijo);
        ChatColor color = ChatColor.RESET;
        String codigo = team == null ? "" : team.getColor();
        if (team != null && teams.isTabColorNombre() && codigo.length() == 2 && codigo.charAt(0) == '&') {
            ChatColor c = ChatColor.getByChar(codigo.charAt(1));
            if (c != null && c.isColor()) color = c;
        }
        if (sbTeam.getColor() != color) sbTeam.setColor(color);
        // el nombre normal sobre la cabeza se esconde cuando el plugin dibuja el suyo ([VRX] Jugador);
        // si no, se verían los dos encimados
        org.bukkit.scoreboard.Team.OptionStatus nombre = team != null && teams.isNametagEnabled()
                ? org.bukkit.scoreboard.Team.OptionStatus.NEVER : org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
        if (sbTeam.getOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY) != nombre) {
            sbTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, nombre);
        }
    }

    private static void quitar(Scoreboard sb, String entrada) {
        org.bukkit.scoreboard.Team t = sb.getEntryTeam(entrada);
        if (t != null && esNuestro(t.getName())) t.removeEntry(entrada);
    }

    private static boolean esNuestro(String nombre) {
        return nombre.equals(SIN_TEAM) || nombre.matches(PREFIJO + "\\d{3}[ab]");
    }

    /** Borra todos los teams del orden (al apagar el plugin o si se desactiva en team.yml). */
    public void limpiar() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team t : sb.getTeams()) {
            if (esNuestro(t.getName())) t.unregister();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        actualizarTodo();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // al salir se saca de su team del orden (si no, queda guardado en el scoreboard del mundo)
        quitar(Bukkit.getScoreboardManager().getMainScoreboard(), event.getPlayer().getName());
        // el orden por "conectados" cambia cuando alguien sale: se acomoda en el tick siguiente
        Bukkit.getScheduler().runTask(plugin, this::actualizarTodo);
    }
}
