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

/** /vidas [jugador] | /vidas set <jugador> <cantidad> | /vidas reset <jugador> */
@CommandAlias("vidas")
@CommandPermission("voranixextremo.vidas")
@Description("Consulta o administra las vidas de los jugadores.")
public class LivesCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;
    private final LivesManager livesManager;

    public LivesCommand(VoranixExtremoPlugin plugin, LivesManager livesManager) {
        this.plugin = plugin;
        this.livesManager = livesManager;
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
}
