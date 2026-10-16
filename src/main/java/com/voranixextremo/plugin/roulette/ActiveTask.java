package com.voranixextremo.plugin.roulette;

/** Una tarea de ruleta en curso para un jugador (ver RouletteManager). */
public record ActiveTask(String outcomeId, String description, double rewardCoins, double penaltyCoins) {
}
