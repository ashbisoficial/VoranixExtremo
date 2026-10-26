package com.voranixextremo.plugin.voranixextremo;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.command.CommandSender;

@CommandAlias("voranixextremo")
@CommandPermission("voranixextremo.admin")
@Description("Comandos de administración de VoranixExtremo.")
public class VoranixExtremoCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;

    public VoranixExtremoCommand(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @CatchUnknown
    public void onUsage(CommandSender sender) {
        sender.sendMessage("Uso: /voranixextremo reload");
    }

    @Subcommand("reload")
    public void onReload(CommandSender sender) {
        plugin.recargar();
        Messages.send(sender, plugin, "reloaded");
    }
}
