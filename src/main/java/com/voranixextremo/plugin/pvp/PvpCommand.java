package com.voranixextremo.plugin.pvp;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;

/** /pvp [on|off|toggle] — sin argumento lo alterna. */
@CommandAlias("pvp")
@CommandPermission("voranixextremo.pvp")
@Description("Activa o desactiva el PVP del servidor.")
public class PvpCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;
    private final PvpManager pvpManager;

    public PvpCommand(VoranixExtremoPlugin plugin, PvpManager pvpManager) {
        this.plugin = plugin;
        this.pvpManager = pvpManager;
    }

    @Default
    @Subcommand("toggle")
    public void onToggle() {
        anunciar(pvpManager.toggle());
    }

    @Subcommand("on")
    public void onEnable() {
        pvpManager.setPvpEnabled(true);
        anunciar(true);
    }

    @Subcommand("off")
    public void onDisable() {
        pvpManager.setPvpEnabled(false);
        anunciar(false);
    }

    private void anunciar(boolean enabled) {
        Messages.broadcast(plugin, enabled ? "pvp-enabled" : "pvp-disabled");
    }
}
