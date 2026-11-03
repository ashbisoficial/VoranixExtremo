package com.voranixextremo.plugin.visualfx;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import org.bukkit.command.CommandSender;

/**
 * /fx cielo <color|#rrggbb> [segundos] [fundido] | parar
 * Necesita que el jugador tenga instalado el mod cliente VoranixVisualFX (repo aparte, Forge) -
 * a quien no lo tenga no le pasa nada, el comando "funciona" igual (manda el paquete a todos).
 */
@CommandAlias("fx")
@CommandPermission("voranixextremo.fx.admin")
@Description("Efectos visuales de cielo para quien tenga el mod VoranixVisualFX instalado.")
public class FxCommand extends BaseCommand {

    private final VisualFxBridge bridge;

    public FxCommand(VisualFxBridge bridge) {
        this.bridge = bridge;
    }

    @Default
    public void onUsage(CommandSender sender) {
        sender.sendMessage(ColorUtils.color("&cUso: &f/fx cielo <color|#rrggbb> [segundos] [fundido] &7o &f/fx parar"));
    }

    @Subcommand("cielo")
    @CommandCompletion("@colores-fx 10 2")
    @Syntax("<color|#rrggbb> [segundos] [fundido]")
    public void onCielo(CommandSender sender, String color, @Optional Double segundos, @Optional Double fundido) {
        int[] rgb = VisualFxBridge.parseColor(color);
        if (rgb == null) {
            sender.sendMessage(ColorUtils.color("&cColor inválido: &f" + color
                    + " &7(" + String.join(", ", VisualFxBridge.COLORES.keySet()) + " o #rrggbb)"));
            return;
        }
        double seg = segundos != null ? segundos : 10.0;
        double fund = fundido != null ? fundido : Math.min(2.0, seg / 3.0);
        bridge.cielo(rgb, seg, fund);
        sender.sendMessage(ColorUtils.color("&aCielo &f" + color + " &apor " + seg + "s enviado a los conectados."));
    }

    @Subcommand("parar")
    public void onParar(CommandSender sender) {
        bridge.detener();
        sender.sendMessage(ColorUtils.color("&aEfecto de cielo detenido."));
    }
}
