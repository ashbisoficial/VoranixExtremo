package com.voranixextremo.plugin.worldevents;

import org.bukkit.boss.BarColor;

/** Qué representa el evento para quien lo ve: el color (título + barra) es siempre el
 * mismo para la misma categoría, así los jugadores aprenden a leerlo de un vistazo. */
public enum EventCategory {
    PELIGRO("&c", BarColor.RED),
    BUENO("&a", BarColor.GREEN),
    NEUTRAL("&e", BarColor.YELLOW);

    private final String chatColorCode;
    private final BarColor barColor;

    EventCategory(String chatColorCode, BarColor barColor) {
        this.chatColorCode = chatColorCode;
        this.barColor = barColor;
    }

    public String getChatColorCode() {
        return chatColorCode;
    }

    public BarColor getBarColor() {
        return barColor;
    }
}
