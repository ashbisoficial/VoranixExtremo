package com.voranixextremo.plugin.worldevents;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.command.CommandSender;

/**
 * /ruleta [girar|estado|detener] — pensado para que lo use producción/staff en un
 * momento del stream, no algo que cualquier jugador pueda disparar.
 */
@CommandAlias("ruleta")
@CommandPermission("voranixextremo.ruleta.admin")
@Description("Gira la ruleta de eventos (afecta a todo el servidor, se ve en la pantalla de todos).")
public class WorldEventCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;
    private final WorldEventManager worldEventManager;

    public WorldEventCommand(VoranixExtremoPlugin plugin, WorldEventManager worldEventManager) {
        this.plugin = plugin;
        this.worldEventManager = worldEventManager;
    }

    @Default
    @Subcommand("girar")
    public void onSpin(CommandSender sender) {
        if (!worldEventManager.hasOutcomes()) {
            sender.sendMessage("No hay eventos configurados en world-events.outcomes.");
            return;
        }
        if (worldEventManager.isEventActive()) {
            sender.sendMessage("Ya hay un evento activo: " + worldEventManager.getActiveEvent().getDisplayName()
                    + " (quedan " + worldEventManager.getActiveEventRemainingSeconds() + "s).");
            return;
        }
        worldEventManager.spin();
    }

    @Subcommand("estado")
    public void onStatus(CommandSender sender) {
        if (worldEventManager.isEventActive()) {
            sender.sendMessage("Evento activo: " + worldEventManager.getActiveEvent().getDisplayName()
                    + " (quedan " + worldEventManager.getActiveEventRemainingSeconds() + "s).");
        } else {
            sender.sendMessage("No hay ningún evento activo.");
        }
    }

    @Subcommand("detener")
    public void onStop(CommandSender sender) {
        boolean stopped = worldEventManager.forceEnd();
        sender.sendMessage(stopped ? "Evento detenido." : "No hay ningún evento activo.");
    }
}
