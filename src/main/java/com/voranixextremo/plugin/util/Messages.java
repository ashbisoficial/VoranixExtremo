package com.voranixextremo.plugin.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public final class Messages {

    private Messages() {
    }

    public static String get(Plugin plugin, String path, String... replacements) {
        FileConfiguration config = plugin.getConfig();
        String prefix = config.getString("messages.prefix", "");
        String raw = config.getString("messages." + path, path);

        String message = prefix + raw;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static void send(CommandSender sender, Plugin plugin, String path, String... replacements) {
        sender.sendMessage(get(plugin, path, replacements));
    }

    public static void broadcast(Plugin plugin, String path, String... replacements) {
        Bukkit.broadcastMessage(get(plugin, path, replacements));
    }
}
