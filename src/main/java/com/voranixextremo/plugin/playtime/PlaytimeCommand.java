package com.voranixextremo.plugin.playtime;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /tiempo [jugador] | /tiempo set <jugador> <horasUsadas> | /tiempo agregar <jugador> <horas> |
 * /tiempo reset <jugador>
 */
@CommandAlias("tiempo")
@CommandPermission("voranixextremo.tiempo")
@Description("Consulta o administra el tiempo jugado (tope para que nadie se adelante farmeando).")
public class PlaytimeCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;
    private final PlaytimeManager tiempo;

    public PlaytimeCommand(VoranixExtremoPlugin plugin, PlaytimeManager tiempo) {
        this.plugin = plugin;
        this.tiempo = tiempo;
    }

    @Default
    @CommandCompletion("@players")
    @Syntax("[jugador]")
    public void onCheck(CommandSender sender, @Optional OfflinePlayer target) {
        if (target == null) {
            if (!(sender instanceof Player player)) {
                Messages.send(sender, plugin, "only-players");
                return;
            }
            Messages.send(sender, plugin, "playtime-check",
                    "%tiempo%", PlaytimeManager.formatear(tiempo.getSegundosRestantes(player)));
            return;
        }
        Messages.send(sender, plugin, "playtime-check-other",
                "%player%", target.getName(),
                "%tiempo%", PlaytimeManager.formatear(tiempo.getSegundosRestantes(target)));
    }

    @Subcommand("set")
    @CommandPermission("voranixextremo.tiempo.admin")
    @CommandCompletion("@players @nothing")
    @Syntax("<jugador> <horasUsadas>")
    public void onSet(CommandSender sender, OfflinePlayer target, double horasUsadas) {
        tiempo.setSegundosJugados(target, Math.round(horasUsadas * 3600));
        sender.sendMessage("Tiempo jugado de " + target.getName() + " establecido en " + horasUsadas + "h.");
    }

    @Subcommand("agregar")
    @CommandPermission("voranixextremo.tiempo.admin")
    @CommandCompletion("@players @nothing")
    @Syntax("<jugador> <horas>")
    public void onAgregar(CommandSender sender, OfflinePlayer target, double horas) {
        // "agregar" es tiempo DISPONIBLE: se resta del tiempo ya usado.
        tiempo.agregarSegundos(target, -Math.round(horas * 3600));
        sender.sendMessage("Se le agregaron " + horas + "h de tiempo disponible a " + target.getName() + ".");
    }

    @Subcommand("reset")
    @CommandPermission("voranixextremo.tiempo.admin")
    @CommandCompletion("@players")
    @Syntax("<jugador>")
    public void onReset(CommandSender sender, OfflinePlayer target) {
        tiempo.resetear(target);
        sender.sendMessage("Tiempo jugado de " + target.getName() + " reseteado (presupuesto completo de nuevo).");
    }
}
