package com.voranixextremo.plugin.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Elige UNA sola denominación (no una mezcla, a diferencia de BankService) para cobrar un
 * precio en el trade de un aldeano, ya que Minecraft solo permite hasta 2 tipos de ítem
 * como costo de un trade. Siempre redondea hacia arriba, así un trade nunca cobra de menos.
 */
public final class CoinEconomy {

    private CoinEconomy() {
    }

    /** La denominación más alta que sigue costando 64 monedas o menos (un stack); si ninguna
     * entra en ese rango, cae a la de menor valor. */
    public static CoinType bestDenominationFor(double amountInBaseUnits, EconomyManager economyManager) {
        List<CoinType> sorted = new ArrayList<>(economyManager.getCoinTypes().values());
        sorted.sort(Comparator.comparingDouble(CoinType::getValue));

        CoinType best = sorted.isEmpty() ? null : sorted.get(0);
        for (CoinType type : sorted) {
            if (type.getValue() <= 0) {
                continue;
            }
            long count = (long) Math.ceil(amountInBaseUnits / type.getValue());
            if (count >= 1 && count <= 64) {
                best = type;
            }
        }
        return best;
    }

    /** Cuántas monedas de {@code denomination} cuesta un precio de {@code amountInBaseUnits}, redondeado hacia arriba. */
    public static int countIn(double amountInBaseUnits, CoinType denomination) {
        return (int) Math.max(1, Math.ceil(amountInBaseUnits / denomination.getValue()));
    }
}
