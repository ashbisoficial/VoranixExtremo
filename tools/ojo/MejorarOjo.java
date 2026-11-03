import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.stream.IntStream;

// Sube la resolución de la animación del Ojo de la muerte (resource pack OjoMuerte).
//
// Los 120 fotogramas originales son de 256 px y van en una sola "letra" de la fuente ojo:muerte;
// Minecraft 1.20.1 no acepta letras de más de 256 px, así que en pantalla se estiraban casi al
// doble y se veían pixelados. Acá cada fotograma se agranda a 512 px con Lanczos (respetando la
// transparencia), se le da un poco de nitidez y se corta en 4 piezas de 256 px, como la ruleta.
// Queda del mismo tamaño y en el mismo lugar de la pantalla que antes.
//
// Uso: java tools/ojo/MejorarOjo.java <carpeta del pack original descomprimido> <carpeta de salida>
//
// Cada fotograma i (0..119) pasa a escribirse con 7 caracteres de la fuente ojo:muerte:
//   PIEZA(i,0) JUNTAR PIEZA(i,1) ATRAS PIEZA(i,2) JUNTAR PIEZA(i,3)
// con PIEZA(i,q) = U+E200 + 4*i + q, JUNTAR = U+E1F0 y ATRAS = U+E1F1.
public class MejorarOjo {
    static final int FRAMES = 120, ORIGEN = 256, DESTINO = 512, TILE = 256;
    // tamaño en pantalla del original: alto 56, ascent 25 (va de 25 arriba de la línea a 31 abajo)
    static final int ALTO = 56, ASCENT = 25;
    static final int CH_PIEZAS = 0xE200, CH_JUNTAR = 0xE1F0, CH_ATRAS = 0xE1F1;
    static final double NITIDEZ = 0.35;

    public static void main(String[] a) throws Exception {
        Path origen = Paths.get(a[0]), salida = Paths.get(a[1]);
        Path tex = salida.resolve("assets/ojo/textures/muerte");
        Path font = salida.resolve("assets/ojo/font");
        Files.createDirectories(tex);
        Files.createDirectories(font);
        int mitad = ALTO / 2;

        StringBuilder json = new StringBuilder("{\n  \"providers\": [\n");
        json.append(String.format("    {\"type\": \"space\", \"advances\": {\"\\u%04X\": -1, \"\\u%04X\": %d}}", CH_JUNTAR, CH_ATRAS, -(ALTO + 1)));
        IntStream.range(0, FRAMES).parallel().forEach(i -> {
            try {
                BufferedImage img = ImageIO.read(origen.resolve(String.format("assets/ojo/textures/muerte/f%03d.png", i)).toFile());
                BufferedImage grande = nitidez(lanczos(img, DESTINO), NITIDEZ);
                for (int q = 0; q < 4; q++) {
                    BufferedImage pieza = grande.getSubimage((q % 2) * TILE, (q / 2) * TILE, TILE, TILE);
                    BufferedImage copia = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
                    copia.setRGB(0, 0, TILE, TILE, pieza.getRGB(0, 0, TILE, TILE, null, 0, TILE), 0, TILE);
                    // Minecraft mide el ancho hasta el último píxel visible: así todas las piezas miden lo mismo
                    if ((copia.getRGB(TILE - 1, 0) >>> 24) == 0) copia.setRGB(TILE - 1, 0, 0x01000000);
                    ImageIO.write(copia, "png", tex.resolve(String.format("f%03d_%d.png", i, q)).toFile());
                }
            } catch (Exception e) {
                throw new RuntimeException("fotograma " + i, e);
            }
        });
        for (int i = 0; i < FRAMES; i++) for (int q = 0; q < 4; q++) {
            // fila de arriba: de 25 arriba de la línea a 3 abajo; fila de abajo: de 3 a 31 abajo
            int ascent = q < 2 ? ASCENT : ASCENT - mitad;
            json.append(String.format(",\n    {\"type\": \"bitmap\", \"file\": \"ojo:muerte/f%03d_%d.png\", \"height\": %d, \"ascent\": %d, \"chars\": [\"\\u%04X\"]}",
                    i, q, mitad, ascent, CH_PIEZAS + 4 * i + q));
        }
        json.append("\n  ]\n}\n");
        Files.writeString(font.resolve("muerte.json"), json);
        System.out.println(FRAMES + " fotogramas a " + DESTINO + " px en 4 piezas -> " + salida.toAbsolutePath());
    }

