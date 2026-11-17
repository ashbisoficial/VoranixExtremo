package com.voranixextremo.plugin.economy;

import com.voranixextremo.plugin.util.Messages;
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
        Messages.send(sender, "coin-usage");
    }

    @Subcommand("dar")
    @CommandCompletion("@players @monedas @nothing")
    @Syntax("<jugador> <tipo> [cantidad]")
    public void onGive(CommandSender sender, OnlinePlayer target, String tipo, @Default("1") int cantidad) {
        CoinType type = economyManager.getCoinType(tipo);
        if (type == null) {
            Messages.send(sender, "coin-unknown", "%disponibles%", String.join(", ", economyManager.getCoinTypes().keySet()));
            return;
        }
        Player player = target.getPlayer();
        player.getInventory().addItem(coinItemFactory.create(type, cantidad));
        Messages.send(sender, "coin-given", "%cantidad%", String.valueOf(cantidad), "%tipo%", type.getId(), "%player%", player.getName());
    }
}
