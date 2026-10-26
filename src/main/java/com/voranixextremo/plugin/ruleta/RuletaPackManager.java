package com.voranixextremo.plugin.ruleta;

import com.sun.net.httpserver.HttpServer;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Arma el resource pack del plugin (Maven mete en el jar, bajo ruleta-pack/, la ruleta de
 * resourcepack/Ruleta y las monedas y comida de resourcepack/assets) y, si se quiere, lo sirve
 * por HTTP para mandarlo a los jugadores. La 1.20.1 acepta un solo pack del servidor, por eso
 * va todo junto. Si en config.yml → resourcepack.url hay un pack propio, se manda ese y este no.
 */
public final class RuletaPackManager implements Listener {

    private static final String CARPETA = "ruleta-pack/";

    private final VoranixExtremoPlugin plugin;
    private final RuletaManager ruleta;
    private HttpServer server;
    private ExecutorService executor;
    private URI uri;
    private String sha1;

    public RuletaPackManager(VoranixExtremoPlugin plugin, RuletaManager ruleta) {
        this.plugin = plugin;
        this.ruleta = ruleta;
    }

    public void start() {
        ConfigurationSection c = ruleta.config();
        try {
            byte[] bytes = zipDesdeJar();
            Path zip = plugin.getDataFolder().toPath().resolve("ruleta-pack.zip");
            Files.createDirectories(zip.getParent());
            Files.write(zip, bytes);
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));

            String externa = c.getString("resource-pack.url-externa", "");
            if (externa != null && !externa.isBlank()) {
                uri = URI.create(externa);
            } else if (c.getBoolean("resource-pack.servidor-interno", true)) {
                int port = c.getInt("resource-pack.puerto", 8165);
                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/ruleta-pack.zip", ex -> {
                    ex.getResponseHeaders().add("Content-Type", "application/zip");
                    // un HEAD (lo usan algunos clientes para ver el tamaño) no lleva cuerpo
                    boolean head = "HEAD".equalsIgnoreCase(ex.getRequestMethod());
                    ex.sendResponseHeaders(200, head ? -1 : bytes.length);
                    try (OutputStream out = ex.getResponseBody()) {
                        if (!head) out.write(bytes);
                    }
                });
                executor = Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "Ruleta-PackServer");
                    t.setDaemon(true);
                    return t;
                });
                server.setExecutor(executor);
                server.start();
                uri = URI.create("http://" + c.getString("resource-pack.ip-publica", "127.0.0.1") + ":" + port + "/ruleta-pack.zip");
            } else {
                uri = null;
            }
            plugin.getLogger().info("Resource pack del plugin listo (sha1 " + sha1 + ")" + (uri != null ? " -> " + uri : ", sin envío automático"));
            if (hayPackPropio()) {
                plugin.getLogger().warning("resourcepack.url tiene un pack propio: se manda ese y no el del plugin. "
                        + "Para que se vea la ruleta, ese pack tiene que incluir plugins/VoranixExtremo/ruleta-pack.zip.");
            }
        } catch (Exception e) {
            uri = null;
            plugin.getLogger().log(Level.SEVERE, "No se pudo preparar el resource pack de la ruleta", e);
        }
    }

    /** Junta los archivos de ruleta-pack/ del jar en un .zip (siempre igual, así el sha1 no cambia). */
    private byte[] zipDesdeJar() throws Exception {
        Path jar = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (JarFile jf = new JarFile(jar.toFile()); ZipOutputStream zip = new ZipOutputStream(buffer)) {
            List<JarEntry> entries = jf.stream()
                    .filter(e -> !e.isDirectory() && e.getName().startsWith(CARPETA))
                    .sorted((a, b) -> a.getName().compareTo(b.getName()))
                    .toList();
            if (entries.isEmpty()) throw new IllegalStateException("El jar no trae " + CARPETA);
            for (JarEntry e : entries) {
                ZipEntry out = new ZipEntry(e.getName().substring(CARPETA.length()));
                out.setTime(0L);
                zip.putNextEntry(out);
                try (InputStream in = jf.getInputStream(e)) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        // server.stop no apaga los hilos: sin esto cada /ruletacolor reload dejaba 2 hilos colgando
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public void send(Player player) {
        if (uri == null || !player.isOnline()) return;
        ConfigurationSection c = ruleta.config();
        String prompt = LegacyComponentSerializer.legacySection().serialize(RuletaManager.parse(c.getString("resource-pack.mensaje", "")));
        player.setResourcePack(uri.toString(), HexFormat.of().parseHex(sha1), prompt.isEmpty() ? null : prompt,
                c.getBoolean("resource-pack.obligatorio", false));
    }

    /** Hay un pack propio en resourcepack.url (lo manda ResourcePackListener): ese reemplazaría a este. */
    private boolean hayPackPropio() {
        String url = plugin.getConfig().getString("resourcepack.url", "");
        return url != null && !url.isBlank();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (hayPackPropio()) return;
        if (ruleta.config().getBoolean("resource-pack.enviar-al-entrar", true)) {
            // un pequeño retraso para que el cliente termine de cargar el mundo
            Bukkit.getScheduler().runTaskLater(plugin, () -> send(e.getPlayer()), 20L);
        }
    }
}
