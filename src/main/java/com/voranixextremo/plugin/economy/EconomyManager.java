package com.voranixextremo.plugin.economy;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class EconomyManager {

    private final VoranixExtremoPlugin plugin;
    private final File dataFile;
    private final YamlConfiguration dataConfig;
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<String, CoinType> coinTypes = new LinkedHashMap<>();
    private String currencySingular;
    private String currencyPlural;

    public EconomyManager(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "economy.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        reloadSettings();
        loadBalances();
    }

    /** Vuelve a leer el nombre de la moneda y economy.coins (los balances guardados no se tocan). */
    public void reloadSettings() {
        this.currencySingular = plugin.getConfig().getString("economy.currency.singular", "Moneda");
        this.currencyPlural = plugin.getConfig().getString("economy.currency.plural", "Monedas");
        coinTypes.clear();
        loadCoinTypes();
    }

    private void loadCoinTypes() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("economy.coins");
        if (section == null) {
            plugin.getLogger().warning("No hay monedas configuradas en economy.coins");
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection coinSection = section.getConfigurationSection(id);
            if (coinSection == null) {
                continue;
            }
            try {
                coinTypes.put(id.toLowerCase(), CoinType.fromConfig(id, coinSection));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Moneda '" + id + "' mal configurada: " + ex.getMessage());
            }
        }
    }

    private void loadBalances() {
        for (String key : dataConfig.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), dataConfig.getDouble(key));
            } catch (IllegalArgumentException ignored) {
                // Clave corrupta o que no es un UUID: se ignora.
            }
        }
    }

    public void save() {
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            dataConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo guardar economy.yml", e);
        }
    }

    public Map<String, CoinType> getCoinTypes() {
        return Collections.unmodifiableMap(coinTypes);
    }

    public CoinType getCoinType(String id) {
        return id == null ? null : coinTypes.get(id.toLowerCase());
    }

    public String getCurrencySingular() {
        return currencySingular;
    }

    public String getCurrencyPlural() {
        return currencyPlural;
    }

    public double getBalance(OfflinePlayer player) {
        return balances.getOrDefault(player.getUniqueId(), 0.0);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    public void setBalance(OfflinePlayer player, double amount) {
        balances.put(player.getUniqueId(), Math.max(0, amount));
    }

    public void deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return;
        }
        setBalance(player, getBalance(player) + amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        double balance = getBalance(player);
        if (balance < amount) {
            return false;
        }
        setBalance(player, balance - amount);
        return true;
    }
}
