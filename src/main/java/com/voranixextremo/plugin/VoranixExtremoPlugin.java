package com.voranixextremo.plugin;

import co.aikar.commands.BukkitCommandCompletionContext;
import co.aikar.commands.BukkitCommandManager;
import co.aikar.commands.CommandCompletions;
import co.aikar.commands.MessageKeys;
import co.aikar.commands.MinecraftMessageKeys;
import com.github.retrooper.packetevents.PacketEvents;
import com.voranixextremo.plugin.chatsystem.Chat;
import com.voranixextremo.plugin.teamsystem.TeamCommand;
import com.voranixextremo.plugin.teamsystem.TeamManager;
import com.voranixextremo.plugin.teamsystem.TeamNametag;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
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
import com.voranixextremo.plugin.ruleta.RuletaCommand;
import com.voranixextremo.plugin.ruleta.RuletaManager;
import com.voranixextremo.plugin.ruleta.RuletaPackManager;
import com.voranixextremo.plugin.shops.ChefCommand;
import com.voranixextremo.plugin.shops.ChefVillagerManager;
import com.voranixextremo.plugin.shops.ShopManager;
import com.voranixextremo.plugin.totem.TotemPop;
import com.voranixextremo.plugin.util.Messages;
import com.voranixextremo.plugin.util.ResourcePackListener;
import com.voranixextremo.plugin.voranixextremo.VoranixExtremoCommand;
import com.voranixextremo.plugin.worldevents.RedMoonListener;
import com.voranixextremo.plugin.worldevents.WorldEventCommand;
import com.voranixextremo.plugin.worldevents.WorldEventManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VoranixExtremoPlugin extends JavaPlugin {

    private LivesManager livesManager;
    private PvpManager pvpManager;
    private EconomyManager economyManager;
    private RuletaManager ruletaManager;
    private RuletaPackManager ruletaPackManager;
    private FoodManager foodManager;
    private ShopManager shopManager;
    private WorldEventManager worldEventManager;
    private Chat chat;
    private TeamManager teamManager;
    private TeamNametag teamNametag;
    private BukkitCommandManager commandManager;

    @Override
    public void onLoad() {
        // PacketEvents (metido dentro del jar) se prepara en onLoad, antes que el resto
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        // reEncodeByDefault(false): un paquete que solo se lee no se vuelve a escribir. Si no, cualquier
        // entidad que PacketEvents no reconozca al leerla rompía la conexión del jugador al reescribirla.
        PacketEvents.getAPI().getSettings().checkForUpdates(false).bStats(false).reEncodeByDefault(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PacketEvents.getAPI().init();

        this.livesManager = new LivesManager(this);
        this.pvpManager = new PvpManager(this);
        this.economyManager = new EconomyManager(this);
        DeathEffects deathEffects = new DeathEffects(this);
        CoinItemFactory coinItemFactory = new CoinItemFactory(this);
        BankService bankService = new BankService(economyManager, coinItemFactory);
        PvpKillRewards pvpKillRewards = new PvpKillRewards(this, bankService);
        this.foodManager = new FoodManager(this);
        this.shopManager = new ShopManager(this);
        ChefVillagerManager chefVillagerManager = new ChefVillagerManager(this, economyManager, coinItemFactory, foodManager);
        this.worldEventManager = new WorldEventManager(this, pvpManager, livesManager);

        getServer().getPluginManager().registerEvents(
                new PlayerDeathListener(this, livesManager, deathEffects, pvpKillRewards), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(livesManager), this);
        getServer().getPluginManager().registerEvents(new PvpListener(pvpManager), this);
        getServer().getPluginManager().registerEvents(new CoinProtectionListener(coinItemFactory), this);
        getServer().getPluginManager().registerEvents(new ResourcePackListener(this), this);
        getServer().getPluginManager().registerEvents(new FoodConsumeListener(this, foodManager), this);
        getServer().getPluginManager().registerEvents(new RedMoonListener(worldEventManager), this);
        getServer().getPluginManager().registerEvents(new TotemPop(this), this);
        this.chat = new Chat(this);
        getServer().getPluginManager().registerEvents(chat, this);

        // Teams: datos y config (team.yml), el nombre bajo el nametag y el chat del team
        this.teamManager = new TeamManager(this);
        this.teamNametag = new TeamNametag(this, teamManager);
        teamManager.setAlCambiar(teamNametag::actualizar);
        teamNametag.start();
        chat.setTeams(teamManager);

        this.ruletaManager = new RuletaManager(this);
        this.ruletaPackManager = new RuletaPackManager(this, ruletaManager);
        ruletaPackManager.start();
        getServer().getPluginManager().registerEvents(ruletaPackManager, this);

        // Comandos con ACF: cada clase *Command declara su comando con anotaciones
        this.commandManager = new BukkitCommandManager(this);
        configurarMensajesComandos();
        CommandCompletions<BukkitCommandCompletionContext> completions = commandManager.getCommandCompletions();
        completions.registerCompletion("monedas", c -> economyManager.getCoinTypes().keySet());
        completions.registerCompletion("comidas", c -> {
            List<String> ids = new ArrayList<>(foodManager.getFoodTypes().keySet());
            ids.addAll(foodManager.getDrinkTypes().keySet());
            return ids;
        });
        completions.registerCompletion("tiendas", c -> shopManager.getShopTypes().keySet());
        completions.registerCompletion("colores-ruleta", c -> {
            List<String> colores = new ArrayList<>(ruletaManager.coloresEnRuleta());
            colores.add("random");
            return colores;
        });
        commandManager.registerCommand(new LivesCommand(this, livesManager));
        commandManager.registerCommand(new PvpCommand(this, pvpManager));
        commandManager.registerCommand(new EconomyCommand(this, economyManager));
        commandManager.registerCommand(new BankCommand(this, bankService));
        commandManager.registerCommand(new CoinCommand(this, economyManager, coinItemFactory));
        commandManager.registerCommand(new FoodCommand(this, foodManager));
        commandManager.registerCommand(new ChefCommand(this, shopManager, chefVillagerManager));
        commandManager.registerCommand(new WorldEventCommand(this, worldEventManager));
        commandManager.registerCommand(new RuletaCommand(this, ruletaManager, ruletaPackManager));
        commandManager.registerCommand(new VoranixExtremoCommand(this));
        completions.registerCompletion("teams", c -> teamManager.getNombres());
        commandManager.registerCommand(new TeamCommand(teamManager));

        registerVaultIfPresent();

        getLogger().info("VoranixExtremo habilitado. Vidas máximas: " + livesManager.getMaxLives()
                + " | PVP inicial: " + (pvpManager.isPvpEnabled() ? "activado" : "desactivado")
                + " | Monedas: " + economyManager.getCoinTypes().size());
    }

    /**
     * ACF en español, y los errores comunes (sin permiso, solo jugadores, jugador no encontrado)
     * con los mismos mensajes de config.yml → messages que usaba el plugin antes.
     */
    private void configurarMensajesComandos() {
        Locale es = new Locale("es");
        commandManager.getLocales().setDefaultLocale(es);
        commandManager.getLocales().addMessage(es, MessageKeys.ERROR_PREFIX, "{message}");
        commandManager.getLocales().addMessage(es, MessageKeys.PERMISSION_DENIED, Messages.get(this, "no-permission"));
        commandManager.getLocales().addMessage(es, MessageKeys.PERMISSION_DENIED_PARAMETER, Messages.get(this, "no-permission"));
        commandManager.getLocales().addMessage(es, MessageKeys.NOT_ALLOWED_ON_CONSOLE, Messages.get(this, "only-players"));
        String noEncontrado = Messages.get(this, "player-not-found");
        commandManager.getLocales().addMessage(es, MinecraftMessageKeys.NO_PLAYER_FOUND, noEncontrado);
        commandManager.getLocales().addMessage(es, MinecraftMessageKeys.NO_PLAYER_FOUND_OFFLINE, noEncontrado);
        commandManager.getLocales().addMessage(es, MinecraftMessageKeys.NO_PLAYER_FOUND_SERVER, noEncontrado);
    }

    /**
     * /voranixextremo reload: vuelve a leer config.yml y se lo pasa a todo lo que lo guarda al arrancar.
     * Lo demás (mensajes, efectos de muerte, recompensas PVP, tótem) ya lee la config en el momento.
     * Las vidas y los balances de los jugadores son datos guardados aparte: no se tocan.
     */
    public void recargar() {
        reloadConfig();
        livesManager.reloadSettings();
        economyManager.reloadSettings();
        foodManager.reload();
        shopManager.reload();
        worldEventManager.reload();
        ruletaManager.load();
        ruletaPackManager.stop();
        ruletaPackManager.start();
        configurarMensajesComandos();
        chat.load();
        teamManager.reloadConfig();
        teamNametag.refrescarTodo();
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
        if (commandManager != null) {
            commandManager.unregisterCommands();
        }
        if (livesManager != null) {
            livesManager.save();
        }
        if (economyManager != null) {
            economyManager.save();
        }
        if (ruletaManager != null) {
            ruletaManager.close();
        }
        if (ruletaPackManager != null) {
            ruletaPackManager.stop();
        }
        if (teamNametag != null) {
            teamNametag.stop();
        }
        if (teamManager != null) {
            teamManager.save();
        }
        PacketEvents.getAPI().terminate();
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
