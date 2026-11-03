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
        sender.sendMessage("Uso: /voranixextremo <reload|texturepackreload>");
    }

    @Subcommand("reload")
    public void onReload(CommandSender sender) {
        plugin.recargar();
        Messages.send(sender, plugin, "reloaded");
    }

    /**
     * Después de actualizar el resource pack (por ejemplo el del VPS): lo vuelve a revisar y se lo
     * manda de nuevo a todos los conectados, que lo descargan otra vez porque cambió su sha1.
     */
    @Subcommand("texturepackreload")
    public void onTexturePackReload(CommandSender sender) {
        Messages.send(sender, plugin, "texturepack-reloading");
        plugin.getRuletaPackManager().recargarYEnviar(enviados ->
                Messages.send(sender, plugin, "texturepack-reloaded", "%jugadores%", String.valueOf(enviados)));
    }
}
