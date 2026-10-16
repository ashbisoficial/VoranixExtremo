package com.voranixextremo.plugin.lives;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LivesCommand implements CommandExecutor, TabCompleter {

    private final VoranixExtremoPlugin plugin;
    private final LivesManager livesManager;

    public LivesCommand(VoranixExtremoPlugin plugin, LivesManager livesManager) {
        this.plugin = plugin;
        this.livesManager = livesManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                Messages.send(sender, plugin, "only-players");
                return true;
            }
            Messages.send(sender, plugin, "vidas-check", "%vidas%", String.valueOf(livesManager.getLives(player)));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("set") || sub.equals("reset")) {
            return handleAdminSubcommand(sender, sub, args);
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Messages.send(sender, plugin, "player-not-found");
            return true;
        }

        Messages.send(sender, plugin, "vidas-check-other",
                "%player%", String.valueOf(target.getName()),
                "%vidas%", String.valueOf(livesManager.getLives(target)));
        return true;
    }

    private boolean handleAdminSubcommand(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("voranixextremo.vidas.admin")) {
            Messages.send(sender, plugin, "no-permission");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Uso: /vidas " + sub + " <jugador>" + (sub.equals("set") ? " <cantidad>" : ""));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            Messages.send(sender, plugin, "player-not-found");
            return true;
        }

        if (sub.equals("reset")) {
            livesManager.resetLives(target);
            sender.sendMessage("Vidas de " + target.getName() + " restablecidas a " + livesManager.getMaxLives() + ".");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("Uso: /vidas set <jugador> <cantidad>");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("La cantidad debe ser un número entero.");
            return true;
        }

        livesManager.setLives(target, amount);
        sender.sendMessage("Vidas de " + target.getName() + " establecidas en " + amount + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("set", "reset"));
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            String partial = args[0].toLowerCase();
            return options.stream().filter(o -> o.toLowerCase().startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("reset"))) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
