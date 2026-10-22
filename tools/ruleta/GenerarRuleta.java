import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

// Genera el resource pack de la ruleta (resourcepack/Ruleta) con las animaciones de
// "Ruleta del fénix.html": entrada con rebote, desenfoque al girar, llamas y alas que
// ondulan, estrellas y brasas, y al final se tiñe del color ganador, tiembla y desaparece.
// Ejecutar desde la raíz de VoranixExtremo: java tools/ruleta/GenerarRuleta.java [carpeta-vista-previa]
//
// Arte (tools/ruleta/art, sacado del HTML):
//   ruleta.png        el dibujo completo (1254 x 1254)
//   sin_marco.jpg     el mismo dibujo sin aro ni flechas: de aquí se sacan las llamas desplazadas
//   mascara.png       R = cuánto se mueve el fuego, G = disco, B = flecha de arriba (a 627 px)
//   mascara_fija.png  blanco = lo que nunca se deforma (disco, aro y flechas)
//
// La ruleta se arma con varias "letras" encimadas en el title:
//   disco_XX     los 8 colores, girados (4 piezas por fotograma)
//   borroso_XX   el disco con desenfoque de movimiento, para cuando gira rápido (1 pieza a media resolución)
//   fijo         aro y fénix
//   fuego_XX     llamas, alas, estrellas, brasas y el brillo del fénix, en loop
//   flecha_X     la gema de arriba, inclinada cuando una línea la empuja
//   tinte_K_N    el disco teñido del color ganador K (N = intensidad; la última es el latido) + aura (4 piezas)
//   entrada_XX   la ruleta completa apareciendo (1 pieza a media resolución)
//   salida_K_XX  la ruleta del color K desapareciendo (1 pieza a media resolución)
// También escribe src/main/resources/ruleta-layout.json para que el plugin sepa qué hay en el pack.
public class GenerarRuleta {
    // --- medidas del dibujo original (en píxeles de art/ruleta.png) ---
    static final double HX = 620, HY = 593;      // centro del fénix = centro de giro
    static final double HUB_R = 111;             // radio del fénix con su borde
    static final double ARO_R = 400;             // borde exterior del aro dorado
    static final int[] FONDO = {2, 1, 10};       // color del fondo negro del dibujo
    static final int CROP_X = 120, CROP_Y = 93, CROP = 1000; // recorte cuadrado que se muestra
    static final double ARTE = 1254;             // lado del dibujo (las coordenadas del HTML van de 0 a 1)
    // Líneas entre colores del dibujo, en grados desde arriba en sentido horario (medidas desde el centro)
    static final double[] DIVISORES = {0.6, 29.3, 63.6, 99.1, 133.1, 164.2, 198.3, 229.9, 262.1, 296.8, 331.8};
    static final double LINEA = 4;               // media línea divisoria, en píxeles del dibujo
    // Flecha de arriba
    static final double[][] FLECHA = {{545, 160}, {620, 80}, {696, 160}, {623, 306}, {617, 306}};
    static final double PIVOTE_X = 620, PIVOTE_Y = 150;
    // Destellos de cuatro puntas del dibujo {x, y, radio} y radio del fénix, tal cual en el HTML
    static final double[][] ESTRELLAS = {{101, 341, 34}, {1134, 216, 40}, {103, 817, 34}, {1131, 941, 42}, {281, 1102, 26}};
    static final double FENIX_R = 112;

    // --- la ruleta: 8 colores, uno de cada uno, en sentido horario desde arriba ---
    // {id, segmento del dibujo del que se copia, giro de tono en grados}
    static final Object[][] COLORES = {
            {"rojo", 9, 0.0}, {"naranjo", 8, 0.0}, {"amarillo", 0, 0.0}, {"verde", 1, 0.0},
            {"cian", 2, -22.0}, {"azul", 5, 0.0}, {"morado", 3, 0.0}, {"rosa", 4, 0.0},
    };

    // --- salida ---
    static final int SIZE = 512;                 // cada capa se dibuja a 512 px...
    static final int TILE = 256;                 // ...y se corta en 4 piezas, porque Minecraft no acepta glifos de más de 256 px
    static final int MEDIA = 256;                // las capas a media resolución van en una sola pieza (ocupa 4 veces menos memoria)
    static final int SEG = COLORES.length;
    static final int POR_SEGMENTO = 8;
    static final int DISCO = SEG * POR_SEGMENTO; // 64 fotogramas del disco
    static final int BORROSO = DISCO;            // 64 fotogramas del disco desenfocado (a media resolución)
    static final double DESENFOQUE = 7;          // medio ancho del desenfoque, en grados
    static final int FUEGO = 30;                 // fotogramas de las llamas...
    static final int FUEGO_TICKS = 2;            // ...cada uno dura 2 ticks: el loop dura 3 segundos
    static final double LOOP = FUEGO * FUEGO_TICKS / 20.0;
    static final double FUERZA_FUEGO = 1.3;      // el control "Fuego" del HTML
    static final double[] FLECHA_ANG = {0, -5, -10, -15};
    static final double[] TINTE = {0.25, 0.5, 0.75, 1, 1}; // el último es el latido (más claro, aura más fuerte)
    // La entrada y la salida se agrandan (rebote hasta 1,1x) y giran: van en un recuadro más grande
    // para que no se corten. Alto en unidades de la fuente (par, para quedar centrado) y su escala.
    static final int ALTO_AMPLIO = 82;
    static final double AMPLIO = ALTO_AMPLIO / (2.0 * GenerarRuleta.PIEZA);
    static final int ENTRADA = 24;               // uno por tick: 1,2 s como en el HTML
    static final int SALIDA = 15;                // uno por tick: 0,75 s
    // Cada pieza mide PIEZA de alto; el title la dibuja a 4x, así la ruleta completa mide 2 * 32 * 4 = 256 px de GUI.
    // Con escala de GUI 2 eso son 512 px de pantalla: justo la resolución de las capas (se ve nítida, 1 a 1).
    // La fila de arriba usa ascent = alto (el máximo que acepta Minecraft) y la de abajo 0.
    static final int PIEZA = 32;
    // Caracteres: los de 4 piezas usan 4 seguidos (arriba-izq, arriba-der, abajo-izq, abajo-der)
    static final int CH_DISCO = 0xE000, CH_FUEGO = 0xE100, CH_FIJO = 0xE180, CH_FLECHA = 0xE184, CH_BORROSO = 0xE1A0,
            CH_TINTE = 0xE200, CH_ENTRADA = 0xE2A0, CH_SALIDA = 0xE2C0;
    static final int CH_MOVER = 0xE1F0;          // 5 espacios: corren la ruleta -2, -1, 0, 1 y 2 (para el temblor)
    static final int CH_JUNTAR = 0xE1FE;         // -1: pega la pieza derecha a la izquierda (sin el espacio de 1 px)
    static final int CH_ATRAS = 0xE1FF;          // vuelve al inicio de la fila: -(2 * PIEZA + 1)

