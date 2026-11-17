package com.voranixextremo.plugin.economy;

import com.voranixextremo.plugin.util.Messages;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /dinero | /dinero pagar <jugador> <cantidad> | /dinero dar|quitar|set <jugador> <cantidad> */
@CommandAlias("dinero")
@CommandPermission("voranixextremo.economia")
@Description("Consulta o administra el balance (banco) de los jugadores.")
public class EconomyCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;
    private final EconomyManager economyManager;

    public EconomyCommand(VoranixExtremoPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Default
    public void onBalance(Player player) {
        Messages.send(player, "balance", "%cantidad%", format(economyManager.getBalance(player)));
    }

    @Subcommand("pagar")
    @CommandCompletion("@players @nothing")
    @Syntax("<jugador> <cantidad>")
    public void onPay(Player payer, OfflinePlayer target, double cantidad) {
        if (cantidad <= 0) {
            Messages.send(payer, "pay-invalid");
            return;
        }
        if (!economyManager.withdraw(payer, cantidad)) {
            Messages.send(payer, "pay-insufficient");
            return;
        }
        economyManager.deposit(target, cantidad);
        Messages.send(payer, "pay-done", "%cantidad%", format(cantidad), "%player%", String.valueOf(target.getName()));
    }

    @Subcommand("dar")
    @CommandPermission("voranixextremo.dinero.admin")
    @CommandCompletion("@players @nothing")
    @Syntax("<jugador> <cantidad>")
    public void onGive(CommandSender sender, OfflinePlayer target, double cantidad) {
        if (cantidadValida(sender, cantidad)) {
            economyManager.deposit(target, cantidad);
            mostrarBalance(sender, target);
        }
    }

    @Subcommand("quitar")
    @CommandPermission("voranixextremo.dinero.admin")
    @CommandCompletion("@players @nothing")
    @Syntax("<jugador> <cantidad>")
    public void onTake(CommandSender sender, OfflinePlayer target, double cantidad) {
        if (cantidadValida(sender, cantidad)) {
            economyManager.withdraw(target, cantidad);
            mostrarBalance(sender, target);
        }
    }

    @Subcommand("set")
    @CommandPermission("voranixextremo.dinero.admin")
    @CommandCompletion("@players @nothing")
    @Syntax("<jugador> <cantidad>")
    public void onSet(CommandSender sender, OfflinePlayer target, double cantidad) {
        if (cantidadValida(sender, cantidad)) {
            economyManager.setBalance(target, cantidad);
            mostrarBalance(sender, target);
        }
    }

    private boolean cantidadValida(CommandSender sender, double cantidad) {
        if (cantidad < 0) {
            Messages.send(sender, "amount-negative");
            return false;
        }
        return true;
    }

    private void mostrarBalance(CommandSender sender, OfflinePlayer target) {
        Messages.send(sender, "balance-other", "%player%", String.valueOf(target.getName()), "%cantidad%", format(economyManager.getBalance(target)));
    }

    private String format(double amount) {
        String name = amount == 1 ? economyManager.getCurrencySingular() : economyManager.getCurrencyPlural();
        String value = amount == Math.floor(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
        return value + " " + name;
    }
}
