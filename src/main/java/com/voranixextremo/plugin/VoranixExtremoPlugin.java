package com.voranixextremo.plugin;

import com.voranixextremo.plugin.economy.BankCommand;
import com.voranixextremo.plugin.economy.BankService;
import com.voranixextremo.plugin.economy.CoinCommand;
import com.voranixextremo.plugin.economy.CoinItemFactory;
import com.voranixextremo.plugin.economy.CoinProtectionListener;
import com.voranixextremo.plugin.economy.EconomyCommand;
import com.voranixextremo.plugin.economy.EconomyManager;
import com.voranixextremo.plugin.economy.VaultEconomyProvider;
import com.voranixextremo.plugin.food.FoodCommand;
import com.voranixextremo.plugin.food.FoodConsumeListener;
import com.voranixextremo.plugin.food.FoodManager;
import com.voranixextremo.plugin.kills.PvpKillRewards;
import com.voranixextremo.plugin.lives.DeathEffects;
import com.voranixextremo.plugin.lives.LivesCommand;
import com.voranixextremo.plugin.lives.LivesManager;
import com.voranixextremo.plugin.lives.PlayerDeathListener;
import com.voranixextremo.plugin.lives.PlayerJoinListener;
import com.voranixextremo.plugin.pvp.PvpCommand;
import com.voranixextremo.plugin.pvp.PvpListener;
import com.voranixextremo.plugin.pvp.PvpManager;
import com.voranixextremo.plugin.roulette.RouletteCommand;
import com.voranixextremo.plugin.roulette.RouletteManager;
import com.voranixextremo.plugin.shops.ChefCommand;
import com.voranixextremo.plugin.shops.ChefVillagerManager;
import com.voranixextremo.plugin.shops.ShopManager;
import com.voranixextremo.plugin.util.ResourcePackListener;
import com.voranixextremo.plugin.worldevents.RedMoonListener;
import com.voranixextremo.plugin.worldevents.WorldEventCommand;
import com.voranixextremo.plugin.worldevents.WorldEventManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoranixExtremoPlugin extends JavaPlugin {

    private LivesManager livesManager;
    private PvpManager pvpManager;
    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.livesManager = new LivesManager(this);
        this.pvpManager = new PvpManager(this);
        this.economyManager = new EconomyManager(this);
        DeathEffects deathEffects = new DeathEffects(this);
        CoinItemFactory coinItemFactory = new CoinItemFactory(this);
        BankService bankService = new BankService(economyManager, coinItemFactory);
        PvpKillRewards pvpKillRewards = new PvpKillRewards(this, bankService);
        FoodManager foodManager = new FoodManager(this);
        ShopManager shopManager = new ShopManager(this);
        ChefVillagerManager chefVillagerManager = new ChefVillagerManager(this, economyManager, coinItemFactory, foodManager);
        RouletteManager rouletteManager = new RouletteManager(this, economyManager, livesManager);
        WorldEventManager worldEventManager = new WorldEventManager(this, pvpManager);

        getServer().getPluginManager().registerEvents(
                new PlayerDeathListener(this, livesManager, deathEffects, pvpKillRewards), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(livesManager), this);
        getServer().getPluginManager().registerEvents(new PvpListener(pvpManager), this);
        getServer().getPluginManager().registerEvents(new CoinProtectionListener(coinItemFactory), this);
        getServer().getPluginManager().registerEvents(new ResourcePackListener(this), this);
        getServer().getPluginManager().registerEvents(new FoodConsumeListener(this, foodManager), this);
        getServer().getPluginManager().registerEvents(new RedMoonListener(worldEventManager), this);

        LivesCommand livesCommand = new LivesCommand(this, livesManager);
        getCommand("vidas").setExecutor(livesCommand);
        getCommand("vidas").setTabCompleter(livesCommand);

        PvpCommand pvpCommand = new PvpCommand(this, pvpManager);
        getCommand("pvp").setExecutor(pvpCommand);
        getCommand("pvp").setTabCompleter(pvpCommand);

        getCommand("dinero").setExecutor(new EconomyCommand(this, economyManager));
        getCommand("banco").setExecutor(new BankCommand(this, bankService));

        CoinCommand coinCommand = new CoinCommand(this, economyManager, coinItemFactory);
        getCommand("moneda").setExecutor(coinCommand);
        getCommand("moneda").setTabCompleter(coinCommand);

        FoodCommand foodCommand = new FoodCommand(this, foodManager);
        getCommand("comida").setExecutor(foodCommand);
        getCommand("comida").setTabCompleter(foodCommand);

        ChefCommand chefCommand = new ChefCommand(this, shopManager, chefVillagerManager);
        getCommand("chef").setExecutor(chefCommand);
        getCommand("chef").setTabCompleter(chefCommand);

        RouletteCommand rouletteCommand = new RouletteCommand(this, rouletteManager);
        getCommand("ruleta").setExecutor(rouletteCommand);
        getCommand("ruleta").setTabCompleter(rouletteCommand);

        getCommand("evento").setExecutor(new WorldEventCommand(this, worldEventManager));

        registerVaultIfPresent();

        getLogger().info("VoranixExtremo habilitado. Vidas máximas: " + livesManager.getMaxLives()
                + " | PVP inicial: " + (pvpManager.isPvpEnabled() ? "activado" : "desactivado")
                + " | Monedas: " + economyManager.getCoinTypes().size());
    }

    private void registerVaultIfPresent() {
        if (!getConfig().getBoolean("economy.vault-integration", true)) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault no está instalado: la economía funciona solo con los comandos propios.");
            return;
        }
        try {
            getServer().getServicesManager().register(Economy.class, new VaultEconomyProvider(economyManager), this, ServicePriority.Normal);
            getLogger().info("Integración con Vault activada.");
        } catch (Throwable t) {
            getLogger().warning("No se pudo registrar la economía en Vault: " + t.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (livesManager != null) {
            livesManager.save();
        }
        if (economyManager != null) {
            economyManager.save();
        }
    }

    public LivesManager getLivesManager() {
        return livesManager;
    }

    public PvpManager getPvpManager() {
        return pvpManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}
