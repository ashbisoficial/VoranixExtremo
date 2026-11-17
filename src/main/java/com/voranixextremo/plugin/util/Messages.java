package com.voranixextremo.plugin.util;

import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Los mensajes del plugin, en plugins/VoranixExtremo/messages.yml (se recarga con /voranixextremo reload).
 *
 * Lo que falte en el messages.yml del servidor (por ejemplo un mensaje nuevo de una versión más nueva
 * del plugin) se toma del messages.yml que trae el jar.
 *
 * Antes los mensajes estaban en config.yml (messages, totempop, death-effects.title.*-text y
 * ruleta-colores.mensajes): la primera vez que se crea messages.yml se copian de ahí, así no se pierde
 * lo que ya estaba cambiado.
 */
public final class Messages {

    private static final String ARCHIVO = "messages.yml";
    private static YamlConfiguration mensajes = new YamlConfiguration();

    private Messages() {
    }

    /** Lee (o crea) messages.yml. Va al arrancar y en cada /voranixextremo reload. */
    public static void cargar(JavaPlugin plugin) {
        File archivo = new File(plugin.getDataFolder(), ARCHIVO);
        boolean nuevo = !archivo.exists();
        if (nuevo) {
            plugin.saveResource(ARCHIVO, false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(archivo);
        try (InputStream in = plugin.getResource(ARCHIVO)) {
            if (in != null) {
                yml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo leer el messages.yml del plugin", e);
        }
        if (nuevo && migrarDeConfig(plugin.getConfig(), yml)) {
            try {
                yml.save(archivo);
                plugin.getLogger().info("Los mensajes que estaban en config.yml se pasaron a messages.yml"
                        + " (en config.yml ya no se usan: puedes borrarlos de ahí).");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "No se pudo guardar messages.yml", e);
            }
        }
        mensajes = yml;
    }

    /** Copia a messages.yml los mensajes que el servidor tenía en config.yml. true si copió alguno. */
    private static boolean migrarDeConfig(FileConfiguration config, YamlConfiguration yml) {
        boolean alguno = false;
        ConfigurationSection viejos = config.getConfigurationSection("messages");
        if (viejos != null) {
            for (String key : viejos.getKeys(false)) {
                alguno |= copiar(config, "messages." + key, yml, key);
            }
        }
        alguno |= copiar(config, "totempop", yml, "totem-pop");
        alguno |= copiar(config, "death-effects.title.death-text", yml, "death-text");
        alguno |= copiar(config, "death-effects.title.eliminated-text", yml, "eliminated-text");
        alguno |= copiar(config, "ruleta-colores.resource-pack.mensaje", yml, "ruleta.pack-pedido");
        ConfigurationSection ruleta = config.getConfigurationSection("ruleta-colores.mensajes");
        if (ruleta != null) {
            for (String key : ruleta.getKeys(false)) {
                alguno |= copiar(config, "ruleta-colores.mensajes." + key, yml, "ruleta." + key);
            }
        }
        return alguno;
    }

    private static boolean copiar(FileConfiguration config, String desde, YamlConfiguration yml, String hasta) {
        String valor = config.isString(desde) ? config.getString(desde) : null;
        if (valor == null) {
            return false;
        }
        yml.set(hasta, valor);
        return true;
    }

    /** El texto tal cual (sin colores ni prefijo); si no existe, def. */
    public static String texto(String path, String def) {
        String valor = mensajes.getString(path);
        return valor != null ? valor : def;
    }

    /** Mensaje con colores pero sin prefijo (para títulos, la barra de la hotbar, broadcasts sin firma). */
    public static String sinPrefijo(String path, String... replacements) {
        String message = texto(path, path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return ColorUtils.color(message);
    }

    /** Mensaje con el prefijo del plugin delante y los %reemplazos% puestos. */
    public static String get(String path, String... replacements) {
        return ColorUtils.color(texto("prefix", "")) + sinPrefijo(path, replacements);
    }

    public static void send(CommandSender sender, String path, String... replacements) {
        sender.sendMessage(get(path, replacements));
    }

    public static void broadcast(String path, String... replacements) {
        Bukkit.broadcastMessage(get(path, replacements));
    }

    // las de antes, con el plugin como primer argumento (ya no hace falta, pero así no cambia nada más)

    public static String get(Plugin plugin, String path, String... replacements) {
        return get(path, replacements);
    }

    public static void send(CommandSender sender, Plugin plugin, String path, String... replacements) {
        send(sender, path, replacements);
    }

    public static void broadcast(Plugin plugin, String path, String... replacements) {
        broadcast(path, replacements);
    }
}
