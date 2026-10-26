package com.voranixextremo.plugin.food;

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
import org.bukkit.inventory.ItemStack;

/** /comida dar <jugador> <id> [cantidad] — el id puede ser una comida o una bebida. */
@CommandAlias("comida")
@CommandPermission("voranixextremo.comida.admin")
@Description("Entrega comida o bebidas a un jugador.")
public class FoodCommand extends BaseCommand {

    private final VoranixExtremoPlugin plugin;
    private final FoodManager foodManager;

    public FoodCommand(VoranixExtremoPlugin plugin, FoodManager foodManager) {
        this.plugin = plugin;
        this.foodManager = foodManager;
    }

    @Default
    @CatchUnknown
    public void onUsage(CommandSender sender) {
        sender.sendMessage("Uso: /comida dar <jugador> <id> [cantidad]");
    }

    @Subcommand("dar")
    @CommandCompletion("@players @comidas @nothing")
    @Syntax("<jugador> <id> [cantidad]")
    public void onGive(CommandSender sender, OnlinePlayer target, String id, @Default("1") int cantidad) {
        Player player = target.getPlayer();
        int amount = Math.max(1, cantidad);

        FoodType food = foodManager.getFoodType(id);
        if (food != null) {
            ItemStack item = foodManager.createFoodItem(food);
            item.setAmount(amount);
            player.getInventory().addItem(item);
            sender.sendMessage("Le diste " + amount + "x " + food.getId() + " a " + player.getName() + ".");
            return;
        }

        DrinkType drink = foodManager.getDrinkType(id);
        if (drink != null) {
            for (int i = 0; i < amount; i++) {
                player.getInventory().addItem(foodManager.createDrinkItem(drink));
            }
            sender.sendMessage("Le diste " + amount + "x " + drink.getId() + " a " + player.getName() + ".");
            return;
        }

        sender.sendMessage("Comida/bebida desconocida. Disponibles: "
                + String.join(", ", foodManager.getFoodTypes().keySet())
                + (foodManager.getDrinkTypes().isEmpty() ? "" : ", " + String.join(", ", foodManager.getDrinkTypes().keySet())));
    }
}