    static BufferedImage src, plate, mascara, mascaraFija;
    static double[] radio = new double[3600];    // borde del disco cada 0,1 grados
    static Path2D flecha;
    static int[] tinteColor = new int[SEG];      // color de cada segmento de la ruleta (en el orden del layout)

    public static void main(String[] args) throws Exception {
        Path art = Paths.get("tools/ruleta/art");
        src = rgb(ImageIO.read(art.resolve("ruleta.png").toFile()));
        plate = rgb(ImageIO.read(art.resolve("sin_marco.jpg").toFile()));
        mascara = rgb(ImageIO.read(art.resolve("mascara.png").toFile()));
        mascaraFija = rgb(ImageIO.read(art.resolve("mascara_fija.png").toFile()));
        Path vista = args.length > 0 ? Paths.get(args[0]) : null;
        medirBorde();
        flecha = poligono(FLECHA);
        for (int k = 0; k < SEG; k++) tinteColor[k] = muestrearColor(k);

        Path root = Paths.get("resourcepack/Ruleta");
        Path tex = root.resolve("assets/ruleta/textures/font");
        Path font = root.resolve("assets/ruleta/font");
        if (Files.exists(tex)) try (var s = Files.list(tex)) { for (Path p : s.toList()) Files.delete(p); }
        Files.createDirectories(tex);
        Files.createDirectories(font);

        StringBuilder json = new StringBuilder("{\n  \"providers\": [\n");
        json.append(String.format("    {\"type\": \"space\", \"advances\": {\"\\u%04X\": %d, \"\\u%04X\": %d, \"\\u%04X\": -2, \"\\u%04X\": -1, \"\\u%04X\": 0, \"\\u%04X\": 1, \"\\u%04X\": 2}}",
                CH_JUNTAR, -1, CH_ATRAS, -(2 * PIEZA + 1), CH_MOVER, CH_MOVER + 1, CH_MOVER + 2, CH_MOVER + 3, CH_MOVER + 4));

        // disco nítido
        BufferedImage disco0 = null;
        for (int i = 0; i < DISCO; i++) {
            double giro = 360.0 * i / DISCO;
            BufferedImage img = render(SIZE, (x, y) -> discoEn(x, y, giro, 0));
            if (i == 0) disco0 = img;
            json.append(glifo(tex, String.format("disco_%02d", i), img, CH_DISCO + 4 * i));
        }
        log("disco");

        // disco desenfocado (cuando gira rápido no se nota la media resolución)
        BufferedImage borroso0 = null;
        for (int i = 0; i < BORROSO; i++) {
            double giro = 360.0 * i / BORROSO;
            BufferedImage img = render(MEDIA, (x, y) -> discoEn(x, y, giro, DESENFOQUE));
            if (i == 0) borroso0 = img;
            json.append(glifoGrande(tex, String.format("borroso_%02d", i), img, CH_BORROSO + i));
        }
        log("borroso");

        // aro y fénix: no se mueven, van en una sola capa
        BufferedImage fijo0 = render(SIZE, (x, y) -> Math.hypot(x - HX, y - HY) < ARO_R + 2 ? fijo(x, y, 0) : 0);
        json.append(glifo(tex, "fijo", fijo0, CH_FIJO));

        // llamas, alas, estrellas, brasas y brillo del fénix (en loop)
        BufferedImage[] fuegos = new BufferedImage[FUEGO];
        for (int f = 0; f < FUEGO; f++) {
            double t = (double) f / FUEGO;
            BufferedImage img = render(SIZE, (x, y) -> Math.hypot(x - HX, y - HY) >= ARO_R - 2 ? fijo(x, y, t) : 0);
            brillos(img, t);
            fuegos[f] = img;
            json.append(glifo(tex, String.format("fuego_%02d", f), img, CH_FUEGO + 4 * f));
        }
        log("fuego");

        // flecha
        BufferedImage[] flechas = new BufferedImage[FLECHA_ANG.length];
        for (int k = 0; k < FLECHA_ANG.length; k++) {
            double a = FLECHA_ANG[k];
            flechas[k] = render(SIZE, (x, y) -> flechaEn(x, y, a));
            json.append(glifo(tex, "flecha_" + k, flechas[k], CH_FLECHA + 4 * k));
        }

        // disco teñido del color ganador + aura (va encima de todo menos la flecha)
        BufferedImage[][] tintes = new BufferedImage[SEG][TINTE.length];
        for (int k = 0; k < SEG; k++) for (int n = 0; n < TINTE.length; n++) {
            int kk = k;
            double amt = TINTE[n];
            boolean latido = n == TINTE.length - 1;
            tintes[k][n] = render(SIZE, (x, y) -> tinteEn(x, y, kk, amt, latido));
            json.append(glifo(tex, "tinte_" + k + "_" + n, tintes[k][n], CH_TINTE + 4 * (k * TINTE.length + n)));
        }
        log("tinte");

        // entrada: aparece chica, se pasa un poco de tamaño y se endereza
        BufferedImage[] entradas = new BufferedImage[ENTRADA];
        for (int i = 0; i < ENTRADA; i++) {
            double e = (i + 0.5) / 20.0, p = clamp(e / 1.2);
            double s = 0.15 + 0.85 * easeBack(p), o = clamp(e / 0.4), rz = (1 - inOut(p)) * -14;
            entradas[i] = render(MEDIA, transformar((x, y) -> completa(x, y, -1), s, rz, o), AMPLIO);
            json.append(glifoRecortado(tex, String.format("entrada_%02d", i), entradas[i], CH_ENTRADA + i, ALTO_AMPLIO));
        }
        log("entrada");

        // salida: teñida del color ganador, se achica girando y se desvanece
        BufferedImage[][] salidas = new BufferedImage[SEG][SALIDA];
        for (int k = 0; k < SEG; k++) for (int i = 0; i < SALIDA; i++) {
            int kk = k;
            double e = (i + 0.5) / 20.0, p = clamp(e / 0.75);
            double s = Math.max(0.03, 1 - 0.97 * easeInBack(p)), o = 1 - inOut(p * 1.15), rz = p * 18;
            salidas[k][i] = render(MEDIA, transformar((x, y) -> completa(x, y, kk), s, rz, o), AMPLIO);
            json.append(glifoRecortado(tex, String.format("salida_%d_%02d", k, i), salidas[k][i], CH_SALIDA + k * SALIDA + i, ALTO_AMPLIO));
        }
        log("salida");

        json.append("\n  ]\n}\n");
        Files.writeString(font.resolve("ruleta.json"), json);

        BufferedImage icono = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        var g = icono.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (BufferedImage capa : new BufferedImage[]{disco0, fijo0, fuegos[0], flechas[0]}) g.drawImage(capa, 0, 0, 128, 128, null);
        g.dispose();
        ImageIO.write(icono, "png", root.resolve("pack.png").toFile());

        Files.writeString(root.resolve("pack.mcmeta"), """
                {
                  "pack": {
                    "description": "§6Ruleta §7- SobrinoDev",
                    "pack_format": 46,
                    "supported_formats": [46, 999],
                    "min_format": 46,
                    "max_format": 999
                  }
                }
                """);

        Files.writeString(Paths.get("src/main/resources/ruleta-layout.json"), String.format("""
                {
                  "disco": %d, "discoFrames": %d,
                  "borroso": %d, "borrosoFrames": %d,
                  "fijo": %d,
                  "fuego": %d, "fuegoFrames": %d, "fuegoTicks": %d,
                  "flecha": %d, "flechaFrames": %d,
                  "tinte": %d, "tinteNiveles": %d,
                  "entrada": %d, "entradaFrames": %d,
                  "salida": %d, "salidaFrames": %d,
                  "mover": %d, "juntar": %d, "atras": %d,
                  "segmentos": [%s]
                }
                """, CH_DISCO, DISCO, CH_BORROSO, BORROSO, CH_FIJO, CH_FUEGO, FUEGO, FUEGO_TICKS, CH_FLECHA, FLECHA_ANG.length,
                CH_TINTE, TINTE.length, CH_ENTRADA, ENTRADA, CH_SALIDA, SALIDA, CH_MOVER, CH_JUNTAR, CH_ATRAS, orden()));

        if (vista != null) vistaPrevia(vista, disco0, borroso0, fijo0, fuegos, flechas, tintes, entradas, salidas);
        System.out.println("Pack listo -> " + root.toAbsolutePath());
    }

