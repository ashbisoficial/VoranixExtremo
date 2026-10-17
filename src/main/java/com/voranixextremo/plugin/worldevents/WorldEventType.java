package com.voranixextremo.plugin.worldevents;

/** Qué hace realmente un evento mundial mientras está activo. */
public enum WorldEventType {
    /** Fuerza el PVP activado durante la duración del evento, y lo devuelve a como estaba al terminar. */
    PVP_WINDOW,
    /** Pone la noche/tormenta, y mientras dura: mobs más fuertes (salud/daño) y botín extra al morir. */
    RED_MOON,
    /** Ejecuta un comando de consola al empezar — forma genérica de agregar eventos sin tocar código. */
    COMMAND,
    /** "Momento Revill": tormenta + lluvia de meteoritos + un objetivo con cuenta regresiva
     * visible; quien no lo cumpla a tiempo queda eliminado directamente (estilo DEDsafio). */
    REVILL
}
