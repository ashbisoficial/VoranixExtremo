package com.voranixextremo.plugin.shops;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.List;

/**
 * Una tienda de aldeano "chef", definida en config.yml -> shops.<id>: qué profesión
 * vanilla usa (el look más parecido disponible) y qué comidas (food.items) vende.
 */
public class ShopType {

    private final String id;
    private final String displayName;
    private final Villager.Profession profession;
    private final List<String> sells;

    private ShopType(String id, String displayName, Villager.Profession profession, List<String> sells) {
        this.id = id;
        this.displayName = displayName;
        this.profession = profession;
        this.sells = sells;
    }

    public static ShopType fromConfig(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        String professionName = section.getString("profession", "BUTCHER");
        Villager.Profession profession;
        try {
            profession = Villager.Profession.valueOf(professionName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            profession = Villager.Profession.BUTCHER;
        }
        List<String> sells = new ArrayList<>(section.getStringList("sells"));
        return new ShopType(id.toLowerCase(), displayName, profession, sells);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Villager.Profession getProfession() {
        return profession;
    }

    public List<String> getSells() {
        return sells;
    }
}
