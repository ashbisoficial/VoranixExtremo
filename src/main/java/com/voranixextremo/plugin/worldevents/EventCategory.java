package com.voranixextremo.plugin.worldevents;

import org.bukkit.Color;
import org.bukkit.boss.BarColor;

/** Qué representa el evento para quien lo ve: el color (título, barra y partículas) es
 * siempre el mismo para la misma categoría, así los jugadores aprenden a leerlo de un vistazo. */
public enum EventCategory {
    PELIGRO("&c", BarColor.RED, Color.RED),
    BUENO("&a", BarColor.GREEN, Color.LIME),
    NEUTRAL("&e", BarColor.YELLOW, Color.YELLOW);

    private final String chatColorCode;
    private final BarColor barColor;
    private final Color particleColor;

    EventCategory(String chatColorCode, BarColor barColor, Color particleColor) {
        this.chatColorCode = chatColorCode;
        this.barColor = barColor;
        this.particleColor = particleColor;
    }

    public String getChatColorCode() {
        return chatColorCode;
    }

    public BarColor getBarColor() {
        return barColor;
    }

    public Color getParticleColor() {
        return particleColor;
    }
}
