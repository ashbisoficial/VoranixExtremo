package com.voranixextremo.plugin.food;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /comida dar <jugador> <id> [cantidad] — entrega una comida o bebida (admin).
 */
public class FoodCommand implements CommandExecutor, TabCompleter {

    private final VoranixExtremoPlugin plugin;
    private final FoodManager foodManager;

    public FoodCommand(VoranixExtremoPlugin plugin, FoodManager foodManager) {
        this.plugin = plugin;
        this.foodManager = foodManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voranixextremo.comida.admin")) {
            Messages.send(sender, plugin, "no-permission");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("dar")) {
            sender.sendMessage("Uso: /comida dar <jugador> <id> [cantidad]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            Messages.send(sender, plugin, "player-not-found");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("Uso: /comida dar <jugador> <id> [cantidad]");
            return true;
        }

        String id = args[2];
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("La cantidad debe ser un número entero.");
                return true;
            }
        }

        FoodType food = foodManager.getFoodType(id);
        if (food != null) {
            ItemStack item = foodManager.createFoodItem(food);
            item.setAmount(Math.max(1, amount));
            target.getInventory().addItem(item);
            sender.sendMessage("Le diste " + amount + "x " + food.getId() + " a " + target.getName() + ".");
            return true;
        }

        DrinkType drink = foodManager.getDrinkType(id);
        if (drink != null) {
            for (int i = 0; i < Math.max(1, amount); i++) {
                target.getInventory().addItem(foodManager.createDrinkItem(drink));
            }
            sender.sendMessage("Le diste " + amount + "x " + drink.getId() + " a " + target.getName() + ".");
            return true;
        }

        sender.sendMessage("Comida/bebida desconocida. Disponibles: "
                + String.join(", ", foodManager.getFoodTypes().keySet())
                + (foodManager.getDrinkTypes().isEmpty() ? "" : ", " + String.join(", ", foodManager.getDrinkTypes().keySet())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("dar");
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return names;
        }
        if (args.length == 3) {
            List<String> ids = new ArrayList<>(foodManager.getFoodTypes().keySet());
            ids.addAll(foodManager.getDrinkTypes().keySet());
            return ids;
        }
        return List.of();
    }
}
