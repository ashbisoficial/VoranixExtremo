import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Junta varios resource packs en uno: el primero manda (su pack.mcmeta y pack.png), de los demás
 * se agregan sus archivos. Si un archivo se repite, avisa y se queda con el del primero.
 * Uso: java JuntarPacks.java salida.zip "descripcion" principal.zip otro.zip ...
 */
public class JuntarPacks {
    public static void main(String[] a) throws Exception {
        Path salida = Paths.get(a[0]);
        String descripcion = a[1];
        Map<String, byte[]> archivos = new TreeMap<>();
        for (int i = 2; i < a.length; i++) {
            boolean principal = i == 2;
            try (ZipFile zip = new ZipFile(a[i])) {
                for (ZipEntry e : Collections.list(zip.entries())) {
                    if (e.isDirectory()) continue;
                    String nombre = e.getName();
                    if (!principal && (nombre.equals("pack.mcmeta") || nombre.equals("pack.png"))) continue;
                    byte[] datos = zip.getInputStream(e).readAllBytes();
                    if (archivos.containsKey(nombre)) {
                        System.out.println("REPETIDO (se queda el primero): " + nombre + " en " + a[i]);
                        continue;
                    }
                    archivos.put(nombre, datos);
                }
            }
            System.out.println("agregado " + a[i]);
        }
        String mcmeta = "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"" + descripcion + "\"\n  }\n}\n";
        archivos.put("pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(salida))) {
            for (Map.Entry<String, byte[]> e : archivos.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                ze.setTime(0L);
                out.putNextEntry(ze);
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        System.out.println(archivos.size() + " archivos -> " + salida);
    }
}