    /** Orden en que pasan los colores bajo la flecha: en el fotograma k*8 la flecha marca el color que estaba k lugares antes. */
    static String orden() {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < SEG; k++) sb.append(k > 0 ? ", " : "").append('"').append(COLORES[Math.floorMod(-k, SEG)][0]).append('"');
        return sb.toString();
    }

    static long t0 = System.currentTimeMillis();
    static void log(String paso) { System.out.printf("%-9s %5.1f s%n", paso, (System.currentTimeMillis() - t0) / 1000.0); }

    // ---------------------------------------------------------------- disco

    /** El disco girado `giro` grados; con desenfoque > 0 promedia varios ángulos (desenfoque de movimiento). */
    static int discoEn(double x, double y, double giro, double desenfoque) {
        double d = Math.hypot(x - HX, y - HY), ang = angulo(x, y), borde = radioEn(ang);
        if (d > borde + 3) return 0;
        double rho = Math.min(d / borde, 0.995);
        if (desenfoque <= 0) return disco(rho, norm(ang - giro), d) | 0xFF000000;
        int n = 9;
        double r = 0, gg = 0, b = 0;
        for (int i = 0; i < n; i++) {
            int c = disco(rho, norm(ang - giro + desenfoque * (2.0 * i / (n - 1) - 1)), d);
            r += (c >> 16) & 255; gg += (c >> 8) & 255; b += c & 255;
        }
        return 0xFF000000 | ((int) (r / n) << 16) | ((int) (gg / n) << 8) | (int) (b / n);
    }

    /** Color del disco en el punto (rho, ángulo de la ruleta nueva). */
    static int disco(double rho, double ang, double d) {
        double w = 360.0 / SEG;
        double t = norm(ang + w / 2) / w;
        int j = (int) Math.floor(t) % SEG;
        double b0 = j * w - w / 2;                              // borde inicial del segmento nuevo
        double rel = norm(ang - b0);                            // grados desde el borde inicial
        int s = (int) COLORES[j][1];
        double a0 = DIVISORES[s], a1 = s + 1 < DIVISORES.length ? DIVISORES[s + 1] : DIVISORES[0] + 360;
        double r = Math.max(d, 1);
        double desde = Math.toDegrees(Math.asin(Math.min(1, LINEA / r)));
        double off0 = r * Math.sin(Math.toRadians(rel)), off1 = r * Math.sin(Math.toRadians(w - rel));
        double srcAng;
        if (off0 < LINEA) srcAng = a0 + Math.toDegrees(Math.asin(Math.min(1, off0 / r)));       // media línea del lado inicial
        else if (off1 < LINEA) srcAng = a1 - Math.toDegrees(Math.asin(Math.min(1, off1 / r)));  // media línea del lado final
        else {
            double f = (rel - desde) / (w - 2 * desde);
            srcAng = a0 + desde + f * (a1 - a0 - 2 * desde);
        }
        srcAng = norm(srcAng);
        double[] p = polar(rho * radioEn(srcAng), srcAng);
        if (flecha.contains(p[0], p[1])) {
            // bajo la flecha no hay disco: se copia de un poco más adentro del mismo color
            double mitad = norm((a0 + a1) / 2);
            double hacia = norm(mitad - srcAng + 180) - 180;
            if (off0 < LINEA || off1 < LINEA) p = polar(0.88 * radioEn(srcAng), srcAng);
            else p = polar(rho * radioEn(srcAng), norm(srcAng + Math.signum(hacia) * 7));
        }
        int c = bilinear(src, p[0], p[1]);
        double giroTono = (double) COLORES[j][2];
        return giroTono == 0 ? c & 0xFFFFFF : girarTono(c, giroTono);
    }

    static int girarTono(int c, double grados) {
        float[] h = Color.RGBtoHSB((c >> 16) & 255, (c >> 8) & 255, c & 255, null);
        if (h[1] < 0.3f) return c & 0xFFFFFF; // líneas y destellos quedan igual
        float hh = (float) (h[0] + grados / 360.0);
        hh -= Math.floor(hh);
        return Color.HSBtoRGB(hh, h[1], h[2]) & 0xFFFFFF;
    }

    /** Giro del disco cuando el segmento k queda bajo la flecha. */
    static double giroDe(int k) { return 360.0 * k * POR_SEGMENTO / DISCO; }

    /** Color medio del segmento k (con el disco detenido en él), como sampleHex() del HTML. */
    static int muestrearColor(int k) {
        double r = 0, g = 0, b = 0;
        int n = 0;
        for (double a = -12; a <= 12; a += 3) for (double f = 0.55; f <= 0.8; f += 0.05) {
            double ang = norm(a);
            int c = disco(f, norm(ang - giroDe(k)), f * radioEn(ang));
            r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
        }
        return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }

    // ---------------------------------------------------------------- aro + fuego

    static int fijo(double x, double y, double t) {
        double d = Math.hypot(x - HX, y - HY);
        double borde = radioEn(angulo(x, y));
        // la flecha va en su propia capa: aquí se rellena lo que hay detrás copiando de un poco al lado
        double sx = x, sy = y;
        if (flecha.intersects(x - 4, y - 4, 8, 8)) {
            double[] q = polar(d, norm(angulo(x, y) + 24));
            sx = q[0]; sy = q[1];
        }
        boolean hub = d < HUB_R;
        double hoyo = hub ? 0 : clamp(borde - d + 0.5);
        if (hoyo >= 1) return 0;

        int p = bilinear(src, sx, sy);
        double[] f = fuego(sx, sy, t);
        if (f != null) {
            // las llamas se sacan del dibujo; si el desplazamiento cae sobre el aro o una flecha se usa
            // el dibujo sin marco, así no se arrastran pedazos del aro
            double qx = sx + f[0], qy = sy + f[1];
            int fl = brillar(mezclarRGB(bilinear(src, qx, qy), bilinear(plate, qx, qy), canal(mascaraFija, qx, qy, 16)), 1 + f[2]);
            p = mezclarRGB(fl, p, canal(mascaraFija, sx, sy, 16));
        }
        p = quitarFondo(p, d);

        double borde2 = Math.min(Math.min(x - CROP_X, CROP_X + CROP - x), Math.min(y - CROP_Y, CROP_Y + CROP - y));
        // borde redondo y suave: las llamas se apagan en círculo, sin dejar ver el recorte cuadrado
        double fade = clamp(borde2 / 70.0) * clamp((560 - d) / 130.0);
        int a = (int) Math.round(((p >>> 24) & 255) * (1 - hoyo) * fade * fade);
        return (a << 24) | (p & 0xFFFFFF);
    }

    /**
     * Desplazamiento de las llamas en el instante t (0..1 del loop), igual que el shader del HTML:
     * ruido que sube, un vaivén suave, las alas de abajo aleteando y un parpadeo de brillo.
     * Devuelve {dx, dy, parpadeo} en píxeles del dibujo, o null donde no hay fuego.
     */
    static double[] fuego(double x, double y, double t) {
        double fw = canal(mascara, x / 2, y / 2, 16);
        if (fw <= 0.002) return null;
        double u = x / ARTE, v = y / ARTE, tt = t * LOOP, w = 2 * Math.PI / LOOP;
        double r = Math.hypot(u - HX / ARTE, v - HY / ARTE);
        double outer = smoothstep(0.30, 0.60, r);
        double k = 0.35 + 0.65 * outer;
        double n1 = fbmLoop(u * 7.0, v * 5.0, 0.06, 0.38, t);
        double n2 = fbmLoop(u * 7.0 + 31.0, v * 5.0 + 11.0, -0.05, 0.38, t);
        double dx = (n1 - 0.5) * 0.115 * k, dy = (n2 - 0.5) * 0.085 * k;
        dy += (0.5 + 0.5 * Math.sin(tt * w + u * 9.0)) * 0.009 * outer;
        // alas: cada una gira alrededor de su base como una onda que va hacia la punta
        double wy = smoothstep(0.60, 0.68, v) * (1 - smoothstep(0.93, 0.99, v));
        if (wy > 0) {
            boolean izq = u <= 0.495;
            double pvx = izq ? 0.447 : 0.539, pvy = 0.837, sgn = izq ? 1 : -1;
            double dp = Math.hypot(u - pvx, v - pvy);
            double th = sgn * 0.125 * smoothstep(0.02, 0.32, dp) * Math.sin(tt * w - dp * 7.0);
            double c = Math.cos(-th), s = Math.sin(-th), qx = u - pvx, qy = v - pvy;
            dx += (pvx + c * qx - s * qy - u) * wy;
            dy += (pvy + s * qx + c * qy - v) * wy;
        }
        dx *= fw * FUERZA_FUEGO;
        dy *= fw * FUERZA_FUEGO;
        double n3 = fbmLoop(u * 9.0, v * 7.0, 0, 0.6, t);
        double fk = ((n3 - 0.5) * 0.85 * outer + 0.07 * Math.sin(tt * w)) * FUERZA_FUEGO * fw;
        return new double[]{dx * ARTE, dy * ARTE, fk};
    }

    /**
     * fbm que se mueve con velocidad (vx, vy) por segundo y vuelve exactamente al inicio al terminar el loop:
     * mezcla el ruido "de ahora" con el de un loop atrás, compensando el contraste que se pierde al mezclar.
     */
    static double fbmLoop(double x, double y, double vx, double vy, double t) {
        double tt = t * LOOP;
        double a = fbm(x + vx * tt, y + vy * tt) - 0.5, b = fbm(x + vx * (tt - LOOP), y + vy * (tt - LOOP)) - 0.5;
        double wa = 1 - t, wb = t;
        return 0.5 + (wa * a + wb * b) / Math.sqrt(wa * wa + wb * wb);
    }

    static double hash(double x, double y) {
        x = fract(x * 123.34); y = fract(y * 456.21);
        double dd = x * (x + 45.32) + y * (y + 45.32);
        x += dd; y += dd;
        return fract(x * y);
    }

    static double noise(double x, double y) {
        double ix = Math.floor(x), iy = Math.floor(y), fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        double a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    static double fbm(double x, double y) {
        double s = 0, a = 0.5;
        for (int i = 0; i < 4; i++) { s += a * noise(x, y); x = x * 2.03 + 17.1; y = y * 2.03 + 9.7; a *= 0.5; }
        return s;
    }

    /** Destellos que laten, brasas que suben y el brillo morado del fénix (como la capa 2D del HTML). */
    static void brillos(BufferedImage img, double t) {
        double w = 2 * Math.PI;
        // brasas: cada una cierra un número entero de ciclos en el loop
        Random rnd = new Random(7);
        for (int i = 0; i < 42; i++) {
            double a = rnd.nextDouble() * w, rr = 420 + rnd.nextDouble() * 130;
            double x0 = HX + Math.sin(a) * rr, y0 = HY - Math.cos(a) * rr * 0.95;
            if (y0 > 1040) y0 = 1040 - rnd.nextDouble() * 300;
            double tam = 1.2 + rnd.nextDouble() * 2.6, subida = 90 + rnd.nextDouble() * 150, fase = rnd.nextDouble();
            int vueltas = 1 + rnd.nextInt(2);
            int color = rnd.nextDouble() < 0.55 ? 0xffaa46 : 0xc878ff;
            double u = (t * vueltas + fase) % 1.0;
            double x = x0 + 8 * Math.sin(w * (u + fase)), y = y0 - subida * u;
            brillo(img, x, y, 0, tam * 5, color, 0.85 * Math.sin(Math.PI * u), 0);
        }
        for (int s = 0; s < ESTRELLAS.length; s++) {
            double[] st = ESTRELLAS[s];
            double p = 0.5 + 0.5 * Math.sin(w * t + s * 1.9);
            // más suave que en el HTML: allá brilla sobre negro, acá sobre el mundo y se vería como niebla
            brillo(img, st[0], st[1], 0, st[2] * (0.9 + p * 0.9), 0xffaaff, 0.12 + 0.2 * p, 0xff78ff);
        }
        double hp = 0.5 + 0.5 * Math.sin(w * t);
        brillo(img, HX, HY, FENIX_R * 0.4, FENIX_R * 1.5, 0xc878ff, 0.10 + 0.12 * hp, 0xa855f7);
    }

    /** Degradé radial que suma luz (como globalCompositeOperation = 'lighter'), en coordenadas del dibujo. */
    static void brillo(BufferedImage img, double cx, double cy, double r0, double r1, int rgb, double alpha, int rgbFin) {
        if (alpha <= 0.002) return;
        if (rgbFin == 0) rgbFin = rgb;
        double k = (double) SIZE / CROP;
        double ox = (cx - CROP_X) * k, oy = (cy - CROP_Y) * k, R = r1 * k, R0 = r0 * k;
        for (int y = (int) Math.floor(oy - R); y <= oy + R; y++)
            for (int x = (int) Math.floor(ox - R); x <= ox + R; x++) {
                if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) continue;
                double dist = Math.hypot(x + 0.5 - ox, y + 0.5 - oy);
                if (dist >= R) continue;
                double f = clamp((dist - R0) / (R - R0));
                int c = mezclarRGB(rgbFin, rgb, 1 - f);
                img.setRGB(x, y, sumarLuz(img.getRGB(x, y), c, alpha * (1 - f)));
            }
    }

    // ---------------------------------------------------------------- flecha, tinte y ruleta completa

    static int flechaEn(double x, double y, double grados) {
        double a = Math.toRadians(-grados);
        double dx = x - PIVOTE_X, dy = y - PIVOTE_Y;
        double sx = PIVOTE_X + dx * Math.cos(a) - dy * Math.sin(a), sy = PIVOTE_Y + dx * Math.sin(a) + dy * Math.cos(a);
        return flecha.contains(sx, sy) ? bilinear(src, sx, sy) | 0xFF000000 : 0;
    }

    /**
     * El disco detenido en el segmento k, teñido de su color con fuerza amt, más el aura de color alrededor.
     * Igual que el shader: conserva un poco las luces y sombras del disco para que se vean las líneas.
     */
    static int tinteEn(double x, double y, int k, double amt, boolean latido) {
        double d = Math.hypot(x - HX, y - HY), ang = angulo(x, y), borde = radioEn(ang);
        int tinte = tinteColor[k];
        double aura = aura(d) * amt * (latido ? 1.25 : 1);
        if (d > HUB_R && d < borde + 1) {
            int c = disco(Math.min(d / borde, 0.995), norm(ang - giroDe(k)), d);
            double sel = Math.max(lum(tinte), 0.05 * 255);
            double shade = Math.max(0.55, Math.min(1.3, lum(c) / sel));
            shade = 1 + (shade - 1) * 0.35;
            double f = shade * (latido ? 1.35 : 1);
            int teñido = escalar(tinte, f);
            teñido = sumarLuz(teñido | 0xFF000000, tinte, aura) & 0xFFFFFF;
            double borde01 = clamp(borde + 1 - d); // borde suave
            return ((int) Math.round(255 * amt * borde01) << 24) | teñido;
        }
        if (aura <= 0.002 || d < HUB_R) return 0;
        return ((int) Math.round(255 * aura) << 24) | tinte;
    }

    /** Aura de color del HTML: anillo entre 300 y 540 px, más fuerte a 384 px. */
    static double aura(double d) {
        if (d <= 300 || d >= 540) return 0;
        double pos = (d - 300) / 240;
        return pos < 0.35 ? 0.38 * pos / 0.35 : 0.38 * (1 - (pos - 0.35) / 0.65);
    }

    /** La ruleta entera quieta (con el color k ganador teñido, o sin teñir si k < 0). */
    static int completa(double x, double y, int k) {
        int c = discoEn(x, y, k >= 0 ? giroDe(k) : 0, 0);
        if (k >= 0) {
            int t = tinteEn(x, y, k, 1, false);
            double d = Math.hypot(x - HX, y - HY);
            if (d < radioEn(angulo(x, y)) + 1) c = sobre(c, t);
            c = sobre(c, fijo(x, y, 0));
            c = sobre(c, flechaEn(x, y, 0));
            if (d >= radioEn(angulo(x, y)) + 1) c = sobre(c, t);
            return c;
        }
        return sobre(sobre(c, fijo(x, y, 0)), flechaEn(x, y, 0));
    }

    /** Escala s y gira rz grados (sentido horario) alrededor del fénix, con opacidad o. */
    static Pixel transformar(Pixel f, double s, double rz, double o) {
        double a = Math.toRadians(-rz), c = Math.cos(a), sn = Math.sin(a);
        return (x, y) -> {
            double dx = (x - HX) / s, dy = (y - HY) / s;
            int p = f.at(HX + c * dx - sn * dy, HY + sn * dx + c * dy);
            int al = (int) Math.round(((p >>> 24) & 255) * o);
            return (al << 24) | (p & 0xFFFFFF);
        };
    }

    // ---------------------------------------------------------------- vista previa

    static void vistaPrevia(Path dir, BufferedImage disco0, BufferedImage borroso0, BufferedImage fijo0, BufferedImage[] fuegos,
                            BufferedImage[] flechas, BufferedImage[][] tintes, BufferedImage[] entradas, BufferedImage[][] salidas) throws Exception {
        Files.createDirectories(dir);
        int c = 256, cols = 6;
        BufferedImage hoja = new BufferedImage(c * cols, c * 5, BufferedImage.TYPE_INT_RGB);
        var g = hoja.createGraphics();
        g.setColor(new Color(0x5a7d3a)); // pasto, para ver la transparencia
        g.fillRect(0, 0, hoja.getWidth(), hoja.getHeight());
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // fila 1: el fuego en distintos momentos del loop
        for (int i = 0; i < cols; i++)
            for (BufferedImage capa : new BufferedImage[]{disco0, fijo0, fuegos[i * FUEGO / cols], flechas[0]}) g.drawImage(capa, i * c, 0, c, c, null);
        // fila 2: girando rápido (desenfoque) y la flecha inclinada
        for (int i = 0; i < cols; i++)
            for (BufferedImage capa : new BufferedImage[]{borroso0, fijo0, fuegos[0], flechas[i % FLECHA_ANG.length]}) g.drawImage(capa, i * c, c, c, c, null);
        // fila 3: el tinte subiendo para el segmento 0 (rojo) + latido
        for (int i = 0; i < Math.min(cols, TINTE.length + 1); i++) {
            BufferedImage[] capas = i == 0 ? new BufferedImage[]{disco0, fijo0, fuegos[0], flechas[0]}
                    : new BufferedImage[]{disco0, fijo0, fuegos[0], tintes[0][i - 1], flechas[0]};
            for (BufferedImage capa : capas) g.drawImage(capa, i * c, 2 * c, c, c, null);
        }
        // fila 4: entrada (cada 2 fotogramas)
        for (int i = 0; i < cols; i++) g.drawImage(entradas[i * 4], i * c, 3 * c, c, c, null);
        // fila 5: salida del color 3
        for (int i = 0; i < cols; i++) g.drawImage(salidas[3][Math.min(SALIDA - 1, i * 2)], i * c, 4 * c, c, c, null);
        g.dispose();
        ImageIO.write(hoja, "png", dir.resolve("vista_previa.png").toFile());
        // y la ruleta sola a tamaño real de una capa, para ver detalle
        BufferedImage grande = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        var g2 = grande.createGraphics();
        g2.setColor(new Color(0x5a7d3a));
        g2.fillRect(0, 0, SIZE, SIZE);
        for (BufferedImage capa : new BufferedImage[]{disco0, fijo0, fuegos[FUEGO / 3], flechas[0]}) g2.drawImage(capa, 0, 0, null);
        g2.dispose();
        ImageIO.write(grande, "png", dir.resolve("vista_grande.png").toFile());
    }

    // ---------------------------------------------------------------- utilidades

    /** Corta la capa en 4 piezas de TILE px y devuelve sus entradas para la fuente. */
    static String glifo(Path tex, String name, BufferedImage img, int ch) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int q = 0; q < 4; q++) {
            BufferedImage copia = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
            copia.setRGB(0, 0, TILE, TILE, img.getRGB((q % 2) * TILE, (q / 2) * TILE, TILE, TILE, null, 0, TILE), 0, TILE);
            String file = name + "_" + q;
            guardar(copia, tex.resolve(file + ".png"));
            sb.append(String.format(",\n    {\"type\": \"bitmap\", \"file\": \"ruleta:font/%s.png\", \"height\": %d, \"ascent\": %d, \"chars\": [\"\\u%04X\"]}",
                    file, PIEZA, q < 2 ? PIEZA : 0, ch + q));
        }
        return sb.toString();
    }

    /** Una capa a media resolución en una sola pieza que ocupa lo mismo que las 4 piezas juntas. */
    static String glifoGrande(Path tex, String name, BufferedImage img, int ch) throws Exception {
        return glifoGrande(tex, name, img, ch, 2 * PIEZA);
    }

    /** Igual, pero de `alto` unidades (centrada en el mismo punto que el resto de las capas). */
    static String glifoGrande(Path tex, String name, BufferedImage img, int ch, int alto) throws Exception {
        guardar(img, tex.resolve(name + ".png"));
        return String.format(",\n    {\"type\": \"bitmap\", \"file\": \"ruleta:font/%s.png\", \"height\": %d, \"ascent\": %d, \"chars\": [\"\\u%04X\"]}",
                name, alto, alto / 2, ch);
    }

    /**
     * Como glifoGrande, pero recorta el borde transparente (centrado, para no correr la ruleta).
     * Los fotogramas chicos de la entrada y la salida quedan como glifos chicos y Minecraft los
     * junta varios por página de textura, en vez de gastar una página entera en cada uno.
     */
    static String glifoRecortado(Path tex, String name, BufferedImage img, int ch, int alto) throws Exception {
        int n = img.getWidth(), c = n / 2, mitad = 0;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++)
            if ((img.getRGB(x, y) >>> 24) > 2) mitad = Math.max(mitad, Math.max(Math.abs(x - c), Math.abs(y - c)) + 1);
        int u = Math.max(2, (int) Math.ceil((double) alto * 2 * (mitad + 1) / n));
        if (u % 2 == 1) u++;                      // par: el ascent (la mitad) queda exacto y la ruleta centrada
        int lado = Math.min(n, (int) Math.round((double) u * n / alto));
        if (lado >= n) return glifoGrande(tex, name, img, ch, alto);
        int x0 = c - lado / 2;
        BufferedImage recorte = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        recorte.setRGB(0, 0, lado, lado, img.getRGB(x0, x0, lado, lado, null, 0, lado), 0, lado);
        guardar(recorte, tex.resolve(name + ".png"));
        return String.format(",\n    {\"type\": \"bitmap\", \"file\": \"ruleta:font/%s.png\", \"height\": %d, \"ascent\": %d, \"chars\": [\"\\u%04X\"]}",
                name, u, u / 2, ch);
    }

    static void guardar(BufferedImage img, Path file) throws Exception {
        BufferedImage copia = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copia.setRGB(0, 0, img.getWidth(), img.getHeight(), img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth()), 0, img.getWidth());
        marcarAncho(copia);
        reducirColores(copia);
        ImageIO.write(copia, "png", file.toFile());
    }

    static void medirBorde() {
        double[] r = new double[3600];
        for (int i = 0; i < 3600; i++) {
            double a = i / 10.0, d = 240;
            while (d < 380) {
                double[] p = polar(d, a), q = polar(d + 2, a);
                if (canalMax(p[0], p[1]) < 45 && canalMax(q[0], q[1]) < 45) break; // el bisel oscuro del color cuenta como disco
                d += 0.5;
            }
            r[i] = d;
        }
        // mediana amplia para ignorar las líneas negras y la flecha, y luego suavizado
        double[] m = new double[3600];
        for (int i = 0; i < 3600; i++) {
            double[] w = new double[301];
            for (int k = -150; k <= 150; k++) w[k + 150] = r[(i + k + 3600) % 3600];
            Arrays.sort(w);
            m[i] = w[150];
        }
        for (int i = 0; i < 3600; i++) {
            double s = 0;
            for (int k = -50; k <= 50; k++) s += m[(i + k + 3600) % 3600];
            radio[i] = s / 101 - 1.5; // un poco hacia adentro: el borde negro queda en la capa fija
        }
    }

    interface Pixel { int at(double x, double y); }

    static BufferedImage render(int n, Pixel f) {
        return render(n, f, 1);
    }

    /**
     * Dibuja una capa de n x n px con 3x3 muestras por píxel, en paralelo por filas. Cubre el recorte
     * completo multiplicado por `escala` (mismo centro): con escala > 1 cabe la ruleta agrandada o girada.
     */
    static BufferedImage render(int n, Pixel f, double escala) {
        int[] out = new int[n * n];
        double lado = CROP * escala, k = lado / n;
        double x0 = CROP_X + (CROP - lado) / 2, y0 = CROP_Y + (CROP - lado) / 2;
        int m = 3;
        IntStream.range(0, n).parallel().forEach(y -> {
            for (int x = 0; x < n; x++) {
                double ar = 0, rr = 0, gg = 0, bb = 0;
                for (int sy = 0; sy < m; sy++) for (int sx = 0; sx < m; sx++) {
                    int p = f.at(x0 + (x + (sx + 0.5) / m) * k, y0 + (y + (sy + 0.5) / m) * k);
                    double a = ((p >>> 24) & 255) / 255.0;
                    ar += a; rr += ((p >> 16) & 255) * a; gg += ((p >> 8) & 255) * a; bb += (p & 255) * a;
                }
                if (ar <= 0) continue;
                int a = (int) Math.round(ar / (m * m) * 255);
                out[y * n + x] = (a << 24) | ((int) (rr / ar) << 16) | ((int) (gg / ar) << 8) | (int) (bb / ar);
            }
        });
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, n, n, out, 0, n);
        return img;
    }

    /** Fuera del marco, el fondo negro se vuelve transparente y las llamas conservan su brillo. */
    static int quitarFondo(int p, double d) {
        double fuera = clamp((d - ARO_R + 5) / 10);
        if (fuera <= 0) return p | 0xFF000000;
        int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        double key = clamp((Math.max(r, Math.max(g, b)) - 26) / 54.0); // sin neblina oscura alrededor de las llamas
        double alpha = 1 - fuera * (1 - key);
        if (alpha <= 0.004) return 0;
        int[] c = {r, g, b};
        for (int k = 0; k < 3; k++) c[k] = (int) Math.max(0, Math.min(255, Math.round((c[k] - (1 - alpha) * FONDO[k]) / alpha)));
        return ((int) Math.round(alpha * 255) << 24) | (c[0] << 16) | (c[1] << 8) | c[2];
    }

    static int brillar(int p, double f) { return (p & 0xFF000000) | escalar(p, f); }

    static int escalar(int p, double f) {
        int r = (int) Math.max(0, Math.min(255, ((p >> 16) & 255) * f)), g = (int) Math.max(0, Math.min(255, ((p >> 8) & 255) * f)),
                b = (int) Math.max(0, Math.min(255, (p & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    static double lum(int c) { return 0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255); }

    /** Mezcla dos colores opacos: t = 0 da a, t = 1 da b. */
    static int mezclarRGB(int a, int b, double t) {
        int o = 0;
        for (int sh = 16; sh >= 0; sh -= 8) {
            double v = ((a >> sh) & 255) * (1 - t) + ((b >> sh) & 255) * t;
            o |= (int) Math.round(v) << sh;
        }
        return 0xFF000000 | o;
    }

    /** Pone src encima de dst (alfa normal). */
    static int sobre(int dst, int src) {
        double sa = ((src >>> 24) & 255) / 255.0, da = ((dst >>> 24) & 255) / 255.0, oa = sa + da * (1 - sa);
        if (oa <= 0) return 0;
        int o = 0;
        for (int sh = 16; sh >= 0; sh -= 8) {
            double v = (((src >> sh) & 255) * sa + ((dst >> sh) & 255) * da * (1 - sa)) / oa;
            o |= (int) Math.round(v) << sh;
        }
        return ((int) Math.round(oa * 255) << 24) | o;
    }

    /** Suma luz de color rgb con fuerza a sobre dst (con su alfa): así brillan los destellos sobre fondo transparente. */
    static int sumarLuz(int dst, int rgb, double a) {
        double da = ((dst >>> 24) & 255) / 255.0, oa = Math.min(1, da + a);
        if (oa <= 0) return 0;
        int o = 0;
        for (int sh = 16; sh >= 0; sh -= 8) {
            double pre = ((dst >> sh) & 255) * da + ((rgb >> sh) & 255) * a; // color premultiplicado
            o |= (int) Math.min(255, Math.round(pre / oa)) << sh;
        }
        return ((int) Math.round(oa * 255) << 24) | o;
    }

    /** Baja cada canal a 64 niveles: casi no se nota y el PNG pesa mucho menos. */
    static void reducirColores(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
            int p = img.getRGB(x, y), a = p >>> 24;
            if (a == 0) { img.setRGB(x, y, 0); continue; }
            int[] c = {(p >> 16) & 255, (p >> 8) & 255, p & 255, a};
            for (int k = 0; k < 4; k++) c[k] = Math.min(255, (int) Math.round(c[k] / 4.0) * 4);
            if (a > 250) c[3] = 255;
            if (c[3] == 0) c[3] = Math.min(4, a);
            img.setRGB(x, y, (c[3] << 24) | (c[0] << 16) | (c[1] << 8) | c[2]);
        }
    }

    /** Minecraft mide el ancho del glifo hasta el último píxel visible: esto deja todas las capas del mismo ancho. */
    static void marcarAncho(BufferedImage img) {
        int w = img.getWidth() - 1;
        if ((img.getRGB(w, 0) >>> 24) == 0) img.setRGB(w, 0, 0x01000000);
    }

    static Path2D poligono(double[][] pts) {
        Path2D p = new Path2D.Double();
        p.moveTo(pts[0][0], pts[0][1]);
        for (int i = 1; i < pts.length; i++) p.lineTo(pts[i][0], pts[i][1]);
        p.closePath();
        return p;
    }

    static BufferedImage rgb(BufferedImage img) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(img, 0, 0, null);
        return out;
    }

    static double radioEn(double ang) { return radio[(int) Math.round(norm(ang) * 10) % 3600]; }
    static double angulo(double x, double y) { return norm(Math.toDegrees(Math.atan2(x - HX, -(y - HY)))); }
    static double[] polar(double d, double ang) { double t = Math.toRadians(ang); return new double[]{HX + d * Math.sin(t), HY - d * Math.cos(t)}; }
    static double norm(double a) { a %= 360; return a < 0 ? a + 360 : a; }
    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    static double fract(double v) { return v - Math.floor(v); }
    static double smoothstep(double a, double b, double x) { double t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); }
    static double inOut(double x) { x = clamp(x); return x * x * (3 - 2 * x); }
    static double easeBack(double x) { double c1 = 1.7, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); }
    static double easeInBack(double x) { double c1 = 1.4, c3 = c1 + 1; return c3 * x * x * x - c1 * x * x; }
    static int canalMax(double x, double y) { int p = src.getRGB((int) x, (int) y); return Math.max(p >> 16 & 255, Math.max(p >> 8 & 255, p & 255)); }

    /** Canal (16 = rojo, 8 = verde, 0 = azul) de una máscara, de 0 a 1, interpolado. */
    static double canal(BufferedImage img, double x, double y, int sh) {
        return ((bilinear(img, x, y) >> sh) & 255) / 255.0;
    }

    static int bilinear(BufferedImage img, double x, double y) {
        x -= 0.5; y -= 0.5;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0, r = 0, g = 0, b = 0;
        for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) {
            int xx = Math.max(0, Math.min(img.getWidth() - 1, x0 + i)), yy = Math.max(0, Math.min(img.getHeight() - 1, y0 + j));
            int p = img.getRGB(xx, yy);
            double w = (i == 0 ? 1 - fx : fx) * (j == 0 ? 1 - fy : fy);
            r += ((p >> 16) & 255) * w; g += ((p >> 8) & 255) * w; b += (p & 255) * w;
        }
        return 0xFF000000 | ((int) Math.round(r) << 16) | ((int) Math.round(g) << 8) | (int) Math.round(b);
    }
}
