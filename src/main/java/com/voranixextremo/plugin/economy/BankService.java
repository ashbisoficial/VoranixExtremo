package com.voranixextremo.plugin.economy;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Convierte entre monedas físicas (ítems en el inventario) y el balance guardado.
 */
public class BankService {

    private final EconomyManager economyManager;
    private final CoinItemFactory coinItemFactory;

    public BankService(EconomyManager economyManager, CoinItemFactory coinItemFactory) {
        this.economyManager = economyManager;
        this.coinItemFactory = coinItemFactory;
    }

    /**
     * Deposita todas las monedas físicas del inventario del jugador en su balance.
     *
     * @return el valor total depositado.
     */
    public double depositAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        double total = 0;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            String coinId = coinItemFactory.getCoinId(item);
            if (coinId == null) {
                continue;
            }
            CoinType type = economyManager.getCoinType(coinId);
            if (type == null) {
                continue;
            }
            total += type.getValue() * item.getAmount();
            contents[slot] = null;
        }

        if (total > 0) {
            inventory.setStorageContents(contents);
            economyManager.deposit(player, total);
        }
        return total;
    }

    /**
     * Retira del balance del jugador el monto indicado, entregándole monedas físicas
     * (usa las denominaciones configuradas de mayor a menor, como dar cambio).
     *
     * @return true si se pudo armar el monto exacto y entregarlo.
     */
    public boolean withdraw(Player player, double amount) {
        if (amount <= 0 || !economyManager.has(player, amount)) {
            return false;
        }

        Optional<List<ItemStack>> coins = breakIntoCoins(amount);
        if (coins.isEmpty()) {
            // El monto no se puede armar exactamente con las denominaciones configuradas.
            return false;
        }

        for (ItemStack item : coins.get()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        economyManager.withdraw(player, amount);
        return true;
    }

    /**
     * Convierte un monto a la menor cantidad de monedas físicas posible, usando las
     * denominaciones configuradas (de mayor a menor valor), sin tocar ningún balance.
     *
     * @return la lista de ítems de moneda, o vacío si el monto no se puede armar exacto.
     */
    public Optional<List<ItemStack>> breakIntoCoins(double amount) {
        if (amount <= 0) {
            return Optional.of(List.of());
        }

        List<CoinType> sorted = new ArrayList<>(economyManager.getCoinTypes().values());
        sorted.sort(Comparator.comparingDouble(CoinType::getValue).reversed());

        double remaining = amount;
        List<ItemStack> result = new ArrayList<>();

        for (CoinType type : sorted) {
            if (type.getValue() <= 0) {
                continue;
            }
            int count = (int) ((remaining + 1e-6) / type.getValue());
            if (count <= 0) {
                continue;
            }
            result.add(coinItemFactory.create(type, count));
            remaining -= count * type.getValue();
        }

        return remaining > 1e-4 ? Optional.empty() : Optional.of(result);
    }
}
