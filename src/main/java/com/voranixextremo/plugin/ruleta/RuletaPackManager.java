package com.voranixextremo.plugin.ruleta;

import com.sun.net.httpserver.HttpServer;
import com.voranixextremo.plugin.VoranixExtremoPlugin;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Arma el resource pack de la ruleta (resourcepack/Ruleta, que Maven mete en el jar bajo
 * ruleta-pack/) y, si se quiere, lo sirve por HTTP para mandarlo a los jugadores. Se manda
 * como un pack aparte, sin reemplazar el pack de las monedas (resourcepack.url).
 */
public final class RuletaPackManager implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("voranixextremo:ruleta".getBytes(StandardCharsets.UTF_8));
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
            plugin.getLogger().info("Resource pack de la ruleta listo (sha1 " + sha1 + ")" + (uri != null ? " -> " + uri : ", sin envío automático"));
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
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, uri, sha1))
                .required(c.getBoolean("resource-pack.obligatorio", false))
                .prompt(RuletaManager.parse(c.getString("resource-pack.mensaje", "")))
                .replace(false));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (ruleta.config().getBoolean("resource-pack.enviar-al-entrar", true)) {
            // un pequeño retraso para que el cliente termine de cargar el mundo (y llegue
            // después del pack de las monedas, que sí reemplaza los packs anteriores)
            Bukkit.getScheduler().runTaskLater(plugin, () -> send(e.getPlayer()), 20L);
        }
    }
}
