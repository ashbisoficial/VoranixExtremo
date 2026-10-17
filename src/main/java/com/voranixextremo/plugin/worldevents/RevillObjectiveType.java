package com.voranixextremo.plugin.worldevents;

/** Cómo se mide si un jugador cumplió el objetivo de un Momento Revill, comparando su
 * altura (Y) contra "objective-y" configurado en el suceso. */
public enum RevillObjectiveType {
    /** Sobrevive quien esté EN o POR ENCIMA de objective-y (ej. "subí a Y=250"). */
    Y_ABOVE,
    /** Sobrevive quien esté EN o POR DEBAJO de objective-y (ej. "bajá a Y=-250"). */
    Y_BELOW
}
