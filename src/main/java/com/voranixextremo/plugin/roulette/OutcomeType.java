package com.voranixextremo.plugin.roulette;

/** Qué hace realmente un suceso de la ruleta al caer. */
public enum OutcomeType {
    /** Aplica un PotionEffect (bueno o malo según la duración/tipo elegido). */
    EFFECT,
    /** Deposita "amount" en el balance del jugador. */
    GIVE_COINS,
    /** Retira "amount" del balance del jugador (o lo que tenga, si es menos). */
    TAKE_COINS,
    /** Suma una vida (hasta el máximo configurado). */
    EXTRA_LIFE,
    /** Resta una vida, igual que una muerte normal. */
    LOSE_LIFE,
    /** Rayo decorativo en la posición del jugador (sin daño). */
    LIGHTNING,
    /** Ejecuta un comando de consola, reemplazando %player% — forma genérica de agregar
     * cualquier suceso sin tocar código (dar un ítem, teletransportar, etc.). */
    COMMAND,
    /** Asigna una tarea con tiempo límite; se resuelve con /ruleta completar|fallar. */
    TASK
}