    /** Texto del fotograma i para el title (fuente ojo:muerte). */
    static String texto(int i) {
        int c = CH_PIEZAS + 4 * i;
        return new StringBuilder().appendCodePoint(c).appendCodePoint(CH_JUNTAR).appendCodePoint(c + 1).appendCodePoint(CH_ATRAS)
                .appendCodePoint(c + 2).appendCodePoint(CH_JUNTAR).appendCodePoint(c + 3).toString();
    }

    /** Agranda con Lanczos-3 en color premultiplicado (así los bordes transparentes no quedan con halo). */
    static BufferedImage lanczos(BufferedImage src, int n) {
        int w = src.getWidth(), h = src.getHeight();
        double[][] pre = new double[4][w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = src.getRGB(x, y);
            double al = ((p >>> 24) & 255) / 255.0;
            int k = y * w + x;
            pre[3][k] = al;
            pre[0][k] = ((p >> 16) & 255) * al;
            pre[1][k] = ((p >> 8) & 255) * al;
            pre[2][k] = (p & 255) * al;
        }
        double[][] hor = escalarEje(pre, w, h, n, true);
        double[][] fin = escalarEje(hor, n, h, n, false);
        BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            int k = y * n + x;
            double al = Math.max(0, Math.min(1, fin[3][k]));
            if (al < 1 / 255.0) continue;
            int r = c255(fin[0][k] / al), g = c255(fin[1][k] / al), b = c255(fin[2][k] / al);
            out.setRGB(x, y, ((int) Math.round(al * 255) << 24) | (r << 16) | (g << 8) | b);
        }
        return out;
    }

    static double[][] escalarEje(double[][] in, int w, int h, int nuevo, boolean horizontal) {
        int largo = horizontal ? w : h, ow = horizontal ? nuevo : w, oh = horizontal ? h : nuevo;
        double esc = (double) largo / nuevo;
        double[][] out = new double[4][ow * oh];
        for (int i = 0; i < nuevo; i++) {
            double centro = (i + 0.5) * esc - 0.5;
            int desde = (int) Math.floor(centro) - 2, hasta = (int) Math.floor(centro) + 3;
            double[] pesos = new double[hasta - desde + 1];
            double suma = 0;
            for (int j = desde; j <= hasta; j++) suma += pesos[j - desde] = sinc(centro - j) * sinc((centro - j) / 3);
            for (int otro = 0; otro < (horizontal ? h : w); otro++) {
                for (int c = 0; c < 4; c++) {
                    double v = 0;
                    for (int j = desde; j <= hasta; j++) {
                        int jj = Math.max(0, Math.min(largo - 1, j));
                        int k = horizontal ? otro * w + jj : jj * w + otro;
                        v += in[c][k] * pesos[j - desde];
                    }
                    int ko = horizontal ? otro * ow + i : i * ow + otro;
                    out[c][ko] = v / suma;
                }
            }
        }
        return out;
    }

    static double sinc(double x) {
        if (Math.abs(x) < 1e-9) return 1;
        if (Math.abs(x) >= 3) return 0;
        double px = Math.PI * x;
        return Math.sin(px) / px;
    }

    /** Máscara de enfoque suave sobre el color (no toca la transparencia). */
    static BufferedImage nitidez(BufferedImage img, double fuerza) {
        int n = img.getWidth();
        BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            int p = img.getRGB(x, y);
            if ((p >>> 24) == 0) continue;
            double[] suma = new double[3];
            int cuenta = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int xx = Math.max(0, Math.min(n - 1, x + dx)), yy = Math.max(0, Math.min(n - 1, y + dy));
                int q = img.getRGB(xx, yy);
                if ((q >>> 24) == 0) continue;
                suma[0] += (q >> 16) & 255; suma[1] += (q >> 8) & 255; suma[2] += q & 255;
                cuenta++;
            }
            int[] c = {(p >> 16) & 255, (p >> 8) & 255, p & 255};
            for (int k = 0; k < 3; k++) c[k] = c255(c[k] + fuerza * (c[k] - suma[k] / cuenta));
            out.setRGB(x, y, (p & 0xFF000000) | (c[0] << 16) | (c[1] << 8) | c[2]);
        }
        return out;
    }

    static int c255(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
