package com.voranixextremo.plugin.teamsystem;

import com.voranixextremo.plugin.voranixextremo.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menús del team (/team menu):
 *  - el principal: datos del team (nombre, tag, líder, color), los integrantes con su cabeza
 *    (líder o integrante, conectado o no), el botón del color y el del chat del team;
 *  - el de colores: el líder elige el color del team (team.yml → colores).
 * Todos los textos están en team.yml → gui.
 */
public class TeamGui implements Listener {

    private static final int FILAS = 5;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_COLOR = 39;
    private static final int SLOT_CHAT = 41;
    private static final int SLOT_CERRAR = 44;
    // las cabezas de los integrantes: los teams son de 3, en el centro de la fila del medio
    private static final int[] SLOTS_MIEMBROS = {21, 22, 23};

    private final Plugin plugin;
    private final TeamManager teams;
    /** Perfil premium (con su skin de Mojang) de cada nombre ya buscado; se busca una sola vez. */
    private final Map<String, PlayerProfile> perfiles = new ConcurrentHashMap<>();
    /** Nombre → desde cuándo se puede volver a buscar (mientras se busca, o un rato después de un error). */
    private final Map<String, Long> esperar = new ConcurrentHashMap<>();
    /** Después de un error (Mojang caído o límite de pedidos) se reintenta a los 3 minutos. */
    private static final long REINTENTAR_MS = 3 * 60 * 1000L;

    public TeamGui(Plugin plugin, TeamManager teams) {
        this.plugin = plugin;
        this.teams = teams;
    }

    /** Marca los inventarios de este menú y guarda qué hace cada casilla. */
    private static final class Menu implements InventoryHolder {
        private final String teamKey;
        private final Map<Integer, Runnable> acciones = new HashMap<>();
        private Inventory inventario;

        private Menu(String teamKey) {
            this.teamKey = teamKey;
        }

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    // ------------------------------------------------------------------ menú principal

    public void abrir(Player player, Team team) {
        Menu menu = new Menu(team.getKey());
        Inventory inv = Bukkit.createInventory(menu, FILAS * 9, texto(team, teams.gui("titulo")));
        menu.inventario = inv;
        rellenar(inv);

        boolean lider = team.isLeader(player.getUniqueId());
        String liderNombre = TeamManager.nombreDe(team.getLeader());
        inv.setItem(SLOT_INFO, item(material(team), texto(team, teams.gui("info.nombre")),
                lineas(team, teams.guiLista("info.lore"), "%lider%", liderNombre,
                        "%cantidad%", String.valueOf(team.size()), "%max%", String.valueOf(teams.getCapacidad()))));

        int i = 0;
        for (UUID uuid : team.getMembers()) {
            if (i >= SLOTS_MIEMBROS.length) break;
            int slot = SLOTS_MIEMBROS[i++];
            inv.setItem(slot, cabeza(team, uuid));
            // si su skin premium todavía no se buscó, se busca y la cabeza se cambia cuando llega
            buscarPerfil(TeamManager.nombreDe(uuid), () -> {
                if (inv.getViewers().isEmpty()) return;
                Team actual = teams.getTeamByName(team.getName());
                if (actual != null) inv.setItem(slot, cabeza(actual, uuid));
            });
        }

        inv.setItem(SLOT_COLOR, item(material(team), texto(team, teams.gui("color.nombre")),
                lineas(team, teams.guiLista(lider ? "color.lore" : "color.lore-no-lider"))));
        if (lider) {
            menu.acciones.put(SLOT_COLOR, () -> abrirColores(player, team));
        }

        boolean enChat = teams.isEnChatDeTeam(player.getUniqueId());
        inv.setItem(SLOT_CHAT, item(enChat ? Material.WRITABLE_BOOK : Material.BOOK, texto(team, teams.gui("chat.nombre")),
                lineas(team, teams.guiLista(enChat ? "chat.lore-activo" : "chat.lore-inactivo"))));
        menu.acciones.put(SLOT_CHAT, () -> {
            teams.setEnChatDeTeam(player.getUniqueId(), !enChat);
            player.sendMessage(teams.msg(enChat ? "chat-leave" : "chat-join"));
            abrir(player, team);
        });

        inv.setItem(SLOT_CERRAR, item(Material.BARRIER, texto(team, teams.gui("cerrar")), List.of()));
        menu.acciones.put(SLOT_CERRAR, player::closeInventory);

        player.openInventory(inv);
    }

