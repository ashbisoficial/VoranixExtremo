package com.voranixextremo.plugin.teamsystem;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Un team: su nombre (tal como lo escribió el líder), el líder y los integrantes (incluye al líder). */
public class Team {

    private final String name;
    private UUID leader;
    // el orden importa: si el líder se va, el nuevo líder es el que lleva más tiempo
    private final Set<UUID> members = Collections.synchronizedSet(new LinkedHashSet<>());

    public Team(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
    }

    public String getName() {
        return name;
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
