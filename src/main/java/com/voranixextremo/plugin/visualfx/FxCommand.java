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
 * /fx cielo <color|#rrggbb> [segundos] [fundido] [colorCenit] | parar
 * Necesita que el jugador tenga instalado el mod cliente VoranixVisualFX (repo aparte, Forge) -
 * a quien no lo tenga no le pasa nada, el comando "funciona" igual (manda el paquete a todos).
 *
 * colorCenit es opcional y es lo que se ve mirando derecho para arriba: si no se pone, se usa el
 * mismo color (cielo parejo); si se pone uno distinto, queda un degradado entre los dos.
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
        sender.sendMessage(ColorUtils.color("&cUso: &f/fx cielo <color|#rrggbb> [segundos] [fundido] [colorCenit] &7o &f/fx parar"));
    }

    @Subcommand("cielo")
    @CommandCompletion("@colores-fx 10 2 @colores-fx")
    @Syntax("<color|#rrggbb> [segundos] [fundido] [colorCenit]")
    public void onCielo(CommandSender sender, String color, @Optional Double segundos, @Optional Double fundido,
                         @Optional String colorCenit) {
        int[] horizonte = VisualFxBridge.parseColor(color);
        if (horizonte == null) {
            sender.sendMessage(ColorUtils.color("&cColor inválido: &f" + color
                    + " &7(" + String.join(", ", VisualFxBridge.COLORES.keySet()) + " o #rrggbb)"));
            return;
        }
        int[] cenit = null;
        if (colorCenit != null) {
            cenit = VisualFxBridge.parseColor(colorCenit);
            if (cenit == null) {
                sender.sendMessage(ColorUtils.color("&cColor de cenit inválido: &f" + colorCenit
                        + " &7(" + String.join(", ", VisualFxBridge.COLORES.keySet()) + " o #rrggbb)"));
                return;
            }
        }
        double seg = segundos != null ? segundos : 10.0;
        double fund = fundido != null ? fundido : Math.min(2.0, seg / 3.0);
        bridge.cielo(horizonte, cenit, seg, fund);
        String detalle = colorCenit != null ? color + " &7(horizonte) &a/ &f" + colorCenit + " &7(cenit)" : color;
        sender.sendMessage(ColorUtils.color("&aCielo &f" + detalle + " &apor " + seg + "s enviado a los conectados."));
    }

    @Subcommand("parar")
    public void onParar(CommandSender sender) {
        bridge.detener();
        sender.sendMessage(ColorUtils.color("&aEfecto de cielo detenido."));
    }
}