    private ItemStack cabeza(Team team, UUID uuid) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        String nombre = TeamManager.nombreDe(uuid);
        ponerSkin(meta, uuid, nombre);
        meta.setDisplayName(texto(team, teams.gui("miembro.nombre").replace("%player%", nombre)));
        List<String> lore = new ArrayList<>();
        lore.add(texto(team, teams.gui(team.isLeader(uuid) ? "miembro.lider" : "miembro.integrante")));
        lore.add(texto(team, teams.gui(Bukkit.getPlayer(uuid) != null ? "miembro.conectado" : "miembro.desconectado")));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * La cabeza con la skin de la cuenta premium que tiene ese nombre (la de Mojang). Se busca una vez
     * por nombre en Mojang (buscarPerfil) y se guarda en teams-data.yml; mientras no llega se usa la
     * última guardada o, si no hay, la cabeza por UUID.
     */
    private void ponerSkin(SkullMeta meta, UUID uuid, String nombre) {
        PlayerProfile premium = perfiles.get(nombre.toLowerCase());
        String skin = premium != null ? premium.getTextures().getSkin().toString() : teams.getSkin(uuid);
        PlayerProfile cabeza = skin == null ? null : perfilCabeza(skin);
        if (cabeza != null) meta.setOwnerProfile(cabeza);
        else meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
    }

    /**
     * La cabeza con esa skin pero sin el nombre ni el UUID del jugador (UUID inventado a partir de la
     * URL). Mods como CustomPlayerModels reemplazan la cabeza de un jugador que usa un modelo y la dejan
     * invisible; con un perfil anónimo el cliente solo ve la textura y dibuja la cabeza normal.
     */
    private static PlayerProfile perfilCabeza(String skin) {
        try {
            UUID id = UUID.nameUUIDFromBytes(("VoranixCabeza:" + skin).getBytes(StandardCharsets.UTF_8));
            PlayerProfile perfil = Bukkit.createPlayerProfile(id, null);
            perfil.getTextures().setSkin(new URL(skin));
            return perfil;
        } catch (Exception e) {
            return null; // URL inválida
        }
    }


    /**
     * Busca en Mojang (fuera del hilo principal) el perfil premium de ese nombre con su skin, y cuando
     * llega corre "listo" en el hilo principal. Cuando sale bien no se vuelve a buscar; si falla (o no
     * es una cuenta premium) se queda con la cabeza que tenía y se reintenta a los 3 minutos.
     */
    private void buscarPerfil(String nombre, Runnable listo) {
        String key = nombre.toLowerCase();
        long ahora = System.currentTimeMillis();
        if (perfiles.containsKey(key) || esperar.getOrDefault(key, 0L) > ahora) return;
        esperar.put(key, ahora + REINTENTAR_MS);
        Bukkit.createPlayerProfile(nombre).update().whenComplete((perfil, error) -> {
            if (error != null || perfil == null || perfil.getUniqueId() == null || perfil.getTextures().getSkin() == null) {
                plugin.getLogger().warning("No se pudo obtener la skin premium de " + nombre + ": "
                        + (error != null ? error.getMessage() : "no es una cuenta premium o no tiene skin")
                        + " (se reintenta en unos minutos)");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                perfiles.put(key, perfil);
                for (UUID uuid : miembrosConNombre(nombre)) teams.setSkin(uuid, perfil.getTextures().getSkin().toString());
                listo.run();
            });
        });
    }


