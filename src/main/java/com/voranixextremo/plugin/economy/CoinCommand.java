package com.voranixextremo.plugin.economy;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /moneda dar <jugador> <tipo> [cantidad] — entrega monedas físicas (admin).
 */
public class CoinCommand implements CommandExecutor, TabCompleter {

    private final VoranixExtremoPlugin plugin;
    private final EconomyManager economyManager;
    private final CoinItemFactory coinItemFactory;

    public CoinCommand(VoranixExtremoPlugin plugin, EconomyManager economyManager, CoinItemFactory coinItemFactory) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.coinItemFactory = coinItemFactory;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voranixextremo.moneda.admin")) {
            Messages.send(sender, plugin, "no-permission");
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("dar")) {
            sender.sendMessage("Uso: /moneda dar <jugador> <tipo> [cantidad]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            Messages.send(sender, plugin, "player-not-found");
            return true;
        }

        CoinType type = economyManager.getCoinType(args[2]);
        if (type == null) {
            sender.sendMessage("Tipo de moneda desconocido. Disponibles: "
                    + String.join(", ", economyManager.getCoinTypes().keySet()));
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("La cantidad debe ser un número entero.");
                return true;
            }
        }

        target.getInventory().addItem(coinItemFactory.create(type, amount));
        sender.sendMessage("Le diste " + amount + "x " + type.getId() + " a " + target.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("dar");
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return names;
        }
        if (args.length == 3) {
            return new ArrayList<>(economyManager.getCoinTypes().keySet());
        }
        return List.of();
    }
}
