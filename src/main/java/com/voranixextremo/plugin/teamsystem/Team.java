package com.voranixextremo.plugin.teamsystem;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Un team: su nombre (tal como lo escribió el líder), su tag (abreviatura corta, por ejemplo VRX), su
 * color (código de color, por ejemplo "&c" o "&#a855f7"), el líder y los integrantes (incluye al líder).
 */
public class Team {

    private final String name;
    private final String tag;
    private String color;
    private UUID leader;
    // el orden importa: si el líder se va, el nuevo líder es el que lleva más tiempo
    private final Set<UUID> members = Collections.synchronizedSet(new LinkedHashSet<>());

    public Team(String name, String tag, String color, UUID leader) {
        this.name = name;
        this.tag = tag;
        this.color = color;
        this.leader = leader;
        this.members.add(leader);
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public String getColor() {
        return color;
    }

    void setColor(String color) {
        this.color = color;
    }

    /** Nombre en minúsculas: así "Dragones" y "dragones" son el mismo team. */
    public String getKey() {
        return key(name);
    }

    public static String key(String name) {
        return name.toLowerCase();
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    /** Copia de los integrantes, en orden de llegada. */
    public Set<UUID> getMembers() {
        synchronized (members) {
            return new LinkedHashSet<>(members);
        }
    }

    public int size() {
        return members.size();
    }

    void addMember(UUID uuid) {
        members.add(uuid);
    }

    void removeMember(UUID uuid) {
        members.remove(uuid);
    }
}
