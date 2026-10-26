package com.voranixextremo.plugin.voranixextremo;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtils {

    private static final Pattern HEX_AMPERSAND = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_LLAVES = Pattern.compile("\\{#([A-Fa-f0-9]{6})}");

    private ColorUtils() {
    }

    public static String color(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        String conHex = reemplazarHex(reemplazarHex(texto, HEX_AMPERSAND), HEX_LLAVES);
        return ChatColor.translateAlternateColorCodes('&', conHex);
    }

    public static List<String> color(List<String> textos) {
        List<String> resultado = new ArrayList<>(textos.size());
        for (String texto : textos) {
            resultado.add(color(texto));
        }
        return resultado;
    }

    public static String strip(String texto) {
        String limpio = ChatColor.stripColor(color(texto));
        return limpio == null ? "" : limpio;
    }

    private static String reemplazarHex(String texto, Pattern patron) {
        Matcher matcher = patron.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String color = ChatColor.of("#" + matcher.group(1)).toString();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(color));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
