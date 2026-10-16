package com.voranixextremo.plugin.economy;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /dinero [pagar|dar|quitar|set] — balance (banco) de cada jugador.
 */
public class EconomyCommand implements CommandExecutor {

    private final VoranixExtremoPlugin plugin;
    private final EconomyManager economyManager;

    public EconomyCommand(VoranixExtremoPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                Messages.send(sender, plugin, "only-players");
                return true;
            }
            sender.sendMessage("Tu balance: " + format(economyManager.getBalance(player)));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "pagar" -> handlePay(sender, args);
            case "dar", "quitar", "set" -> handleAdmin(sender, sub, args);
            default -> sender.sendMessage("Uso: /dinero [pagar <jugador> <cantidad>]");
        }
        return true;
    }

    private void handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player payer)) {
            Messages.send(sender, plugin, "only-players");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Uso: /dinero pagar <jugador> <cantidad>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Messages.send(sender, plugin, "player-not-found");
            return;
        }

        double amount = parseAmount(sender, args[2]);
        if (Double.isNaN(amount) || amount <= 0) {
            return;
        }

        if (!economyManager.withdraw(payer, amount)) {
            sender.sendMessage("No tenés suficiente dinero.");
            return;
        }

        economyManager.deposit(target, amount);
        sender.sendMessage("Le pagaste " + format(amount) + " a " + target.getName() + ".");
    }

    private void handleAdmin(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("voranixextremo.dinero.admin")) {
            Messages.send(sender, plugin, "no-permission");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Uso: /dinero " + sub + " <jugador> <cantidad>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Messages.send(sender, plugin, "player-not-found");
            return;
        }

        double amount = parseAmount(sender, args[2]);
        if (Double.isNaN(amount) || amount < 0) {
            return;
        }

        switch (sub) {
            case "dar" -> economyManager.deposit(target, amount);
            case "quitar" -> economyManager.withdraw(target, amount);
            case "set" -> economyManager.setBalance(target, amount);
            default -> { /* no alcanzable */ }
        }
        sender.sendMessage("Balance de " + target.getName() + " ahora: " + format(economyManager.getBalance(target)));
    }

    private double parseAmount(CommandSender sender, String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage("La cantidad debe ser un número.");
            return Double.NaN;
        }
    }

    private String format(double amount) {
        String name = amount == 1 ? economyManager.getCurrencySingular() : economyManager.getCurrencyPlural();
        String value = amount == Math.floor(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
        return value + " " + name;
    }
}
