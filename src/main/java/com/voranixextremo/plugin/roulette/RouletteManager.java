package com.voranixextremo.plugin.roulette;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import com.voranixextremo.plugin.economy.EconomyManager;
import com.voranixextremo.plugin.lives.LivesManager;
import com.voranixextremo.plugin.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Carga los sucesos de config.yml -> roulette.outcomes y los aplica: elige uno al azar
 * (ponderado por "weight"), lo muestra con una animación tipo tragamonedas, y ejecuta su
 * efecto real (buff/debuff, monedas, vida, un comando libre, o una tarea con plazo).
 */
public class RouletteManager {

    private final VoranixExtremoPlugin plugin;
    private final EconomyManager economyManager;
    private final LivesManager livesManager;
    private final Map<String, RouletteOutcome> outcomes = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, ActiveTask> activeTasks = new HashMap<>();

    public RouletteManager(VoranixExtremoPlugin plugin, EconomyManager economyManager, LivesManager livesManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.livesManager = livesManager;
        load();
    }

    private void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("roulette.outcomes");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection outcomeSection = section.getConfigurationSection(id);
            if (outcomeSection == null) {
                continue;
            }
            try {
                outcomes.put(id.toLowerCase(), RouletteOutcome.fromConfig(id, outcomeSection));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Suceso de ruleta '" + id + "' mal configurado: " + ex.getMessage());
            }
        }
    }

    public boolean hasOutcomes() {
        return !outcomes.isEmpty();
    }

    public long getRemainingCooldownSeconds(Player player) {
        Long until = cooldownUntil.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        long remainingMillis = until - System.currentTimeMillis();
        return remainingMillis <= 0 ? 0 : (remainingMillis / 1000) + 1;
    }

    public void spin(Player target, boolean bypassCooldown) {
        if (!hasOutcomes()) {
            return;
        }
        if (!bypassCooldown) {
            int cooldownSeconds = plugin.getConfig().getInt("roulette.cooldown-seconds", 300);
            cooldownUntil.put(target.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
        }

        RouletteOutcome result = pickWeighted();
        Messages.broadcast(plugin, "roulette-spinning", "%player%", target.getName());
        runSpinAnimation(target, result);
    }

    private RouletteOutcome pickWeighted() {
        int totalWeight = outcomes.values().stream().mapToInt(RouletteOutcome::getWeight).sum();
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, totalWeight));
        int cumulative = 0;
        for (RouletteOutcome outcome : outcomes.values()) {
            cumulative += outcome.getWeight();
            if (roll < cumulative) {
                return outcome;
            }
        }
        return outcomes.values().iterator().next();
    }

    private void runSpinAnimation(Player target, RouletteOutcome result) {
        List<RouletteOutcome> all = new ArrayList<>(outcomes.values());
        int steps = plugin.getConfig().getInt("roulette.spin-steps", 14);

        BukkitTask[] holder = new BukkitTask[1];
        int[] step = {0};
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (step[0] >= steps) {
                holder[0].cancel();
                applyOutcome(target, result);
                return;
            }
            RouletteOutcome flash = all.get(ThreadLocalRandom.current().nextInt(all.size()));
            showFrame(target, flash);
            target.playSound(target.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F + step[0] * 0.02F);
            step[0]++;
        }, 0L, 4L);
    }

    private void showFrame(Player target, RouletteOutcome outcome) {
        String colored = ChatColor.translateAlternateColorCodes('&', categoryColor(outcome.getCategory()) + outcome.getDisplayName());
        Component component = LegacyComponentSerializer.legacySection().deserialize(colored);
        target.showTitle(Title.title(component, Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(250), Duration.ZERO)));
    }

    private String categoryColor(OutcomeCategory category) {
        return switch (category) {
            case BUENO -> "&a";
            case MALO -> "&c";
            case TAREA -> "&e";
        };
    }

    private void applyOutcome(Player target, RouletteOutcome outcome) {
        String messageKey = switch (outcome.getCategory()) {
            case BUENO -> "roulette-result-bueno";
            case MALO -> "roulette-result-malo";
            case TAREA -> "roulette-result-tarea";
        };
        Messages.broadcast(plugin, messageKey, "%player%", target.getName(), "%resultado%", outcome.getDisplayName());

        switch (outcome.getType()) {
            case EFFECT -> {
                if (outcome.getEffectType() != null) {
                    target.addPotionEffect(new PotionEffect(outcome.getEffectType(), outcome.getDurationTicks(), outcome.getAmplifier(), false, true, true));
                }
            }
            case GIVE_COINS -> economyManager.deposit(target, outcome.getAmount());
            case TAKE_COINS -> economyManager.withdraw(target, Math.min(outcome.getAmount(), economyManager.getBalance(target)));
            case EXTRA_LIFE -> livesManager.setLives(target, Math.min(livesManager.getMaxLives(), livesManager.getLives(target) + 1));
            case LOSE_LIFE -> applyLifeLoss(target);
            case LIGHTNING -> target.getWorld().strikeLightningEffect(target.getLocation());
            case COMMAND -> runCommand(target, outcome.getCommand());
            case TASK -> startTask(target, outcome);
        }
    }

    private void applyLifeLoss(Player target) {
        int remaining = livesManager.loseLife(target);
        if (remaining > 0) {
            Messages.broadcast(plugin, "life-lost", "%player%", target.getName(), "%vidas%", String.valueOf(remaining));
        } else {
            Messages.broadcast(plugin, "eliminated", "%player%", target.getName());
        }
    }

    private void runCommand(Player target, String commandTemplate) {
        if (commandTemplate == null || commandTemplate.isBlank()) {
            return;
        }
        String resolved = commandTemplate.replace("%player%", target.getName());
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), resolved);
    }

    private void startTask(Player target, RouletteOutcome outcome) {
        activeTasks.put(target.getUniqueId(),
                new ActiveTask(outcome.getId(), outcome.getTaskDescription(), outcome.getTaskRewardCoins(), outcome.getTaskPenaltyCoins()));

        Messages.broadcast(plugin, "roulette-task-assigned", "%player%", target.getName(),
                "%tarea%", outcome.getTaskDescription(), "%segundos%", String.valueOf(outcome.getTaskDurationSeconds()));

        UUID playerId = target.getUniqueId();
        String outcomeId = outcome.getId();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> autoFailIfStillActive(playerId, outcomeId), outcome.getTaskDurationSeconds() * 20L);
    }

    private void autoFailIfStillActive(UUID playerId, String outcomeId) {
        ActiveTask task = activeTasks.get(playerId);
        if (task == null || !task.outcomeId().equals(outcomeId)) {
            return;
        }
        activeTasks.remove(playerId);

        Player player = plugin.getServer().getPlayer(playerId);
        String name = player != null ? player.getName() : playerId.toString();
        if (player != null && task.penaltyCoins() > 0) {
            economyManager.withdraw(player, Math.min(task.penaltyCoins(), economyManager.getBalance(player)));
        }
        Messages.broadcast(plugin, "roulette-task-failed", "%player%", name);
    }

    /** @return false si el jugador no tenía ninguna tarea activa para resolver. */
    public boolean resolveTask(Player target, boolean success) {
        ActiveTask task = activeTasks.remove(target.getUniqueId());
        if (task == null) {
            return false;
        }
        if (success) {
            economyManager.deposit(target, task.rewardCoins());
            Messages.broadcast(plugin, "roulette-task-completed", "%player%", target.getName());
        } else {
            economyManager.withdraw(target, Math.min(task.penaltyCoins(), economyManager.getBalance(target)));
            Messages.broadcast(plugin, "roulette-task-failed", "%player%", target.getName());
        }
        return true;
    }
}
