package com.voranixextremo.plugin.teamsystem;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /team create|invite|accept|leave|kick|disband|info|list|chat|menu|color — ver team.yml. */
@CommandAlias("team")
@CommandPermission("voranixextremo.team")
@Description("Crea y administra tu team.")
public class TeamCommand extends BaseCommand {

    private final TeamManager teams;
    private final TeamGui gui;

    public TeamCommand(TeamManager teams, TeamGui gui) {
        this.teams = teams;
        this.gui = gui;
    }

    /** /team help, /team solo o un subcomando que no existe: muestra la ayuda de team.yml → ayuda. */
    @Default
    @CatchUnknown
    @Subcommand("help|ayuda")
    public void onHelp(CommandSender sender) {
        for (String linea : teams.getAyuda()) sender.sendMessage(linea);
    }

    /** /team create <nombre> <tag>, por ejemplo /team create Voranix VRX */
    @Subcommand("create")
    @Syntax("<nombre> <tag>")
    public void onCreate(Player player, String nombre, @Optional String tag) {
        if (teams.getTeam(player.getUniqueId()) != null) {
            player.sendMessage(teams.msg("ya-en-team"));
            return;
        }
        if (tag == null) {
            player.sendMessage(teams.msg("uso-create"));
            return;
        }
        String error = teams.validarNombre(nombre);
        if (error == null) error = teams.validarTag(tag);
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        Team team = teams.crear(nombre, tag, player.getUniqueId());
        player.sendMessage(teams.msg(team, "creado"));
    }

