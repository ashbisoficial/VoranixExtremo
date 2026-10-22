package com.voranixextremo.plugin.worldevents;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Mientras esté activo el evento RED_MOON: los mobs que nacen son más fuertes
 * (salud/daño multiplicados) y al morir tienen una chance extra de soltar botín
 * configurado (bonus-drops) además de su loot normal.
 */
public class RedMoonListener implements Listener {

    private final WorldEventManager worldEventManager;

    public RedMoonListener(WorldEventManager worldEventManager) {
        this.worldEventManager = worldEventManager;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        WorldEventOutcome redMoon = worldEventManager.getActiveRedMoon();
        if (redMoon == null) {
            return;
        }
        buffEntity(event.getEntity(), redMoon);
    }

    private void buffEntity(LivingEntity entity, WorldEventOutcome redMoon) {
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * redMoon.getMobHealthMultiplier());
            entity.setHealth(Math.min(entity.getHealth() * redMoon.getMobHealthMultiplier(), health.getValue()));
        }
        AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * redMoon.getMobDamageMultiplier());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        WorldEventOutcome redMoon = worldEventManager.getActiveRedMoon();
        if (redMoon == null) {
            return;
        }
        for (BonusDrop drop : redMoon.getBonusDrops()) {
            if (ThreadLocalRandom.current().nextDouble() > drop.chance()) {
                continue;
            }
            int amount = drop.min() >= drop.max() ? drop.min()
                    : ThreadLocalRandom.current().nextInt(drop.min(), drop.max() + 1);
            event.getDrops().add(new ItemStack(drop.material(), amount));
        }
    }
}
