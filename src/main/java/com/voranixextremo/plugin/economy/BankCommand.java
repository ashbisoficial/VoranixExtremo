package com.voranixextremo.plugin.economy;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /banco depositar | /banco retirar <cantidad> — convierte monedas físicas en balance y viceversa. */
@CommandAlias("banco")
@CommandPermission("voranixextremo.economia")
@Description("Convierte monedas físicas en balance y viceversa.")
public class BankCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;
    private final BankService bankService;

    public BankCommand(VoranixExtremoPlugin plugin, BankService bankService) {
        this.plugin = plugin;
        this.bankService = bankService;
    }

    @Default
    @CatchUnknown
    public void onUsage(CommandSender sender) {
        sender.sendMessage("Uso: /banco <depositar|retirar> [cantidad]");
    }

    @Subcommand("depositar")
    public void onDeposit(Player player) {
        double total = bankService.depositAll(player);
        player.sendMessage(total > 0 ? "Depositaste " + total + "." : "No tenías monedas físicas para depositar.");
    }

    @Subcommand("retirar")
    @Syntax("<cantidad>")
    public void onWithdraw(Player player, double cantidad) {
        boolean success = bankService.withdraw(player, cantidad);
        player.sendMessage(success
                ? "Retiraste " + cantidad + " en monedas."
                : "No se pudo retirar ese monto (fondos insuficientes o no se puede armar con las monedas configuradas).");
    }
}