    /** /team menu: el menú con los datos del team, sus integrantes y el color. */
    @Subcommand("menu|settings|gui")
    public void onMenu(Player player) {
        Team team = teams.getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(teams.msg("sin-team"));
            return;
        }
        gui.abrir(player, team);
    }

    /** /team color [color]: sin color abre el menú de colores; con color lo cambia directo (solo el líder). */
    @Subcommand("color")
    @CommandCompletion("@colores-team")
    @Syntax("[color]")
    public void onColor(Player player, @Optional String nombre) {
        Team team = equipoDeLider(player);
        if (team == null) return;
        if (nombre == null) {
            gui.abrirColores(player, team);
            return;
        }
        TeamManager.ColorOpcion color = teams.getColor(nombre);
        if (color == null) {
            List<String> ids = new ArrayList<>();
            for (TeamManager.ColorOpcion c : teams.getColores()) ids.add(c.id());
            player.sendMessage(teams.msg("color-invalido", "%color%", nombre, "%disponibles%", String.join(", ", ids)));
            return;
        }
        teams.cambiarColor(team, color);
        teams.avisar(team, teams.msg("color-cambiado", "%player%", player.getName(),
                "%color%", color.codigo(), "%nombre-color%", color.nombre()));
    }

    @Subcommand("invite")
    @CommandCompletion("@players")
    @Syntax("<jugador>")
    public void onInvite(Player player, OnlinePlayer objetivo) {
        Team team = equipoDeLider(player);
        if (team == null) return;
        Player invitado = objetivo.getPlayer();
        if (invitado.equals(player)) {
            player.sendMessage(teams.msg("no-a-ti"));
            return;
        }
        if (teams.getTeam(invitado.getUniqueId()) != null) {
            player.sendMessage(teams.msg("jugador-ya-en-team", "%player%", invitado.getName()));
            return;
        }
        if (team.size() >= teams.getCapacidad()) {
            player.sendMessage(teams.msg("team-lleno", "%max%", String.valueOf(teams.getCapacidad())));
            return;
        }
        if (teams.tieneInvitacion(invitado.getUniqueId(), team)) {
            player.sendMessage(teams.msg("ya-invitado", "%player%", invitado.getName()));
            return;
        }
        teams.invitar(invitado.getUniqueId(), team);
        player.sendMessage(teams.msg("invitacion-enviada", "%player%", invitado.getName(),
                "%segundos%", String.valueOf(teams.getSegundosInvitacion())));
        invitado.sendMessage(teams.msg("invitacion-recibida", "%player%", player.getName(), "%team%", team.getName()));
    }

    @Subcommand("accept")
    @CommandCompletion("@teams")
    @Syntax("<team>")
    public void onAccept(Player player, String nombre) {
        if (teams.getTeam(player.getUniqueId()) != null) {
            player.sendMessage(teams.msg("ya-en-team"));
            return;
        }
        Team team = teams.getTeamByName(nombre);
        if (team == null) {
            player.sendMessage(teams.msg("team-no-existe", "%team%", nombre));
            return;
        }
        if (!teams.tieneInvitacion(player.getUniqueId(), team)) {
            player.sendMessage(teams.msg("sin-invitacion", "%team%", team.getName()));
            return;
        }
        if (team.size() >= teams.getCapacidad()) {
            player.sendMessage(teams.msg("team-lleno", "%max%", String.valueOf(teams.getCapacidad())));
            return;
        }
        teams.unir(player.getUniqueId(), team);
        teams.avisar(team, teams.msg("unido", "%player%", player.getName()));
    }

    @Subcommand("leave")
    public void onLeave(Player player) {
        Team team = teams.getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(teams.msg("sin-team"));
            return;
        }
        UUID nuevoLider = teams.sacar(player.getUniqueId());
        player.sendMessage(teams.msg("saliste", "%team%", team.getName()));
        teams.avisar(team, teams.msg("salio", "%player%", player.getName()));
        if (nuevoLider != null) {
            teams.avisar(team, teams.msg("nuevo-lider", "%player%", TeamManager.nombreDe(nuevoLider)));
        }
    }

    @Subcommand("kick")
    @CommandCompletion("@players")
    @Syntax("<jugador>")
    public void onKick(Player player, String nombre) {
        Team team = equipoDeLider(player);
        if (team == null) return;
        UUID objetivo = null;
        for (UUID uuid : team.getMembers()) {
            if (TeamManager.nombreDe(uuid).equalsIgnoreCase(nombre)) objetivo = uuid;
        }
        if (objetivo == null) {
            player.sendMessage(teams.msg("no-miembro", "%player%", nombre));
            return;
        }
        if (objetivo.equals(player.getUniqueId())) {
            player.sendMessage(teams.msg("no-a-ti"));
            return;
        }
        String nombreReal = TeamManager.nombreDe(objetivo);
        teams.sacar(objetivo);
        player.sendMessage(teams.msg("expulsaste", "%player%", nombreReal));
        Player expulsado = org.bukkit.Bukkit.getPlayer(objetivo);
        if (expulsado != null) expulsado.sendMessage(teams.msg("expulsado", "%team%", team.getName()));
        teams.avisar(team, teams.msg("salio", "%player%", nombreReal));
    }

    @Subcommand("disband")
    public void onDisband(Player player) {
        Team team = equipoDeLider(player);
        if (team == null) return;
        teams.avisar(team, teams.msg("disuelto", "%team%", team.getName()));
        teams.disolver(team);
    }

    @Subcommand("info")
    @CommandCompletion("@teams")
    @Syntax("[team]")
    public void onInfo(CommandSender sender, @Optional String nombre) {
        Team team;
        if (nombre != null) {
            team = teams.getTeamByName(nombre);
            if (team == null) {
                sender.sendMessage(teams.msg("team-no-existe", "%team%", nombre));
                return;
            }
        } else if (sender instanceof Player player) {
            team = teams.getTeam(player.getUniqueId());
            if (team == null) {
                sender.sendMessage(teams.msg("sin-team"));
                return;
            }
        } else {
            sender.sendMessage("Uso: /team info <team>");
            return;
        }
        List<String> miembros = new ArrayList<>();
        for (UUID uuid : team.getMembers()) miembros.add(TeamManager.nombreDe(uuid));
        sender.sendMessage(teams.msg(team, "info-titulo",
                "%cantidad%", String.valueOf(team.size()), "%max%", String.valueOf(teams.getCapacidad())));
        sender.sendMessage(teams.msg("info-lider", "%player%", TeamManager.nombreDe(team.getLeader())));
        sender.sendMessage(teams.msg("info-miembros", "%miembros%", String.join(", ", miembros)));
    }

    @Subcommand("list")
    public void onList(CommandSender sender) {
        if (teams.getTeams().isEmpty()) {
            sender.sendMessage(teams.msg("lista-vacia"));
            return;
        }
        sender.sendMessage(teams.msg("lista-titulo", "%cantidad%", String.valueOf(teams.getTeams().size())));
        for (Team team : teams.getTeams()) {
            sender.sendMessage(teams.msg(team, "lista-linea",
                    "%cantidad%", String.valueOf(team.size()), "%max%", String.valueOf(teams.getCapacidad())));
        }
    }

    @Subcommand("chat join")
    public void onChatJoin(Player player) {
        if (teams.getTeam(player.getUniqueId()) == null) {
            player.sendMessage(teams.msg("sin-team"));
            return;
        }
        teams.setEnChatDeTeam(player.getUniqueId(), true);
        player.sendMessage(teams.msg("chat-join"));
    }

    @Subcommand("chat leave")
    public void onChatLeave(Player player) {
        teams.setEnChatDeTeam(player.getUniqueId(), false);
        player.sendMessage(teams.msg("chat-leave"));
    }

    /** El team del jugador si es su líder; si no, avisa y devuelve null. */
    private Team equipoDeLider(Player player) {
        Team team = teams.getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(teams.msg("sin-team"));
            return null;
        }
        if (!team.isLeader(player.getUniqueId())) {
            player.sendMessage(teams.msg("solo-lider"));
            return null;
        }
        return team;
    }
}
