package com.voranixextremo.plugin.worldevents;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /ruleta [girar|estado|detener] — pensado para que lo use producción/staff en un
 * momento del stream, no algo que cualquier jugador pueda disparar.
 */
public class WorldEventCommand implements CommandExecutor {

    private final VoranixExtremoPlugin plugin;
    private final WorldEventManager worldEventManager;

    public WorldEventCommand(VoranixExtremoPlugin plugin, WorldEventManager worldEventManager) {
        this.plugin = plugin;
        this.worldEventManager = worldEventManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voranixextremo.ruleta.admin")) {
            Messages.send(sender, plugin, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("girar")) {
            if (!worldEventManager.hasOutcomes()) {
                sender.sendMessage("No hay eventos configurados en world-events.outcomes.");
                return true;
            }
            if (worldEventManager.isEventActive()) {
                sender.sendMessage("Ya hay un evento activo: " + worldEventManager.getActiveEvent().getDisplayName()
                        + " (quedan " + worldEventManager.getActiveEventRemainingSeconds() + "s).");
                return true;
            }
            worldEventManager.spin();
            return true;
        }

        if (args[0].equalsIgnoreCase("estado")) {
            if (worldEventManager.isEventActive()) {
                sender.sendMessage("Evento activo: " + worldEventManager.getActiveEvent().getDisplayName()
                        + " (quedan " + worldEventManager.getActiveEventRemainingSeconds() + "s).");
            } else {
                sender.sendMessage("No hay ningún evento activo.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("detener")) {
            boolean stopped = worldEventManager.forceEnd();
            sender.sendMessage(stopped ? "Evento detenido." : "No hay ningún evento activo.");
            return true;
        }

        sender.sendMessage("Uso: /ruleta [girar|estado|detener]");
        return true;
    }
}
