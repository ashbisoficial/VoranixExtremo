package com.voranixextremo.plugin.ruleta;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * /ruletacolor girar <color|random> [mensaje] | parar | pack | reload
 * (no es /ruleta porque ese ya es la ruleta de eventos).
 */
@CommandAlias("ruletacolor")
@CommandPermission("voranixextremo.ruletacolor.admin")
@Description("Gira la ruleta de colores en pantalla (con resource pack).")
public class RuletaCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;
    private final RuletaManager ruleta;
    private final RuletaPackManager pack;

    public RuletaCommand(VoranixExtremoPlugin plugin, RuletaManager ruleta, RuletaPackManager pack) {
        this.plugin = plugin;
        this.ruleta = ruleta;
        this.pack = pack;
    }

    @Default
    @CatchUnknown
    public void onUsage(CommandSender sender) {
        ruleta.enviar(sender, ruleta.msg("uso"));
    }

    // el último String se queda con todo lo que venga después, así el mensaje puede tener espacios
    @Subcommand("girar")
    @CommandCompletion("@colores-ruleta @nothing")
    @Syntax("<color|random> [mensaje]")
    public void onSpin(CommandSender sender, String color, @Optional String mensaje) {
        Component error = ruleta.spin(color.toLowerCase(Locale.ROOT), mensaje);
        if (error != null) ruleta.enviar(sender, error);
    }

    @Subcommand("parar")
    public void onStop(CommandSender sender) {
        ruleta.enviar(sender, ruleta.msg(ruleta.stop(true) ? "parada" : "no-girando"));
    }

    @Subcommand("pack")
    public void onPack(CommandSender sender) {
        for (Player p : Bukkit.getOnlinePlayers()) pack.send(p);
        ruleta.enviar(sender, ruleta.msg("pack-enviado"));
    }

    @Subcommand("reload")
    public void onReload(CommandSender sender) {
        plugin.reloadConfig();
        ruleta.load();
        pack.stop();
        pack.start();
        ruleta.enviar(sender, ruleta.msg("recargado"));
    }
}
