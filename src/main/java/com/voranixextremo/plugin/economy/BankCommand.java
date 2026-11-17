package com.voranixextremo.plugin.economy;

import com.voranixextremo.plugin.util.Messages;
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
        Messages.send(sender, "bank-usage");
    }

    @Subcommand("depositar")
    public void onDeposit(Player player) {
        double total = bankService.depositAll(player);
        if (total > 0) {
            Messages.send(player, "bank-deposited", "%cantidad%", String.valueOf(total));
        } else {
            Messages.send(player, "bank-nothing");
        }
    }

    @Subcommand("retirar")
    @Syntax("<cantidad>")
    public void onWithdraw(Player player, double cantidad) {
        boolean success = bankService.withdraw(player, cantidad);
        if (success) {
            Messages.send(player, "bank-withdrawn", "%cantidad%", String.valueOf(cantidad));
        } else {
            Messages.send(player, "bank-withdraw-failed");
        }
    }
}
