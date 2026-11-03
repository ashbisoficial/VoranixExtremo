import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Pasa el script muerte_ojo.yml de VoranixConditions al Ojo en HD (ver MejorarOjo.java):
// cada fotograma era una letra (\ue100 + i) y ahora son 7 caracteres (las 4 piezas de 256 px).
// Además le pone al title el color #4C5C24: el shader del pack de VoranixExtremo lo reconoce y le
// quita la sombra (sin eso, cada fotograma lleva una copia oscura corrida detrás).
// Uso: java tools/ojo/ScriptOjoHD.java muerte_ojo.yml salida.yml
public class ScriptOjoHD {
    static final int CH_ORIGINAL = 0xE100, CH_PIEZAS = 0xE200, CH_JUNTAR = 0xE1F0, CH_ATRAS = 0xE1F1;

    public static void main(String[] a) throws Exception {
        String yml = Files.readString(Path.of(a[0]), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\\{\"text\":\"\\\\u([0-9a-fA-F]{4})\",\"font\":\"ojo:muerte\"\\}").matcher(yml);
        StringBuilder sb = new StringBuilder();
        int cambiados = 0;
        while (m.find()) {
            int i = Integer.parseInt(m.group(1), 16) - CH_ORIGINAL;
            int c = CH_PIEZAS + 4 * i;
            String texto = esc(c) + esc(CH_JUNTAR) + esc(c + 1) + esc(CH_ATRAS) + esc(c + 2) + esc(CH_JUNTAR) + esc(c + 3);
            String nuevo = "{\"text\":\"" + texto + "\",\"font\":\"ojo:muerte\",\"color\":\"#4C5C24\"}";
            m.appendReplacement(sb, Matcher.quoteReplacement(nuevo));
            cambiados++;
        }
        m.appendTail(sb);
        String salida = sb.toString().replace(
                "# Necesita el resource pack OjoMuerte-pack.zip (cuadros de la animación + sonido).",
                "# Necesita el resource pack con el Ojo en HD (cada cuadro son 4 piezas de 256 px) y el shader\n"
                        + "# de VoranixExtremo (el color #4C5C24 le quita la sombra al title): el pack combinado del VPS.");
        Files.writeString(Path.of(a[1]), salida, StandardCharsets.UTF_8);
        System.out.println(cambiados + " fotogramas cambiados -> " + a[1]);
    }

    static String esc(int c) {
        return String.format("\\u%04x", c);
    }
}