    private List<UUID> miembrosConNombre(String nombre) {
        List<UUID> uuids = new ArrayList<>();
        for (Team team : teams.getTeams()) {
            for (UUID uuid : team.getMembers()) if (TeamManager.nombreDe(uuid).equalsIgnoreCase(nombre)) uuids.add(uuid);
        }
        return uuids;
    }

    // ------------------------------------------------------------------ menú de colores

    public void abrirColores(Player player, Team team) {
        Menu menu = new Menu(team.getKey());
        List<TeamManager.ColorOpcion> colores = teams.getColores();
        int filas = Math.min(6, Math.max(2, (colores.size() + 8) / 9 + 1));
        Inventory inv = Bukkit.createInventory(menu, filas * 9, texto(team, teams.gui("titulo-colores")));
        menu.inventario = inv;
        rellenar(inv);

        for (int i = 0; i < colores.size() && i < (filas - 1) * 9; i++) {
            TeamManager.ColorOpcion color = colores.get(i);
            boolean actual = color.codigo().equalsIgnoreCase(team.getColor());
            String nombre = teams.gui("opcion-color.nombre").replace("%codigo%", color.codigo()).replace("%nombre%", color.nombre());
            ItemStack item = item(color.material(), texto(team, nombre),
                    lineas(team, teams.guiLista(actual ? "opcion-color.lore-actual" : "opcion-color.lore")));
            if (actual) brillo(item);
            inv.setItem(i, item);
            menu.acciones.put(i, () -> {
                // al hacer clic se vuelve a revisar: puede haber dejado de ser líder con el menú abierto
                Team suyo = teams.getTeam(player.getUniqueId());
                if (suyo == null || !suyo.isLeader(player.getUniqueId())) {
                    player.closeInventory();
                    player.sendMessage(teams.msg("solo-lider"));
                    return;
                }
                if (!actual) {
                    teams.cambiarColor(suyo, color);
                    teams.avisar(suyo, teams.msg("color-cambiado", "%player%", player.getName(),
                            "%color%", color.codigo(), "%nombre-color%", color.nombre()));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                }
                abrir(player, suyo);
            });
        }

        int volver = (filas - 1) * 9 + 4;
        inv.setItem(volver, item(Material.ARROW, texto(team, teams.gui("volver")), List.of()));
        menu.acciones.put(volver, () -> abrir(player, team));
        player.openInventory(inv);
    }

    // ------------------------------------------------------------------ clics

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true); // nada se puede sacar ni meter
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() != event.getSlot() || event.getClickedInventory() != event.getView().getTopInventory()) return;
        // si el team ya no existe o ya no es suyo, se cierra
        Team team = teams.getTeam(player.getUniqueId());
        if (team == null || !team.getKey().equals(menu.teamKey)) {
            player.closeInventory();
            return;
        }
        Runnable accion = menu.acciones.get(event.getSlot());
        if (accion != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
            accion.run();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ ayudas

    private Material material(Team team) {
        for (TeamManager.ColorOpcion opcion : teams.getColores()) {
            if (opcion.codigo().equalsIgnoreCase(team.getColor())) return opcion.material();
        }
        return Material.WHITE_BANNER;
    }

    private void rellenar(Inventory inv) {
        ItemStack vidrio = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, vidrio);
    }

    private String texto(Team team, String texto) {
        return ColorUtils.color(teams.aplicar(texto, team));
    }

    private List<String> lineas(Team team, List<String> lineas, String... reemplazos) {
        List<String> resultado = new ArrayList<>();
        for (String linea : lineas) {
            for (int i = 0; i + 1 < reemplazos.length; i += 2) linea = linea.replace(reemplazos[i], reemplazos[i + 1]);
            resultado.add(texto(team, linea));
        }
        return resultado;
    }

    private static ItemStack item(Material material, String nombre, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(nombre);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void brillo(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.addEnchant(Enchantment.LUCK, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }
}
