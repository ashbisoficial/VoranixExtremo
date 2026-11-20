package com.voranixextremo.plugin.teamsystem;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Placeholders de PlaceholderAPI con el team del jugador, para usarlos como prefijo o sufijo en el
 * chat, el tab, el scoreboard, etc. Si el jugador no tiene team, devuelven "" (o lo que diga
 * team.yml → placeholder.sin-team en %voranixteam_sufijo% y %voranixteam_prefijo%).
 *
 *   %voranixteam_tag%            VRX (sin color)
 *   %voranixteam_tag_color%      VRX pintado con el color del team
 *   %voranixteam_sufijo%         team.yml → placeholder.sufijo  (por defecto " &8[&cVRX&8]")
 *   %voranixteam_prefijo%        team.yml → placeholder.prefijo (por defecto "&8[&cVRX&8] ")
 *   %voranixteam_nombre%         Voranix (sin color)
 *   %voranixteam_nombre_color%   Voranix pintado con el color del team
 *   %voranixteam_color%          el código del color (para pintar lo que va después)
 *   %voranixteam_color_nombre%   Rojo
 *   %voranixteam_lider%          nombre del líder
 *   %voranixteam_miembros%       cantidad de integrantes
 *   %voranixteam_max%            capacidad del team
 *   %voranixteam_tiene%          "si" o "no"
 *   %voranixteam_posicion%       posición del team en el orden del tab (1, 2...; vacío sin team)
 *   %voranixteam_orden%          clave para ordenar el tab en otro plugin (TAB...): 001a, 001b... y 999z sin team
 */
public class TeamPlaceholders extends PlaceholderExpansion {

    private final VoranixExtremoPlugin plugin;
    private final TeamManager teams;

    public TeamPlaceholders(VoranixExtremoPlugin plugin, TeamManager teams) {
        this.plugin = plugin;
        this.teams = teams;
    }

    @Override
    public String getIdentifier() {
        return "voranixteam";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Que no se borre cuando se recarga PlaceholderAPI (/papi reload). */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        Team team = teams.getTeam(player.getUniqueId());
        String p = params.toLowerCase();
        if (p.equals("tiene")) return team != null ? "si" : "no";
        if (p.equals("orden")) {
            if (team == null) return "999z";
            return String.format("%03d", Math.min(teams.getPosicion(team), 998)) + (team.isLeader(player.getUniqueId()) ? "a" : "b");
        }
        if (team == null) {
            return p.equals("sufijo") || p.equals("prefijo") ? ColorUtils.color(teams.getPlaceholder("sin-team")) : "";
        }
        return switch (p) {
            case "tag" -> team.getTag();
            case "tag_color" -> ColorUtils.color(team.getColor() + team.getTag());
            case "sufijo", "prefijo" -> ColorUtils.color(teams.aplicar(teams.getPlaceholder(p), team));
            case "nombre" -> team.getName();
            case "nombre_color" -> ColorUtils.color(team.getColor() + team.getName());
            case "color" -> ColorUtils.color(team.getColor());
            case "color_nombre" -> teams.nombreDeColor(team.getColor());
            case "lider" -> TeamManager.nombreDe(team.getLeader());
            case "miembros" -> String.valueOf(team.size());
            case "max" -> String.valueOf(teams.getCapacidad());
            case "posicion" -> String.valueOf(teams.getPosicion(team));
            default -> null; // placeholder desconocido: PlaceholderAPI lo deja tal cual
        };
    }
}
