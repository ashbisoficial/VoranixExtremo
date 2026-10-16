package com.voranixextremo.plugin.economy;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /banco depositar|retirar — convierte entre monedas físicas y balance.
 */
public class BankCommand implements CommandExecutor {

    private final VoranixExtremoPlugin plugin;
    private final BankService bankService;

    public BankCommand(VoranixExtremoPlugin plugin, BankService bankService) {
        this.plugin = plugin;
        this.bankService = bankService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, plugin, "only-players");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Uso: /banco <depositar|retirar> [cantidad]");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("depositar")) {
            double total = bankService.depositAll(player);
            sender.sendMessage(total > 0 ? "Depositaste " + total + "." : "No tenías monedas físicas para depositar.");
            return true;
        }

        if (sub.equals("retirar")) {
            if (args.length < 2) {
                sender.sendMessage("Uso: /banco retirar <cantidad>");
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("La cantidad debe ser un número.");
                return true;
            }
            boolean success = bankService.withdraw(player, amount);
            sender.sendMessage(success
                    ? "Retiraste " + amount + " en monedas."
                    : "No se pudo retirar ese monto (fondos insuficientes o no se puede armar con las monedas configuradas).");
            return true;
        }

        sender.sendMessage("Uso: /banco <depositar|retirar> [cantidad]");
        return true;
    }
}
