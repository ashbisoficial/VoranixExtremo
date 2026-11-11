package com.voranixextremo.plugin.lives;

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

/** /vidas [jugador] | /vidas set <jugador> <cantidad> | /vidas reset <jugador> | /vidas revivir <jugador> */
@CommandAlias("vidas")
@CommandPermission("voranixextremo.vidas")
@Description("Consulta o administra las vidas de los jugadores.")
public class LivesCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;
    private final LivesManager livesManager;
    private final StormEffect tormenta;

    public LivesCommand(VoranixExtremoPlugin plugin, LivesManager livesManager, StormEffect tormenta) {
        this.plugin = plugin;
        this.livesManager = livesManager;
        this.tormenta = tormenta;
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
            Messages.send(sender, plugin, "vidas-check", "%vidas%", String.valueOf(livesManager.getLives(player)));
            return;
        }
        Messages.send(sender, plugin, "vidas-check-other",
                "%player%", String.valueOf(target.getName()),
                "%vidas%", String.valueOf(livesManager.getLives(target)));
    }

    @Subcommand("set")
    @CommandPermission("voranixextremo.vidas.admin")
    @CommandCompletion("@players @nothing")
    @Syntax("<jugador> <cantidad>")
    public void onSet(CommandSender sender, OfflinePlayer target, int cantidad) {
        livesManager.setLives(target, cantidad);
        sender.sendMessage("Vidas de " + target.getName() + " establecidas en " + cantidad + ".");
    }

    @Subcommand("reset")
    @CommandPermission("voranixextremo.vidas.admin")
    @CommandCompletion("@players")
    @Syntax("<jugador>")
    public void onReset(CommandSender sender, OfflinePlayer target) {
        livesManager.resetLives(target);
        sender.sendMessage("Vidas de " + target.getName() + " restablecidas a " + livesManager.getMaxLives() + ".");
    }

    /**
     * El "revivir" de verdad: le da UNA vida (no el máximo como /vidas reset) y dispara la
     * tormenta (nubes + rayos) justo donde está parado - pensado para el momento dramático de
     * traer de vuelta a alguien con el ítem legendario. Necesita que esté online (para la
     * ubicación de la tormenta); si está offline, usá /vidas reset o /vidas set.
     */
    @Subcommand("revivir")
    @CommandPermission("voranixextremo.vidas.admin")
    @CommandCompletion("@players")
    @Syntax("<jugador>")
    public void onRevivir(CommandSender sender, Player target) {
        livesManager.revivirCon(target, 1);
        tormenta.disparar(target.getLocation(), 15);
        Messages.broadcast(plugin, "revived", "%player%", target.getName());
    }
}
