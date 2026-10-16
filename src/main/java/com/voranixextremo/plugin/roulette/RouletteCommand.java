package com.voranixextremo.plugin.roulette;

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
import java.util.concurrent.ThreadLocalRandom;

/**
 * /ruleta [girar [jugador]|sortear|completar <jugador>|fallar <jugador>]
 */
public class RouletteCommand implements CommandExecutor, TabCompleter {

    private final VoranixExtremoPlugin plugin;
    private final RouletteManager rouletteManager;

    public RouletteCommand(VoranixExtremoPlugin plugin, RouletteManager rouletteManager) {
        this.plugin = plugin;
        this.rouletteManager = rouletteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("girar")) {
            return handleSpin(sender, args);
        }
        if (args[0].equalsIgnoreCase("sortear")) {
            return handleRandomDraw(sender);
        }
        if (args[0].equalsIgnoreCase("completar") || args[0].equalsIgnoreCase("fallar")) {
            return handleResolve(sender, args, args[0].equalsIgnoreCase("completar"));
        }
        sender.sendMessage("Uso: /ruleta [girar [jugador]|sortear|completar <jugador>|fallar <jugador>]");
        return true;
    }

    private boolean handleSpin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("voranixextremo.ruleta")) {
            Messages.send(sender, plugin, "no-permission");
            return true;
        }

        boolean isAdmin = sender.hasPermission("voranixextremo.ruleta.admin");
        Player target;
        if (args.length >= 2) {
            if (!isAdmin) {
                Messages.send(sender, plugin, "no-permission");
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Messages.send(sender, plugin, "player-not-found");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Messages.send(sender, plugin, "only-players");
            return true;
        }

        if (!isAdmin) {
            long remaining = rouletteManager.getRemainingCooldownSeconds(target);
            if (remaining > 0) {
                sender.sendMessage("Todavía tenés que esperar " + remaining + " segundos para volver a girar.");
                return true;
            }
        }

        if (!rouletteManager.hasOutcomes()) {
            sender.sendMessage("No hay sucesos configurados en roulette.outcomes.");
            return true;
        }

        rouletteManager.spin(target, isAdmin);
        return true;
    }

    private boolean handleRandomDraw(CommandSender sender) {
        if (!sender.hasPermission("voranixextremo.ruleta.admin")) {
            Messages.send(sender, plugin, "no-permission");
            return true;
        }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            sender.sendMessage("No hay jugadores conectados.");
            return true;
        }
        if (!rouletteManager.hasOutcomes()) {
            sender.sendMessage("No hay sucesos configurados en roulette.outcomes.");
            return true;
        }
        Player target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        rouletteManager.spin(target, true);
        return true;
    }

    private boolean handleResolve(CommandSender sender, String[] args, boolean success) {
        if (!sender.hasPermission("voranixextremo.ruleta.admin")) {
            Messages.send(sender, plugin, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Uso: /ruleta " + args[0] + " <jugador>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            Messages.send(sender, plugin, "player-not-found");
            return true;
        }
        boolean resolved = rouletteManager.resolveTask(target, success);
        sender.sendMessage(resolved ? "Tarea resuelta." : (target.getName() + " no tiene ninguna tarea activa."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("girar", "sortear", "completar", "fallar");
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return names;
        }
        return List.of();
    }
}
