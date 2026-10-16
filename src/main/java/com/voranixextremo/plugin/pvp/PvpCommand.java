package com.voranixextremo.plugin.pvp;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class PvpCommand implements CommandExecutor, TabCompleter {

    private final VoranixExtremoPlugin plugin;
    private final PvpManager pvpManager;

    public PvpCommand(VoranixExtremoPlugin plugin, PvpManager pvpManager) {
        this.plugin = plugin;
        this.pvpManager = pvpManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voranixextremo.pvp")) {
            Messages.send(sender, plugin, "no-permission");
            return true;
        }

        boolean newState;
        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            newState = pvpManager.toggle();
        } else if (args[0].equalsIgnoreCase("on")) {
            pvpManager.setPvpEnabled(true);
            newState = true;
        } else if (args[0].equalsIgnoreCase("off")) {
            pvpManager.setPvpEnabled(false);
            newState = false;
        } else {
            sender.sendMessage("Uso: /pvp [on|off|toggle]");
            return true;
        }

        Messages.broadcast(plugin, newState ? "pvp-enabled" : "pvp-disabled");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("on", "off", "toggle");
        }
        return List.of();
    }
}
