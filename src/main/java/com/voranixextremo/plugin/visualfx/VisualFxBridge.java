package com.voranixextremo.plugin.visualfx;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Habla con el mod cliente VoranixVisualFX (repo aparte, Forge 1.20.1) por el canal de red
 * "voranixvisualfx:main". A quien no tenga el mod instalado no le pasa nada: Minecraft descarta
 * en silencio un paquete de un canal que ese cliente no registró.
 *
 * El formato del paquete tiene que coincidir byte a byte con network/SkyColorPacket.java de ese
 * otro repo (está documentado en su README) - si se cambia acá, hay que cambiarlo ahí también.
 */
public final class VisualFxBridge {

    private static final String CANAL = "voranixvisualfx:main";
    private static final byte ID_CIELO = 0;
    private static final byte ID_NIEBLA_ZONA = 1;

    /** Los mismos 8 colores que /ruletacolor, así "fx cielo morado" usa el mismo morado de la ruleta. */
    public static final Map<String, int[]> COLORES = colores();

    private final VoranixExtremoPlugin plugin;

    public VisualFxBridge(VoranixExtremoPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CANAL);
    }

    public void stop() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CANAL);
    }

    private static Map<String, int[]> colores() {
        Map<String, int[]> m = new LinkedHashMap<>();
        m.put("rojo", rgb(0xff2b2b));
        m.put("naranjo", rgb(0xff8a1c));
        m.put("amarillo", rgb(0xffe12b));
        m.put("verde", rgb(0x2bd94a));
        m.put("celeste", rgb(0x2be4ff));
        m.put("azul", rgb(0x2b8cff));
        m.put("morado", rgb(0x6a3bff));
        m.put("rosa", rgb(0xe02bff));
        return Map.copyOf(m);
    }

    private static int[] rgb(int hex) {
        return new int[]{(hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF};
    }

    /** Nombre de color (de COLORES) o "#rrggbb". Null si no se pudo interpretar. */
    public static int[] parseColor(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim().toLowerCase(Locale.ROOT);
        if (COLORES.containsKey(t)) {
            return COLORES.get(t);
        }
        if (t.startsWith("#") && t.length() == 7) {
            try {
                return rgb(Integer.parseInt(t.substring(1), 16));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Manda el cielo a todos los conectados. colorCenit es lo que se ve mirando para arriba;
     * si es null se usa el mismo colorHorizonte (cielo parejo, sin degradado). segundos/
     * fundidoSegundos en segundos reales.
     */
    public void cielo(int[] colorHorizonte, int[] colorCenit, double segundos, double fundidoSegundos) {
        int[] cenit = colorCenit != null ? colorCenit : colorHorizonte;
        byte[] datos = armarPaquete(colorHorizonte, cenit, (int) Math.round(segundos * 20), (int) Math.round(fundidoSegundos * 20));
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            jugador.sendPluginMessage(plugin, CANAL, datos);
        }
    }

    /** Apaga el efecto ya mismo en todos los conectados. */
    public void detener() {
        int[] negro = {0, 0, 0};
        cielo(negro, negro, 0, 0);
    }

    private static byte[] armarPaquete(int[] horizonte, int[] cenit, int duracionTicks, int fundidoTicks) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream datos = new DataOutputStream(buffer);
            datos.writeByte(ID_CIELO);
            datos.writeByte(horizonte[0]);
            datos.writeByte(horizonte[1]);
            datos.writeByte(horizonte[2]);
            datos.writeByte(cenit[0]);
            datos.writeByte(cenit[1]);
            datos.writeByte(cenit[2]);
            datos.writeInt(duracionTicks);
            datos.writeInt(fundidoTicks);
            return buffer.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream no tira IOException en la práctica
            throw new IllegalStateException(e);
        }
    }

    /**
     * Niebla de zona para UN jugador puntual (a diferencia de cielo(), que es siempre para
     * todos): lo usa FogZoneManager cuando ese jugador entra a una zona. cercana/lejana en
     * bloques; lejana &lt;= 0 apaga el efecto (lo manda FogZoneManager cuando sale de la zona).
     */
    public void nieblaZona(Player jugador, int[] colorRgb, float cercana, float lejana, double fundidoSegundos) {
        jugador.sendPluginMessage(plugin, CANAL, armarPaqueteNiebla(colorRgb, cercana, lejana, (int) Math.round(fundidoSegundos * 20)));
    }

    /** Apaga la niebla de zona para ese jugador (con una transición corta, no de un golpe). */
    public void quitarNieblaZona(Player jugador, double fundidoSegundos) {
        nieblaZona(jugador, new int[]{0, 0, 0}, 0f, 0f, fundidoSegundos);
    }

    private static byte[] armarPaqueteNiebla(int[] color, float cercana, float lejana, int fundidoTicks) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream datos = new DataOutputStream(buffer);
            datos.writeByte(ID_NIEBLA_ZONA);
            datos.writeByte(color[0]);
            datos.writeByte(color[1]);
            datos.writeByte(color[2]);
            datos.writeFloat(cercana);
            datos.writeFloat(lejana);
            datos.writeInt(fundidoTicks);
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
