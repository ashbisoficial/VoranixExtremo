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
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;
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
    private volatile URI uri;
    /** sha1 del pack que se manda; con un link externo se calcula al descargarlo (null mientras tanto) */
    private volatile String sha1;

    public RuletaPackManager(VoranixExtremoPlugin plugin, RuletaManager ruleta) {
        this.plugin = plugin;
        this.ruleta = ruleta;
    }

    public void start() {
        start(null);
    }

    /**
     * Vuelve a preparar el pack (si es externo, lo vuelve a descargar para sacar su sha1 nuevo) y,
     * cuando está listo, se lo manda a todos los conectados: como el sha1 cambió, el cliente lo baja
     * de nuevo en vez de usar el que tenía guardado. listo recibe a cuántos se les mandó.
     */
    public void recargarYEnviar(IntConsumer listo) {
        stop();
        start(() -> {
            int enviados = 0;
            if (uri != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    send(p);
                    enviados++;
                }
            }
            listo.accept(enviados);
        });
    }

    /** listo (puede ser null) se corre en el hilo principal cuando el pack ya tiene su sha1. */
    private void start(Runnable listo) {
        ConfigurationSection c = ruleta.config();
        try {
            byte[] bytes = zipDesdeJar();
            Path zip = plugin.getDataFolder().toPath().resolve("ruleta-pack.zip");
            Files.createDirectories(zip.getParent());
            Files.write(zip, bytes);
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));

            String externa = urlExterna();
            if (externa != null) {
                // el pack de afuera puede traer más cosas (por ejemplo varios packs juntos): su sha1 no es el
                // del pack del plugin, así que se calcula descargándolo una vez, en segundo plano
                uri = URI.create(externa);
                sha1 = null;
                calcularSha1Externo(uri, listo);
                listo = null; // se avisa cuando termine la descarga
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
            plugin.getLogger().info("Resource pack del plugin listo" + (sha1 != null ? " (sha1 " + sha1 + ")" : "")
                    + (uri != null ? " -> " + uri : ", sin envío automático"));
        } catch (Exception e) {
            uri = null;
            plugin.getLogger().log(Level.SEVERE, "No se pudo preparar el resource pack de la ruleta", e);
        }
        if (listo != null) listo.run();
    }

    /** Descarga el pack externo (sin guardarlo) para saber su sha1; mientras tanto se manda sin sha1. */
    private void calcularSha1Externo(URI externa, Runnable listo) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            revisarExterno(externa);
            if (listo != null) Bukkit.getScheduler().runTask(plugin, listo);
        });
    }

    private void revisarExterno(URI externa) {
        try {
            HttpURLConnection conexion = (HttpURLConnection) externa.toURL().openConnection();
            conexion.setConnectTimeout(10_000);
            conexion.setReadTimeout(60_000);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            long bytes = 0;
            try (InputStream in = conexion.getInputStream()) {
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = in.read(buffer)) > 0; bytes += n) digest.update(buffer, 0, n);
            }
            String calculado = HexFormat.of().formatHex(digest.digest());
            if (externa.equals(uri)) sha1 = calculado; // por si mientras tanto hubo un reload con otro link
            plugin.getLogger().info("Resource pack externo revisado: " + (bytes / 1024 / 1024) + " MB, sha1 " + calculado);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo descargar el resource pack externo para revisarlo (" + e.getMessage()
                    + "). Se va a mandar sin sha1: funciona igual, pero los jugadores lo vuelven a bajar cada vez.");
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
        // sin sha1 (todavía revisando el pack externo) Spigot lo manda igual, solo que el cliente no lo puede cachear
        player.setResourcePack(uri.toString(), sha1 == null ? null : HexFormat.of().parseHex(sha1),
                prompt.isEmpty() ? null : prompt, c.getBoolean("resource-pack.obligatorio", false));
    }

    /**
     * Link del pack de afuera, o null si se usa el del plugin: ruleta-colores.resource-pack.url-externa,
     * o si está vacío, resourcepack.url (el campo viejo de arriba del config.yml). Los dos se mandan
     * igual, con su sha1: antes resourcepack.url se mandaba sin sha1 y el cliente se quedaba con el
     * pack que ya tenía guardado de ese mismo link, aunque en el VPS hubiera uno nuevo.
     */
    private String urlExterna() {
        for (String url : new String[]{ruleta.config().getString("resource-pack.url-externa", ""),
                plugin.getConfig().getString("resourcepack.url", "")}) {
            if (url != null && !url.isBlank()) return url.trim();
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (ruleta.config().getBoolean("resource-pack.enviar-al-entrar", true)) {
            // un pequeño retraso para que el cliente termine de cargar el mundo
            Bukkit.getScheduler().runTaskLater(plugin, () -> send(e.getPlayer()), 20L);
        }
    }
}
