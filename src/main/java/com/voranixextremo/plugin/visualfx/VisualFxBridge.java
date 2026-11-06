package com.voranixextremo.plugin.visualfx;

import com.voranixextremo.plugin.VoranixExtremoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Habla con el mod cliente VoranixVisualFX (repo aparte, Forge 1.20.1) por el canal de red
 * "voranixvisualfx:main". A quien no tenga el mod instalado no le pasa nada: Minecraft descarta
 * en silencio un paquete de un canal que ese cliente no registró.
 *
 * El formato de cada paquete tiene que coincidir byte a byte con los que decodifica ese otro
 * repo (documentado en su README) - si se cambia acá, hay que cambiarlo ahí también.
 */
public final class VisualFxBridge {

    private static final String CANAL = "voranixvisualfx:main";
    private static final byte ID_CIELO = 0;
    private static final byte ID_ZONA_AGREGAR = 1;
    private static final byte ID_ZONA_QUITAR = 2;
    private static final byte ID_ZONA_LIMPIAR = 3;

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
        byte[] datos = armarPaqueteCielo(colorHorizonte, cenit, (int) Math.round(segundos * 20), (int) Math.round(fundidoSegundos * 20));
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            jugador.sendPluginMessage(plugin, CANAL, datos);
        }
    }

    /** Apaga el efecto de cielo ya mismo en todos los conectados. */
    public void detener() {
        int[] negro = {0, 0, 0};
        cielo(negro, negro, 0, 0);
    }

    private static byte[] armarPaqueteCielo(int[] horizonte, int[] cenit, int duracionTicks, int fundidoTicks) {
        return conBuffer(datos -> {
            datos.writeByte(ID_CIELO);
            datos.writeByte(horizonte[0]);
            datos.writeByte(horizonte[1]);
            datos.writeByte(horizonte[2]);
            datos.writeByte(cenit[0]);
            datos.writeByte(cenit[1]);
            datos.writeByte(cenit[2]);
            datos.writeInt(duracionTicks);
            datos.writeInt(fundidoTicks);
        });
    }

    /** Le manda esta zona a TODOS los conectados (se usa al crearla - cada uno decide si le aplica según su mundo, ver FogZoneManager). */
    public void enviarZonaATodos(FogZoneManager.Zona zona) {
        byte[] datos = armarPaqueteZona(zona);
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            jugador.sendPluginMessage(plugin, CANAL, datos);
        }
    }

    /** Le manda esta zona a UN jugador puntual (join, cambio de mundo, registro de canal). */
    public void enviarZonaA(Player jugador, FogZoneManager.Zona zona) {
        jugador.sendPluginMessage(plugin, CANAL, armarPaqueteZona(zona));
    }

    /** Le avisa a TODOS que se borró una zona (por nombre). */
    public void enviarRemocionATodos(String nombre) {
        byte[] datos = armarPaqueteRemocion(nombre);
        for (Player jugador : Bukkit.getOnlinePlayers()) {
            jugador.sendPluginMessage(plugin, CANAL, datos);
        }
    }

    /** "Borrá todas las zonas que tengas" - se manda antes de reenviar las del mundo actual. */
    public void enviarLimpiarA(Player jugador) {
        jugador.sendPluginMessage(plugin, CANAL, new byte[]{ID_ZONA_LIMPIAR});
    }

    private static byte[] armarPaqueteZona(FogZoneManager.Zona zona) {
        return conBuffer(datos -> {
            datos.writeByte(ID_ZONA_AGREGAR);
            escribirTexto(datos, zona.nombre());
            datos.writeByte(zona.tipo() == FogZoneManager.Tipo.ESFERA ? 1 : 0);
            datos.writeDouble(zona.p0());
            datos.writeDouble(zona.p1());
            datos.writeDouble(zona.p2());
            datos.writeDouble(zona.p3());
            datos.writeDouble(zona.p4());
            datos.writeDouble(zona.p5());
            datos.writeByte(zona.color()[0]);
            datos.writeByte(zona.color()[1]);
            datos.writeByte(zona.color()[2]);
            datos.writeFloat(zona.cercana());
            datos.writeFloat(zona.lejana());
            datos.writeFloat(zona.margen());
            datos.writeBoolean(zona.creciendoActivo());
            datos.writeLong(zona.tickInicio());
            datos.writeFloat(zona.velocidadBloquesPorSegundo());
            datos.writeFloat(zona.radioMaximo());
        });
    }

    private static byte[] armarPaqueteRemocion(String nombre) {
        return conBuffer(datos -> {
            datos.writeByte(ID_ZONA_QUITAR);
            escribirTexto(datos, nombre);
        });
    }

    /** Mismo formato que ByteUtil.escribirTexto del lado del mod: 1 byte de longitud + UTF-8 (nada de VarInt). */
    private static void escribirTexto(DataOutputStream datos, String texto) throws IOException {
        byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 255) {
            throw new IllegalArgumentException("Texto demasiado largo para el formato de 1 byte: " + texto);
        }
        datos.writeByte(bytes.length);
        datos.write(bytes);
    }

    @FunctionalInterface
    private interface EscritorPaquete {
        void escribir(DataOutputStream datos) throws IOException;
    }

    private static byte[] conBuffer(EscritorPaquete escritor) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream datos = new DataOutputStream(buffer);
            escritor.escribir(datos);
            return buffer.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream no tira IOException en la práctica
            throw new IllegalStateException(e);
        }
    }
}
