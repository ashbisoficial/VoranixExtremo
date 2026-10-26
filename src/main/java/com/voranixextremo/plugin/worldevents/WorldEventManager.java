package com.voranixextremo.plugin.worldevents;

import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.lives.LivesManager;
import com.voranixextremo.plugin.pvp.PvpManager;
import com.voranixextremo.plugin.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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
    private final LivesManager livesManager;
    private final Map<String, WorldEventOutcome> outcomes = new LinkedHashMap<>();

    private WorldEventOutcome activeEvent;
    private long activeEventEndsAt;
    private boolean pvpStateBeforeWindow;
    private BukkitTask revertTask;
    private BukkitTask meteorTask;
    private BukkitTask countdownTask;
    private BossBar revillCountdownBar;

    public WorldEventManager(VoranixExtremoPlugin plugin, PvpManager pvpManager, LivesManager livesManager) {
        this.plugin = plugin;
        this.pvpManager = pvpManager;
        this.livesManager = livesManager;
        load();
    }

    /** Vuelve a leer world-events.outcomes. Si hay un evento activo, sigue hasta terminar como estaba. */
    public void reload() {
        outcomes.clear();
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
        bossBar.setTitle(ColorUtils.color(flash.getCategory().getChatColorCode() + flash.getDisplayName()));
        bossBar.setProgress(Math.max(0.0, 1.0 - (double) nextElapsed / totalTicks));

        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> scheduleSpinStep(all, result, stepIndex + 1, nextElapsed, totalTicks, bossBar),
                SPIN_INTERVALS_TICKS[stepIndex]);
    }

    private void showFrameToEveryone(WorldEventOutcome outcome, boolean isFinal) {
        String colored = ColorUtils.color(outcome.getCategory().getChatColorCode() + outcome.getDisplayName());
        // tiempos en ticks: el resultado queda 1,6 s y se va en 0,5 s; cada "tic" dura 0,3 s
        int stay = isFinal ? 32 : 6, fadeOut = isFinal ? 10 : 0;
        Sound sound = isFinal ? Sound.ENTITY_PLAYER_LEVELUP : Sound.UI_BUTTON_CLICK;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(colored, "", 0, stay, fadeOut);
            player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
            spawnSpinAura(player, outcome.getCategory(), isFinal);
        }
    }

    /** Aro de partículas de color alrededor de cada jugador en cada "tic" de la ruleta —
     * del color de la categoría que está pasando. En el resultado final es más grande y
     * suma un estallido de partículas de tótem, como remate. */
    private void spawnSpinAura(Player player, EventCategory category, boolean isFinal) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.2, 0);
        Particle.DustOptions dustOptions = new Particle.DustOptions(category.getParticleColor(), isFinal ? 2.0F : 1.2F);

        int points = isFinal ? 24 : 10;
        double radius = isFinal ? 1.4 : 0.9;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            world.spawnParticle(Particle.REDSTONE, point, 1, 0, 0, 0, 0, dustOptions);
        }

        if (isFinal) {
            world.spawnParticle(Particle.TOTEM, center, 40, 0.5, 0.8, 0.5, 0.3);
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
                }
                setStormyWeather(true);
            }
            case COMMAND -> {
                if (outcome.getCommand() != null && !outcome.getCommand().isBlank()) {
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), outcome.getCommand());
                }
            }
            case REVILL -> {
                setStormyWeather(true);
                announceRevillStart(outcome);
                startRevillCountdown(outcome);
                startMeteorShower(outcome);
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
            setStormyWeather(false);
        }
        if (finished.getType() == WorldEventType.REVILL) {
            if (countdownTask != null) {
                countdownTask.cancel();
                countdownTask = null;
            }
            if (meteorTask != null) {
                meteorTask.cancel();
                meteorTask = null;
            }
            if (revillCountdownBar != null) {
                revillCountdownBar.removeAll();
                revillCountdownBar = null;
            }
            setStormyWeather(false);
            resolveRevill(finished);
        }

        activeEvent = null;
        activeEventEndsAt = 0;
        revertTask = null;

        Messages.broadcast(plugin, "world-event-ended", "%evento%", finished.getDisplayName());
    }

    private void setStormyWeather(boolean stormy) {
        for (World world : plugin.getServer().getWorlds()) {
            world.setStorm(stormy);
            world.setThundering(stormy);
        }
    }

    private void announceRevillStart(WorldEventOutcome outcome) {
        Messages.broadcast(plugin, "revill-objective", "%objetivo%", outcome.getObjectiveDescription());

        String titleText = ColorUtils.color("&4&l¡MOMENTO REVILL!");
        String subtitleText = ColorUtils.color("&c" + outcome.getObjectiveDescription());

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(titleText, subtitleText, 6, 60, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0F, 1.0F);
        }
    }

    private void startRevillCountdown(WorldEventOutcome outcome) {
        revillCountdownBar = Bukkit.createBossBar("", EventCategory.PELIGRO.getBarColor(), BarStyle.SOLID);
        for (Player player : Bukkit.getOnlinePlayers()) {
            revillCountdownBar.addPlayer(player);
        }

        int totalSeconds = Math.max(1, outcome.getDurationSeconds());
        int[] remaining = {totalSeconds};
        updateRevillBar(outcome, remaining[0], totalSeconds);

        countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                if (countdownTask != null) {
                    countdownTask.cancel();
                }
                return;
            }
            updateRevillBar(outcome, remaining[0], totalSeconds);
        }, 20L, 20L);
    }

    private void updateRevillBar(WorldEventOutcome outcome, int remainingSeconds, int totalSeconds) {
        if (revillCountdownBar == null) {
            return;
        }
        String title = ColorUtils.color(
                "&c&l¡REVILL! &r&f" + outcome.getObjectiveDescription() + " &7(" + remainingSeconds + "s)");
        revillCountdownBar.setTitle(title);
        revillCountdownBar.setProgress(Math.max(0.0, (double) remainingSeconds / totalSeconds));
    }

    private void startMeteorShower(WorldEventOutcome outcome) {
        int interval = Math.max(20, outcome.getMeteorIntervalTicks());
        meteorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> spawnMeteor(outcome), interval, interval);
    }

    private void spawnMeteor(WorldEventOutcome outcome) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        Player target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        Location spawnLocation = target.getLocation().clone().add(
                ThreadLocalRandom.current().nextInt(-12, 13), 25, ThreadLocalRandom.current().nextInt(-12, 13));
        World world = spawnLocation.getWorld();
        if (world == null) {
            return;
        }

        LargeFireball fireball = world.spawn(spawnLocation, LargeFireball.class);
        fireball.setVelocity(new Vector(0, -1, 0));
        fireball.setYield((float) outcome.getMeteorYield());
        fireball.setIsIncendiary(outcome.isMeteorBreaksBlocks());
    }

    private void resolveRevill(WorldEventOutcome outcome) {
        int survived = 0;
        int eliminated = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (livesManager.isEliminated(player)) {
                continue;
            }
            if (meetsRevillObjective(player, outcome)) {
                survived++;
            } else {
                livesManager.eliminate(player);
                eliminated++;
                Messages.broadcast(plugin, "revill-eliminated", "%player%", player.getName());
            }
        }
        Messages.broadcast(plugin, "revill-summary",
                "%sobrevivientes%", String.valueOf(survived), "%eliminados%", String.valueOf(eliminated));
    }

    private boolean meetsRevillObjective(Player player, WorldEventOutcome outcome) {
        RevillObjectiveType type = outcome.getObjectiveType();
        if (type == null) {
            return true;
        }
        double y = player.getLocation().getY();
        return switch (type) {
            case Y_ABOVE -> y >= outcome.getObjectiveY();
            case Y_BELOW -> y <= outcome.getObjectiveY();
        };
    }
}
