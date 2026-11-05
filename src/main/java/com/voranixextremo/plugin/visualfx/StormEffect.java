package com.voranixextremo.plugin.visualfx;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Tormenta puntual (nubes + rayos) en un punto del mapa, para momentos dramáticos (matar un
 * boss, conseguir un ítem legendario). 100% vainilla - no necesita el mod VoranixVisualFX, así
 * que lo ve todo el mundo, lo tenga instalado o no. Pensado para dos formas de dispararla:
 * manual (staff parado ahí, /fx tormenta) y programática (disparar(...) desde cualquier otro
 * listener del plugin, ej. cuando se recoja un ítem específico).
 */
public final class StormEffect {

    private final VoranixExtremoPlugin plugin;

    public StormEffect(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
    }

    /** Arranca la tormenta en ese punto por "segundos" (nubes todo el rato, rayos de a ratos). */
    public void disparar(Location centro, int segundos) {
        World mundo = centro.getWorld();
        if (mundo == null) {
            return;
        }
        int duracionTicks = Math.max(20, segundos * 20);
        BukkitTask[] referencia = new BukkitTask[1];
        referencia[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int transcurridoTicks = 0;

            @Override
            public void run() {
                nube(mundo, centro);
                if (ThreadLocalRandom.current().nextInt(100) < 10) {
                    rayo(mundo, centro);
                }
                transcurridoTicks += 4;
                if (transcurridoTicks >= duracionTicks) {
                    referencia[0].cancel();
                }
            }
        }, 0L, 4L);
    }

    private void nube(World mundo, Location centro) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 14; i++) {
            double x = centro.getX() + rnd.nextDouble(-9, 9);
            double y = centro.getY() + 10 + rnd.nextDouble(0, 5);
            double z = centro.getZ() + rnd.nextDouble(-9, 9);
            mundo.spawnParticle(Particle.LARGE_SMOKE, x, y, z, 1, 0, 0, 0, 0.01);
            mundo.spawnParticle(Particle.CLOUD, x, y, z, 1, 0, 0, 0, 0.01);
        }
    }

    private void rayo(World mundo, Location centro) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double x = centro.getX() + rnd.nextDouble(-7, 7);
        double z = centro.getZ() + rnd.nextDouble(-7, 7);
        // strikeLightningEffect: el rayo visual + el sonido del trueno, sin dañar ni prender fuego
        mundo.strikeLightningEffect(new Location(mundo, x, centro.getY(), z));
    }
}
