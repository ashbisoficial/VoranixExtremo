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
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /fx cielo <color|#rrggbb> [segundos] [fundido] [colorCenit] | parar
 * /fx niebla pos1 | pos2 | crear <nombre> [color] [cercana] [lejana] [fundido] | borrar <nombre> | lista
 * Necesita que el jugador tenga instalado el mod cliente VoranixVisualFX (repo aparte, Forge) -
 * a quien no lo tenga no le pasa nada, el comando "funciona" igual (manda los paquetes igual).
 *
 * colorCenit (de "cielo") es opcional y es lo que se ve mirando derecho para arriba: si no se
 * pone, se usa el mismo color (cielo parejo); si se pone uno distinto, queda un degradado.
 *
 * La zona de niebla se arma con dos esquinas, como la selección de WorldEdit: parate en una
 * punta y hacé /fx niebla pos1, parate en la otra y /fx niebla pos2, después /fx niebla crear.
 */
@CommandAlias("fx")
@CommandPermission("voranixextremo.fx.admin")
@Description("Efectos visuales (cielo, niebla por zonas) para quien tenga el mod VoranixVisualFX instalado.")
public class FxCommand extends BaseCommand {

    private final VisualFxBridge bridge;
    private final FogZoneManager zonas;
    private final StormEffect tormenta;

    public FxCommand(VisualFxBridge bridge, FogZoneManager zonas, StormEffect tormenta) {
        this.bridge = bridge;
        this.zonas = zonas;
        this.tormenta = tormenta;
    }

    @Default
    public void onUsage(CommandSender sender) {
        sender.sendMessage(ColorUtils.color("&cUso: &f/fx cielo <color|#rrggbb> [segundos] [fundido] [colorCenit]"));
        sender.sendMessage(ColorUtils.color("&c     &f/fx parar"));
        sender.sendMessage(ColorUtils.color("&c     &f/fx niebla pos1 &7(parado en una esquina de la zona)"));
        sender.sendMessage(ColorUtils.color("&c     &f/fx niebla pos2 &7(parado en la esquina opuesta)"));
        sender.sendMessage(ColorUtils.color("&c     &f/fx niebla crear <nombre> [color] [cercana] [lejana] [fundido]"));
        sender.sendMessage(ColorUtils.color("&c     &f/fx niebla borrar <nombre> &7o &f/fx niebla lista"));
        sender.sendMessage(ColorUtils.color("&c     &f/fx tormenta [segundos] &7(nubes + rayos acá mismo, sin lluvia ni daño)"));
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

    @Subcommand("niebla pos1")
    public void onNieblaPos1(Player jugador) {
        zonas.marcarPos1(jugador);
        jugador.sendMessage(ColorUtils.color("&aPos1 marcada &7(" + formatear(jugador.getLocation()) + ")&a. Ahora pará en la otra esquina y usá &f/fx niebla pos2&a."));
    }

    @Subcommand("niebla pos2")
    public void onNieblaPos2(Player jugador) {
        zonas.marcarPos2(jugador);
        jugador.sendMessage(ColorUtils.color("&aPos2 marcada &7(" + formatear(jugador.getLocation()) + ")&a. Ahora &f/fx niebla crear <nombre>&a."));
    }

    @Subcommand("niebla crear")
    @CommandCompletion("* @colores-fx 2 18 2")
    @Syntax("<nombre> [color] [cercana] [lejana] [fundido]")
    public void onNieblaCrear(Player jugador, String nombre, @Optional String color,
                               @Optional Double cercana, @Optional Double lejana, @Optional Double fundido) {
        int[] rgb = color != null ? VisualFxBridge.parseColor(color) : new int[]{200, 200, 200};
        if (rgb == null) {
            jugador.sendMessage(ColorUtils.color("&cColor inválido: &f" + color
                    + " &7(" + String.join(", ", VisualFxBridge.COLORES.keySet()) + " o #rrggbb)"));
            return;
        }
        float near = cercana != null ? cercana.floatValue() : 2f;
        float far = lejana != null ? lejana.floatValue() : 18f;
        double fund = fundido != null ? fundido : 2.0;

        FogZoneManager.ErrorCrear error = zonas.crear(jugador, nombre, rgb, near, far, fund);
        if (error != null) {
            jugador.sendMessage(ColorUtils.color(switch (error) {
                case FALTA_POS1 -> "&cPrimero marcá la esquina 1: &f/fx niebla pos1";
                case FALTA_POS2 -> "&cFalta la esquina 2: parate en la otra punta y &f/fx niebla pos2";
                case MUNDOS_DISTINTOS -> "&cPos1 y pos2 están en mundos distintos - volvé a marcarlas en el mismo mundo.";
                case YA_EXISTE -> "&cYa existe una zona de niebla llamada &f" + nombre;
            }));
            return;
        }
        Location p1 = zonas.getPos1(jugador), p2 = zonas.getPos2(jugador);
        jugador.sendMessage(ColorUtils.color("&aZona de niebla &f" + nombre + " &acreada entre &7("
                + formatear(p1) + ")&a y &7(" + formatear(p2) + ")&a."));
    }

    @Subcommand("niebla borrar")
    @Syntax("<nombre>")
    public void onNieblaBorrar(CommandSender sender, String nombre) {
        if (zonas.borrar(nombre)) {
            sender.sendMessage(ColorUtils.color("&aZona de niebla &f" + nombre + " &aborrada."));
        } else {
            sender.sendMessage(ColorUtils.color("&cNo existe una zona de niebla llamada &f" + nombre));
        }
    }

    @Subcommand("niebla lista")
    public void onNieblaLista(CommandSender sender) {
        if (zonas.listar().isEmpty()) {
            sender.sendMessage(ColorUtils.color("&7No hay zonas de niebla creadas."));
            return;
        }
        sender.sendMessage(ColorUtils.color("&6Zonas de niebla:"));
        for (FogZoneManager.Zona zona : zonas.listar()) {
            sender.sendMessage(ColorUtils.color(String.format("&7- &f%s &7(%s, %.0f-%.0f bloques de niebla)",
                    zona.nombre(), zona.mundo(), zona.cercana(), zona.lejana())));
        }
    }

    @Subcommand("tormenta")
    @CommandCompletion("15")
    @Syntax("[segundos]")
    public void onTormenta(Player jugador, @Optional Integer segundos) {
        int seg = segundos != null ? segundos : 15;
        tormenta.disparar(jugador.getLocation(), seg);
        jugador.sendMessage(ColorUtils.color("&aTormenta &7(nubes + rayos, sin lluvia) &adisparada acá por " + seg + "s."));
    }

    private static String formatear(Location loc) {
        return String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ());
    }
}
