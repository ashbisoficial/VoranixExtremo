package com.voranixextremo.plugin.worldevents;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.pvp.PvpManager;
import com.voranixextremo.plugin.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Eventos GLOBALES: afectan a todo el servidor, no a un jugador. /ruleta gira y la
 * animación (y el resultado) se muestra a TODOS los jugadores conectados a la vez.
 */
public class WorldEventManager {

    private final VoranixExtremoPlugin plugin;
    private final PvpManager pvpManager;
    private final Map<String, WorldEventOutcome> outcomes = new LinkedHashMap<>();

    private WorldEventOutcome activeEvent;
    private long activeEventEndsAt;
    private boolean pvpStateBeforeWindow;
    private BukkitTask revertTask;

    public WorldEventManager(VoranixExtremoPlugin plugin, PvpManager pvpManager) {
        this.plugin = plugin;
        this.pvpManager = pvpManager;
        load();
    }

    private void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("world-events.outcomes");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection outcomeSection = section.getConfigurationSection(id);
            if (outcomeSection == null) {
                continue;
            }
            try {
                outcomes.put(id.toLowerCase(), WorldEventOutcome.fromConfig(id, outcomeSection));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Evento mundial '" + id + "' mal configurado: " + ex.getMessage());
            }
        }
    }

    public boolean hasOutcomes() {
        return !outcomes.isEmpty();
    }

    public boolean isEventActive() {
        return activeEvent != null;
    }

    public WorldEventOutcome getActiveEvent() {
        return activeEvent;
    }

    public long getActiveEventRemainingSeconds() {
        if (activeEvent == null || activeEventEndsAt == 0) {
            return 0;
        }
        long remaining = activeEventEndsAt - System.currentTimeMillis();
        return remaining <= 0 ? 0 : remaining / 1000;
    }

    /** Null si no hay Luna Roja activa en este momento. Lo usa RedMoonListener. */
    public WorldEventOutcome getActiveRedMoon() {
        return (activeEvent != null && activeEvent.getType() == WorldEventType.RED_MOON) ? activeEvent : null;
    }

    public void spin() {
        if (!hasOutcomes() || isEventActive()) {
            return;
        }
        WorldEventOutcome result = pickWeighted();
        Messages.broadcast(plugin, "world-event-spinning");
        runSpinAnimation(result);
    }

    public boolean forceEnd() {
        if (activeEvent == null) {
            return false;
        }
        if (revertTask != null) {
            revertTask.cancel();
            revertTask = null;
        }
        endEvent();
        return true;
    }

    private WorldEventOutcome pickWeighted() {
        int totalWeight = outcomes.values().stream().mapToInt(WorldEventOutcome::getWeight).sum();
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, totalWeight));
        int cumulative = 0;
        for (WorldEventOutcome outcome : outcomes.values()) {
            cumulative += outcome.getWeight();
            if (roll < cumulative) {
                return outcome;
            }
        }
        return outcomes.values().iterator().next();
    }

    /**
     * Ritmo de los "tics" de la ruleta en ticks de Minecraft (20/seg) entre cada cambio
     * de nombre mostrado: arranca rápido y se va frenando, como una ruleta de verdad
     * perdiendo impulso, hasta la pausa final antes de revelar el resultado.
     */
    private static final int[] SPIN_INTERVALS_TICKS = {
            2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 6, 7, 8, 10, 12, 14, 18
    };

    private void runSpinAnimation(WorldEventOutcome result) {
        List<WorldEventOutcome> all = new ArrayList<>(outcomes.values());
        int totalTicks = 0;
        for (int interval : SPIN_INTERVALS_TICKS) {
            totalTicks += interval;
        }

        BossBar bossBar = Bukkit.createBossBar("", EventCategory.NEUTRAL.getBarColor(), BarStyle.SOLID);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
        bossBar.setProgress(1.0);

        scheduleSpinStep(all, result, 0, 0, totalTicks, bossBar);
    }

    private void scheduleSpinStep(List<WorldEventOutcome> all, WorldEventOutcome result, int stepIndex,
                                   int elapsedTicks, int totalTicks, BossBar bossBar) {
        if (stepIndex >= SPIN_INTERVALS_TICKS.length) {
            bossBar.removeAll();
            showFrameToEveryone(result, true);
            startEvent(result);
            return;
        }

        WorldEventOutcome flash = all.get(ThreadLocalRandom.current().nextInt(all.size()));
        showFrameToEveryone(flash, false);

        int nextElapsed = elapsedTicks + SPIN_INTERVALS_TICKS[stepIndex];
        bossBar.setColor(flash.getCategory().getBarColor());
        bossBar.setTitle(ChatColor.translateAlternateColorCodes('&', flash.getCategory().getChatColorCode() + flash.getDisplayName()));
        bossBar.setProgress(Math.max(0.0, 1.0 - (double) nextElapsed / totalTicks));

        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> scheduleSpinStep(all, result, stepIndex + 1, nextElapsed, totalTicks, bossBar),
                SPIN_INTERVALS_TICKS[stepIndex]);
    }

    private void showFrameToEveryone(WorldEventOutcome outcome, boolean isFinal) {
        String colored = ChatColor.translateAlternateColorCodes('&', outcome.getCategory().getChatColorCode() + outcome.getDisplayName());
        Component component = LegacyComponentSerializer.legacySection().deserialize(colored);
        Title.Times times = isFinal
                ? Title.Times.times(Duration.ZERO, Duration.ofMillis(1600), Duration.ofMillis(500))
                : Title.Times.times(Duration.ZERO, Duration.ofMillis(300), Duration.ZERO);
        Title title = Title.title(component, Component.empty(), times);
        Sound sound = isFinal ? Sound.ENTITY_PLAYER_LEVELUP : Sound.UI_BUTTON_CLICK;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
        }
    }

    private void startEvent(WorldEventOutcome outcome) {
        activeEvent = outcome;
        activeEventEndsAt = outcome.getDurationSeconds() > 0
                ? System.currentTimeMillis() + outcome.getDurationSeconds() * 1000L
                : 0;

        Messages.broadcast(plugin, "world-event-started", "%evento%", outcome.getDisplayName());

        switch (outcome.getType()) {
            case PVP_WINDOW -> {
                pvpStateBeforeWindow = pvpManager.isPvpEnabled();
                if (!pvpStateBeforeWindow) {
                    pvpManager.setPvpEnabled(true);
                }
            }
            case RED_MOON -> {
                for (World world : plugin.getServer().getWorlds()) {
                    world.setTime(18000);
                    world.setStorm(true);
                    world.setThundering(true);
                }
            }
            case COMMAND -> {
                if (outcome.getCommand() != null && !outcome.getCommand().isBlank()) {
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), outcome.getCommand());
                }
            }
        }

        if (outcome.getDurationSeconds() > 0) {
            revertTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::endEvent, outcome.getDurationSeconds() * 20L);
        }
    }

    private void endEvent() {
        if (activeEvent == null) {
            return;
        }
        WorldEventOutcome finished = activeEvent;

        if (finished.getType() == WorldEventType.PVP_WINDOW && !pvpStateBeforeWindow) {
            pvpManager.setPvpEnabled(false);
        }
        if (finished.getType() == WorldEventType.RED_MOON) {
            for (World world : plugin.getServer().getWorlds()) {
                world.setStorm(false);
                world.setThundering(false);
            }
        }

        activeEvent = null;
        activeEventEndsAt = 0;
        revertTask = null;

        Messages.broadcast(plugin, "world-event-ended", "%evento%", finished.getDisplayName());
    }
}
