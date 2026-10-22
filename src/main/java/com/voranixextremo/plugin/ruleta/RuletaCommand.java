package com.voranixextremo.plugin.ruleta;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /ruletacolor girar <color|random> [mensaje] | parar | pack | reload
 * (no es /ruleta porque ese ya es la ruleta de eventos).
 */
public class RuletaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMANDOS = List.of("girar", "parar", "pack", "reload");

    private final VoranixExtremoPlugin plugin;
    private final RuletaManager ruleta;
    private final RuletaPackManager pack;

    public RuletaCommand(VoranixExtremoPlugin plugin, RuletaManager ruleta, RuletaPackManager pack) {
        this.plugin = plugin;
        this.ruleta = ruleta;
        this.pack = pack;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ruleta.msg("uso"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "girar" -> {
                if (args.length < 2) {
                    sender.sendMessage(ruleta.msg("uso"));
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                String mensaje = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
                Component error = ruleta.spin(id, mensaje);
                if (error != null) sender.sendMessage(error);
            }
            case "parar" -> sender.sendMessage(ruleta.msg(ruleta.stop(true) ? "parada" : "no-girando"));
            case "pack" -> {
                for (Player p : Bukkit.getOnlinePlayers()) pack.send(p);
                sender.sendMessage(ruleta.msg("pack-enviado"));
            }
            case "reload" -> {
                plugin.reloadConfig();
                ruleta.load();
                pack.stop();
                pack.start();
                sender.sendMessage(ruleta.msg("recargado"));
            }
            default -> sender.sendMessage(ruleta.msg("uso"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> opciones = new ArrayList<>();
        if (args.length == 1) {
            opciones.addAll(SUBCOMANDOS);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("girar")) {
            opciones.addAll(ruleta.coloresEnRuleta());
            opciones.add("random");
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return opciones.stream().filter(o -> o.startsWith(typed)).toList();
    }
}
