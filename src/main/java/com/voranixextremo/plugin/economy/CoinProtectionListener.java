package com.voranixextremo.plugin.economy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Las monedas usan materiales vanilla (lingotes, etc.) con un custom-model-data encima,
 * así que sin esto podrían colarse en recetas de crafteo, como combustible de horno o
 * en el yunque/mesa de herrería. Esto bloquea esos usos para que no se puedan "gastar"
 * por accidente ni usar para duplicar/exploitar recetas.
 */
public class CoinProtectionListener implements Listener {

    private final CoinItemFactory coinItemFactory;

    public CoinProtectionListener(CoinItemFactory coinItemFactory) {
        this.coinItemFactory = coinItemFactory;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isCoin(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (isCoin(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (isCoin(event.getInventory().getItem(0)) || isCoin(event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (isCoin(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    private boolean isCoin(ItemStack item) {
        return coinItemFactory.getCoinId(item) != null;
    }
}
