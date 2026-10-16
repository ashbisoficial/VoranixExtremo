package com.voranixextremo.plugin;

import com.voranixextremo.plugin.lives.DeathEffects;
import com.voranixextremo.plugin.lives.LivesCommand;
import com.voranixextremo.plugin.lives.LivesManager;
import com.voranixextremo.plugin.lives.PlayerDeathListener;
import com.voranixextremo.plugin.lives.PlayerJoinListener;
import com.voranixextremo.plugin.pvp.PvpCommand;
import com.voranixextremo.plugin.pvp.PvpListener;
import com.voranixextremo.plugin.pvp.PvpManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoranixExtremoPlugin extends JavaPlugin {

    private LivesManager livesManager;
    private PvpManager pvpManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.livesManager = new LivesManager(this);
        this.pvpManager = new PvpManager(this);
        DeathEffects deathEffects = new DeathEffects(this);

        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this, livesManager, deathEffects), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(livesManager), this);
        getServer().getPluginManager().registerEvents(new PvpListener(pvpManager), this);

        LivesCommand livesCommand = new LivesCommand(this, livesManager);
        getCommand("vidas").setExecutor(livesCommand);
        getCommand("vidas").setTabCompleter(livesCommand);

        PvpCommand pvpCommand = new PvpCommand(this, pvpManager);
        getCommand("pvp").setExecutor(pvpCommand);
        getCommand("pvp").setTabCompleter(pvpCommand);

        getLogger().info("VoranixExtremo habilitado. Vidas máximas: " + livesManager.getMaxLives()
                + " | PVP inicial: " + (pvpManager.isPvpEnabled() ? "activado" : "desactivado"));
    }

    @Override
    public void onDisable() {
        if (livesManager != null) {
            livesManager.save();
        }
    }

    public LivesManager getLivesManager() {
        return livesManager;
    }

    public PvpManager getPvpManager() {
        return pvpManager;
    }
}
