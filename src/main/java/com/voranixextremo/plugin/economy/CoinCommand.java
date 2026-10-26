package com.voranixextremo.plugin.economy;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /moneda dar <jugador> <tipo> [cantidad] */
@CommandAlias("moneda")
@CommandPermission("voranixextremo.moneda.admin")
@Description("Entrega monedas físicas a un jugador.")
public class CoinCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;
    private final EconomyManager economyManager;
    private final CoinItemFactory coinItemFactory;

    public CoinCommand(VoranixExtremoPlugin plugin, EconomyManager economyManager, CoinItemFactory coinItemFactory) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.coinItemFactory = coinItemFactory;
    }

    @Default
    @CatchUnknown
    public void onUsage(CommandSender sender) {
        sender.sendMessage("Uso: /moneda dar <jugador> <tipo> [cantidad]");
    }

    @Subcommand("dar")
    @CommandCompletion("@players @monedas @nothing")
    @Syntax("<jugador> <tipo> [cantidad]")
    public void onGive(CommandSender sender, OnlinePlayer target, String tipo, @Default("1") int cantidad) {
        CoinType type = economyManager.getCoinType(tipo);
        if (type == null) {
            sender.sendMessage("Tipo de moneda desconocido. Disponibles: "
                    + String.join(", ", economyManager.getCoinTypes().keySet()));
            return;
        }
        Player player = target.getPlayer();
        player.getInventory().addItem(coinItemFactory.create(type, cantidad));
        sender.sendMessage("Le diste " + cantidad + "x " + type.getId() + " a " + player.getName() + ".");
    }
}
