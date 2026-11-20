import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

// Genera el resource pack de la ruleta (resourcepack/Ruleta) con las animaciones de
// "Ruleta del fénix.html": llamas y alas que ondulan, estrellas y brasas, desenfoque al girar,
// y al final se tiñe del color ganador.
// Ejecutar desde la raíz de VoranixExtremo: java tools/ruleta/GenerarRuleta.java [carpeta-vista-previa]
//
// Arte (tools/ruleta/art, sacado del HTML):
//   ruleta.png        el dibujo completo (1254 x 1254)
//   sin_marco.jpg     el mismo dibujo sin aro ni flechas: de aquí se sacan las llamas desplazadas
//   mascara.png       R = cuánto se mueve el fuego, G = disco, B = flecha de arriba (a 627 px)
//   mascara_fija.png  blanco = lo que nunca se deforma (disco, aro y flechas)
//
// La ruleta se arma con varias "letras" encimadas en el title. Lo que se mueve entero (agrandarse al
// aparecer, girar el disco, achicarse al irse) no va dibujado cuadro por cuadro: lo hace el shader del
// pack (rendertype_text.vsh) con el color de cada capa (ver RuletaManager.java). Así cada capa es UNA
// imagen y alcanza la memoria para dibujarlas al doble de resolución:
//   disco        los 8 colores, a 1024 px (4 x 4 piezas de 256): el shader lo gira
//   borroso_N    el disco con desenfoque de movimiento (3 fuerzas), para cuando gira rápido (512 px)
//   fijo         aro y fénix (1024 px)
//   flecha_X     la gema de arriba, inclinada cuando una línea la empuja (1024 px, solo las piezas con algo)
//   fuego_XX     llamas, alas, estrellas, brasas y el brillo del fénix, en loop (512 px)
//   tinte_K_N    el disco teñido del color ganador K (N = intensidad; la última es el latido) + aura (512 px)
// Todas miden lo mismo en pantalla: las de 512 px van en 2 x 2 piezas más altas.
// Cada pieza lleva en su píxel (255, 0) el color FIRMA: con eso el shader sabe que es la ruleta.
// También escribe src/main/resources/ruleta-layout.json para que el plugin sepa qué hay en el pack.
public class GenerarRuleta {
    // --- medidas del dibujo original (en píxeles de art/ruleta.png) ---
    static final double HX = 620, HY = 593;      // centro del fénix = centro de giro
    static final double HUB_R = 111;             // radio del fénix con su borde
    static final double ARO_R = 400;             // borde exterior del aro dorado
    static final int[] FONDO = {2, 1, 10};       // color del fondo negro del dibujo
    // recorte cuadrado que se muestra: casi todo el dibujo, centrado en el fénix, para que las llamas y
    // los destellos de los costados no queden cortados. El shader dibuja el cuadro un 5 % más grande que
    // antes (RULETA_TAMANO en rendertype_text.vsh): con escala de GUI 4 en 1080p ocupa justo el alto de la pantalla.
    static final int CROP = 1180, CROP_X = (int) HX - CROP / 2, CROP_Y = (int) HY - CROP / 2;
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
            {"celeste", 2, -22.0}, {"azul", 5, 0.0}, {"morado", 3, 0.0}, {"rosa", 4, 0.0},
    };

    // --- salida ---
    static final int HD = 1024;                  // disco, aro y flecha
    static final int MEDIA = 512;                // fuego, tinte y desenfoque (son suaves: no se nota)
    static final int TILE = 256;                 // Minecraft no acepta glifos de más de 256 px: cada capa va en piezas
    static final int SEG = COLORES.length;
    static final int POR_SEGMENTO = 8;
    static final int DISCO = SEG * POR_SEGMENTO; // posiciones del disco por vuelta (el plugin cuenta el giro en estas unidades)
    static final double[] DESENFOQUE = {4, 9, 18}; // medio ancho de cada desenfoque, en grados
    static final int FUEGO = 30;                 // fotogramas de las llamas...
    static final int FUEGO_TICKS = 2;            // ...cada uno dura 2 ticks: el loop dura 3 segundos
    static final double LOOP = FUEGO * FUEGO_TICKS / 20.0;
    static final double FUERZA_FUEGO = 1.3;      // el control "Fuego" del HTML
    static final double[] FLECHA_ANG = {0, -5, -10, -15};
    static final double[] TINTE = {0.25, 0.5, 0.75, 1, 1}; // el último es el latido (más claro, aura más fuerte)
    // La ruleta completa mide LADO unidades de la fuente; el title la dibuja a 4x: 256 px de GUI
    // (con escala de GUI 4, 1024 px de pantalla: justo la resolución del disco). Cada fila de piezas
    // empieza en ARRIBA (el ascent de la primera) y baja de a una pieza; todas las capas, sean de
    // 4 x 4 o de 2 x 2 piezas, ocupan exactamente el mismo cuadrado y avanzan LADO + 1.
    static final int LADO = 64, ARRIBA = 16;
    // Color que marca las piezas de la ruleta (en su píxel 255, 0); tiene que coincidir con rendertype_text.vsh/fsh
    static final int FIRMA = 0x3DA75E;
    // Caracteres: cada capa usa piezas*piezas seguidos (fila por fila)
    static final int CH_DISCO = 0xE000, CH_FIJO = 0xE010, CH_FLECHA = 0xE020, CH_BORROSO = 0xE060,
            CH_FUEGO = 0xE100, CH_TINTE = 0xE200;
    static final int CH_MOVER = 0xE1F0;          // 5 espacios: corren la ruleta -2, -1, 0, 1 y 2 (para el temblor)
    static final int CH_SALTAR2 = 0xE1F8;        // lo que avanza una pieza de 2 x 2 (para una pieza vacía)
    static final int CH_SALTAR4 = 0xE1F9;        // lo que avanza una pieza de 4 x 4
    static final int CH_JUNTAR = 0xE1FE;         // -1: pega la pieza derecha a la izquierda (sin el espacio de 1 px)
    static final int CH_ATRAS = 0xE1FF;          // vuelve al inicio de la fila: -(LADO + 1)

    static BufferedImage src, plate, mascara, mascaraFija;
    static double[] radio = new double[3600];    // borde del disco cada 0,1 grados
    static double radioMax;                      // el más grande
    // El borde del dibujo no es redondo (se sale a la izquierda y abajo a la derecha). Como el disco gira,
    // el hueco del aro tiene que ser un círculo: el disco mide DISCO_R, justo más que el borde más grande.
    static double radioMin, DISCO_R, MARCO_FIN;
    // Entre el disco y las gemas va un marco limpio (DISCO_R a MARCO_FIN) con el color de la banda oscura
    // del dibujo en cada ángulo (bandaColor, sin gemas) y un filo de luz, para que las gemas no queden pegadas al disco.
    static int[] bandaColor = new int[360];
    static final int FILO_LUZ = 0x7A4FB0;
    // Las 8 gemas chicas del aro (grados aprox.): sus puntas llegan casi al disco, así que van encima del marco
    static final double[] GEMAS_ANG = {33.5, 74.5, 113.5, 147, 212.5, 246.5, 286, 327};
    static final double GEMA_R = 19;               // radio alrededor del centro de cada gema que se copia tal cual
    static double[][] gemas;                       // centros medidos (x, y en el dibujo)
    // En el dibujo el aro no está centrado en el fénix (el centro de giro): está unos px a la izquierda y
    // abajo, y la banda oscura se veía más ancha de un lado. CX, CY es el centro del aro (medirAro()):
    // la capa fija se arma centrada en él, así el aro queda parejo alrededor del disco.
    static double CX = HX, CY = HY, ARO_IN = 378; // ARO_IN: radio de adentro del dorado
    static Path2D flecha;
    static int[] tinteColor = new int[SEG];      // color de cada segmento de la ruleta (en el orden del layout)

    public static void main(String[] args) throws Exception {
        Path art = Paths.get("tools/ruleta/art");
        src = rgb(ImageIO.read(art.resolve("ruleta.png").toFile()));
        plate = rgb(ImageIO.read(art.resolve("sin_marco.jpg").toFile()));
        mascara = rgb(ImageIO.read(art.resolve("mascara.png").toFile()));
        mascaraFija = rgb(ImageIO.read(art.resolve("mascara_fija.png").toFile()));
        Path vista = args.length > 0 ? Paths.get(args[0]) : null;
        medirAro();
        medirBorde();
        flecha = poligono(FLECHA);
        for (int j = 0; j < SEG; j++) colorBase[j] = medirColor(j);
        for (int k = 0; k < SEG; k++) tinteColor[k] = muestrearColor(k);

        Path root = Paths.get("resourcepack/Ruleta");
        Path tex = root.resolve("assets/ruleta/textures/font");
        Path font = root.resolve("assets/ruleta/font");
        if (Files.exists(tex)) try (var s = Files.list(tex)) { for (Path p : s.toList()) Files.delete(p); }
        Files.createDirectories(tex);
        Files.createDirectories(font);

        StringBuilder json = new StringBuilder("{\n  \"providers\": [\n");
        json.append(String.format("    {\"type\": \"space\", \"advances\": {\"\\u%04X\": %d, \"\\u%04X\": %d, \"\\u%04X\": %d, \"\\u%04X\": %d, "
                        + "\"\\u%04X\": -2, \"\\u%04X\": -1, \"\\u%04X\": 0, \"\\u%04X\": 1, \"\\u%04X\": 2}}",
                CH_JUNTAR, -1, CH_ATRAS, -(LADO + 1), CH_SALTAR2, LADO / 2 + 1, CH_SALTAR4, LADO / 4 + 1,
                CH_MOVER, CH_MOVER + 1, CH_MOVER + 2, CH_MOVER + 3, CH_MOVER + 4));

        // disco: una sola imagen, el shader la gira
        BufferedImage disco = render(HD, (x, y) -> discoEn(x, y, 0, 0));
        glifos(json, tex, "disco", disco, CH_DISCO, null);
        log("disco");

        // disco desenfocado: el desenfoque es igual en todo el giro, así que también lo gira el shader
        BufferedImage[] borrosos = new BufferedImage[DESENFOQUE.length];
        for (int i = 0; i < DESENFOQUE.length; i++) {
            double d = DESENFOQUE[i];
            borrosos[i] = render(MEDIA, (x, y) -> discoEn(x, y, 0, d));
            glifos(json, tex, "borroso_" + i, borrosos[i], CH_BORROSO + 4 * i, null);
        }
        log("borroso");

        // aro y fénix: no se mueven, van en una sola capa
        BufferedImage fijo = render(HD, (x, y) -> Math.hypot(x - HX, y - HY) < ARO_R + 2 ? fijo(x, y, 0) : 0);
        glifos(json, tex, "fijo", fijo, CH_FIJO, null);
        log("fijo");

        // llamas, alas, estrellas, brasas y brillo del fénix (en loop)
        BufferedImage[] fuegos = new BufferedImage[FUEGO];
        for (int f = 0; f < FUEGO; f++) {
            double t = (double) f / FUEGO;
            BufferedImage img = render(MEDIA, (x, y) -> Math.hypot(x - HX, y - HY) >= ARO_R - 2 ? fijo(x, y, t) : 0);
            brillos(img, t);
            fuegos[f] = img;
            glifos(json, tex, String.format("fuego_%02d", f), img, CH_FUEGO + 4 * f, null);
        }
        log("fuego");

        // flecha: solo las piezas donde hay algo (las demás se saltan con un espacio)
        BufferedImage[] flechas = new BufferedImage[FLECHA_ANG.length];
        for (int k = 0; k < FLECHA_ANG.length; k++) {
            double a = FLECHA_ANG[k];
            flechas[k] = render(HD, (x, y) -> flechaEn(x, y, a));
        }
        boolean[] piezasFlecha = piezasUsadas(flechas);
        for (int k = 0; k < FLECHA_ANG.length; k++) glifos(json, tex, "flecha_" + k, flechas[k], CH_FLECHA + 16 * k, piezasFlecha);
        log("flecha");

        // disco teñido del color ganador + aura (va encima de todo menos la flecha), con el disco detenido en ese color
        BufferedImage[][] tintes = new BufferedImage[SEG][TINTE.length];
        for (int k = 0; k < SEG; k++) for (int n = 0; n < TINTE.length; n++) {
            int kk = k;
            double amt = TINTE[n];
            boolean latido = n == TINTE.length - 1;
            tintes[k][n] = render(MEDIA, (x, y) -> tinteEn(x, y, kk, amt, latido));
            glifos(json, tex, "tinte_" + k + "_" + n, tintes[k][n], CH_TINTE + 4 * (k * TINTE.length + n), null);
        }
        log("tinte");

        json.append("\n  ]\n}\n");
        Files.writeString(font.resolve("ruleta.json"), json);

        BufferedImage icono = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        var g = icono.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (BufferedImage capa : new BufferedImage[]{disco, fijo, fuegos[0], flechas[0]}) g.drawImage(capa, 0, 0, 128, 128, null);
        g.dispose();
        ImageIO.write(icono, "png", root.resolve("pack.png").toFile());

        Files.writeString(root.resolve("pack.mcmeta"), """
                {
                  "pack": {
                    "description": "§6VoranixExtremo §7- ruleta, monedas y comida",
                    "pack_format": 15
                  }
                }
                """);

        StringBuilder mascaraFlecha = new StringBuilder();
        for (int i = 0; i < piezasFlecha.length; i++) mascaraFlecha.append(i > 0 ? ", " : "").append(piezasFlecha[i]);
        StringBuilder grados = new StringBuilder();
        for (int i = 0; i < DESENFOQUE.length; i++) grados.append(i > 0 ? ", " : "").append(DESENFOQUE[i]);
        Files.writeString(Paths.get("src/main/resources/ruleta-layout.json"), String.format("""
                {
                  "posiciones": %d,
                  "disco": %d, "fijo": %d,
                  "borroso": %d, "borrosoGrados": [%s],
                  "fuego": %d, "fuegoFrames": %d, "fuegoTicks": %d,
                  "flecha": %d, "flechaFrames": %d, "flechaPiezas": [%s],
                  "tinte": %d, "tinteNiveles": %d,
                  "mover": %d, "juntar": %d, "atras": %d, "saltar2": %d, "saltar4": %d,
                  "segmentos": [%s]
                }
                """, DISCO, CH_DISCO, CH_FIJO, CH_BORROSO, grados, CH_FUEGO, FUEGO, FUEGO_TICKS,
                CH_FLECHA, FLECHA_ANG.length, mascaraFlecha, CH_TINTE, TINTE.length,
                CH_MOVER, CH_JUNTAR, CH_ATRAS, CH_SALTAR2, CH_SALTAR4, orden()));

        if (vista != null) vistaPrevia(vista, disco, borrosos, fijo, fuegos, flechas, tintes);
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
        double d = Math.hypot(x - HX, y - HY), ang = angulo(x, y);
        // el disco es redondo y un poco más grande que el hueco del aro, que va encima y tapa lo que sobra
        if (d > DISCO_R + 4) return 0;
        double rho = Math.min(d / DISCO_R, 0.995);
        if (desenfoque <= 0) return disco(rho, norm(ang - giro), d) | 0xFF000000;
        int n = 9;
        double r = 0, gg = 0, b = 0;
        for (int i = 0; i < n; i++) {
            int c = disco(rho, norm(ang - giro + desenfoque * (2.0 * i / (n - 1) - 1)), d);
            r += (c >> 16) & 255; gg += (c >> 8) & 255; b += c & 255;
        }
        return 0xFF000000 | ((int) (r / n) << 16) | ((int) (gg / n) << 8) | (int) (b / n);
    }

    /**
     * Color del disco en el punto (rho, ángulo de la ruleta nueva). El disco se dibuja limpio: 8 gajos
     * iguales con el color de cada gajo del dibujo (colorBase), todos con el mismo sombreado y líneas
     * del mismo grosor. Antes se estiraban los gajos del dibujo (que son 11 y de distinto ancho) y
     * algunos colores salían deformados.
     */
    static int disco(double rho, double ang, double d) {
        double w = 360.0 / SEG;
        double t = norm(ang + w / 2) / w;
        int j = (int) Math.floor(t) % SEG;
        double rel = norm(ang - (j * w - w / 2));               // grados desde el borde inicial del gajo
        double r = Math.max(d, 1);
        // distancia (en píxeles del dibujo) a la línea divisoria más cercana
        double e = r * Math.sin(Math.toRadians(Math.min(Math.min(rel, w - rel), 90)));
        int c = colorBase[j];
        // un poco más claro hacia el centro y más oscuro hacia el aro, igual en todos los gajos
        double luz = 1.08 - 0.2 * rho;
        // bisel junto a cada línea: sombra pegada a la línea y un filo de luz un poco más adentro
        luz *= 1 - 0.22 * Math.exp(-Math.max(0, e - LINEA) / 5);
        luz += 0.10 * Math.exp(-Math.pow((e - LINEA - 6) / 2.5, 2));
        // borde de afuera más oscuro (lo tapa casi todo el aro)
        if (rho > 0.9) luz *= 1 - 0.35 * (rho - 0.9) / 0.1;
        int col = escalar(c, luz);
        // línea divisoria oscura, suavizada en su último píxel
        double linea = Math.max(0, Math.min(1, LINEA + 0.5 - e));
        return mezclarRGB(col, LINEA_COLOR, linea);
    }

    static final int LINEA_COLOR = 0x241336;
    static int[] colorBase = new int[SEG];                       // color de cada gajo (en el orden de COLORES)

    /** Color típico (mediana) del gajo s del dibujo, sin las líneas ni los bordes. */
    static int medirColor(int j) {
        int s = (int) COLORES[j][1];
        double a0 = DIVISORES[s], a1 = s + 1 < DIVISORES.length ? DIVISORES[s + 1] : DIVISORES[0] + 360;
        List<Integer> rs = new ArrayList<>(), gs = new ArrayList<>(), bs = new ArrayList<>();
        for (double a = a0 + 6; a <= a1 - 6; a += 1) for (double f = 0.35; f <= 0.8; f += 0.03) {
            double aa = norm(a);
            double[] p = polar(f * radioEn(aa), aa);
            if (flecha.contains(p[0], p[1])) continue;
            int c = bilinear(src, p[0], p[1]);
            rs.add((c >> 16) & 255); gs.add((c >> 8) & 255); bs.add(c & 255);
        }
        Collections.sort(rs); Collections.sort(gs); Collections.sort(bs);
        int m = rs.size() / 2;
        int c = (rs.get(m) << 16) | (gs.get(m) << 8) | bs.get(m);
        double giroTono = (double) COLORES[j][2];
        return giroTono == 0 ? c : girarTono(c, giroTono);
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
            int c = disco(f, norm(ang - giroDe(k)), f * DISCO_R);
            r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
        }
        return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }

    // ---------------------------------------------------------------- aro + fuego

    static int fijo(double x, double y, double t) {
        double d = Math.hypot(x - HX, y - HY), ang = angulo(x, y);
        // el hueco es un círculo de radio DISCO_R; hasta MARCO_FIN lo que hay del dibujo (su borde, que no
        // es redondo) queda tapado por el marco que se dibuja más abajo
        double ds = d;
        // el aro se copia centrado en CX, CY; más afuera (llamas) se vuelve de a poco al dibujo tal cual
        double aro = d < DISCO_R - 1 ? 0 : 1 - smoothstep(ARO_R + 5, ARO_R + 30, d);
        // la flecha va en su propia capa: aquí se rellena lo que hay detrás copiando de un poco al lado
        double angS = flecha.intersects(x - 4, y - 4, 8, 8) ? norm(ang + 24) : ang;
        double[] q0 = polarC(ds, angS), q1 = polar(d, angS);
        double sx = q1[0] + (q0[0] - q1[0]) * aro, sy = q1[1] + (q0[1] - q1[1]) * aro;
        boolean hub = d < HUB_R;
        double hoyo = hub ? 0 : clamp(DISCO_R - d + 0.5);
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
        // marco entre el disco y las gemas: banda oscura con un filo de luz, y se funde con el dibujo al final
        if (!hub && d < MARCO_FIN + 3) {
            int m = bandaColor[(int) Math.round(ang) % 360];
            m = mezclarRGB(m, FILO_LUZ, 0.55 * Math.exp(-Math.pow((d - DISCO_R - 6) / 1.6, 2)));
            double w = 1 - smoothstep(MARCO_FIN, MARCO_FIN + 3, d);
            // las gemas quedan enteras, encima del marco
            for (double[] g : gemas) w *= smoothstep(GEMA_R - 2, GEMA_R + 1, Math.hypot(sx - g[0], sy - g[1]));
            p = (p & 0xFF000000) | mezclarRGB(p & 0xFFFFFF, m, w);
        }
        // filo de adentro de la banda: una línea oscura redonda pegada al disco
        if (!hub && d < DISCO_R + 5) p = (p & 0xFF000000) | mezclarRGB(p & 0xFFFFFF, LINEA_COLOR, 1 - smoothstep(DISCO_R + 1.5, DISCO_R + 5, d));

        double fade = desvanecer(x, y);
        int a = (int) Math.round(((p >>> 24) & 255) * (1 - hoyo) * fade * fade);
        return (a << 24) | (p & 0xFFFFFF);
    }

    /**
     * Cuánto se ve lo que está en (x, y), de 0 a 1: las llamas se apagan en círculo, sin dejar ver el
     * recorte cuadrado; abajo se apagan antes, para no mostrar el pedestal del dibujo.
     */
    static double desvanecer(double x, double y) {
        double d = Math.hypot(x - HX, y - HY);
        double borde2 = Math.min(Math.min(x - CROP_X, CROP_X + CROP - x), Math.min(y - CROP_Y, CROP_Y + CROP - y));
        double abajo = smoothstep(0.55, 0.85, (y - HY) / Math.max(d, 1));
        double limite = 600 - 60 * abajo;
        return clamp(borde2 / 30.0) * clamp((limite - d) / (120 - 10 * abajo));
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
        double f0 = desvanecer(cx, cy);
        alpha *= f0 * f0; // lo de los bordes se apaga igual que las llamas
        if (alpha <= 0.002) return;
        if (rgbFin == 0) rgbFin = rgb;
        int n = img.getWidth();
        double k = (double) n / CROP;
        double ox = (cx - CROP_X) * k, oy = (cy - CROP_Y) * k, R = r1 * k, R0 = r0 * k;
        for (int y = (int) Math.floor(oy - R); y <= oy + R; y++)
            for (int x = (int) Math.floor(ox - R); x <= ox + R; x++) {
                if (x < 0 || y < 0 || x >= n || y >= n) continue;
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
        sx += CX - HX; sy += CY - HY; // se mueve con el aro (ver CX, CY)
        return flecha.contains(sx, sy) ? bilinear(src, sx, sy) | 0xFF000000 : 0;
    }

    /**
     * El disco detenido en el segmento k, teñido de su color con fuerza amt, más el aura de color alrededor.
     * Igual que el shader: conserva un poco las luces y sombras del disco para que se vean las líneas.
     */
    static int tinteEn(double x, double y, int k, double amt, boolean latido) {
        double d = Math.hypot(x - HX, y - HY), ang = angulo(x, y), borde = DISCO_R;
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

    // ---------------------------------------------------------------- vista previa

    /**
     * Arma la ruleta como la ve el juego (las capas giradas igual que el shader) para revisarla, y
     * compara el disco girado por el shader con el disco dibujado ya girado: tienen que coincidir.
     */
    static void vistaPrevia(Path dir, BufferedImage disco, BufferedImage[] borrosos, BufferedImage fijo, BufferedImage[] fuegos,
                            BufferedImage[] flechas, BufferedImage[][] tintes) throws Exception {
        Files.createDirectories(dir);
        int k = 3;
        double giro = giroDe(k);
        BufferedImage girado = girar(disco, giro);
        BufferedImage esperado = render(HD, (x, y) -> discoEn(x, y, giro, 0));
        double dif = 0;
        int n = 0;
        for (int y = 0; y < HD; y += 2) for (int x = 0; x < HD; x += 2) {
            int a = girado.getRGB(x, y), b = esperado.getRGB(x, y);
            if ((a >>> 24) < 250 || (b >>> 24) < 250) continue;
            for (int sh = 0; sh <= 16; sh += 8) dif += Math.abs(((a >> sh) & 255) - ((b >> sh) & 255));
            n += 3;
        }
        System.out.printf("disco girado por el shader vs. dibujado girado: diferencia media %.1f / 255%n", dif / n);

        int c = 512, cols = 4;
        BufferedImage hoja = new BufferedImage(c * cols, c, BufferedImage.TYPE_INT_RGB);
        var g = hoja.createGraphics();
        g.setColor(new Color(0x5a7d3a)); // pasto, para ver la transparencia
        g.fillRect(0, 0, hoja.getWidth(), hoja.getHeight());
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        BufferedImage[][] paneles = {
                {disco, fijo, fuegos[0], flechas[0]},
                {girar(borrosos[2], 100), fijo, fuegos[FUEGO / 2], flechas[2]},
                {girado, fijo, fuegos[0], tintes[k][1], flechas[0]},
                {girado, fijo, fuegos[0], tintes[k][TINTE.length - 1], flechas[0]},
        };
        for (int i = 0; i < paneles.length; i++) for (BufferedImage capa : paneles[i]) g.drawImage(capa, i * c, 0, c, c, null);
        g.dispose();
        ImageIO.write(hoja, "png", dir.resolve("vista_previa.png").toFile());

        BufferedImage grande = new BufferedImage(HD, HD, BufferedImage.TYPE_INT_RGB);
        var g2 = grande.createGraphics();
        g2.setColor(new Color(0x5a7d3a));
        g2.fillRect(0, 0, HD, HD);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (BufferedImage capa : new BufferedImage[]{disco, fijo, fuegos[FUEGO / 3], flechas[0]}) g2.drawImage(capa, 0, 0, HD, HD, null);
        g2.dispose();
        ImageIO.write(grande, "png", dir.resolve("vista_grande.png").toFile());
    }

    /** Gira la imagen `grados` alrededor de su centro igual que el shader (sentido horario en pantalla). */
    static BufferedImage girar(BufferedImage img, double grados) {
        int n = img.getWidth();
        double a = Math.toRadians(grados), cs = Math.cos(a), sn = Math.sin(a), c = n / 2.0;
        BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            // el shader lleva cada punto p del glifo a R(a)(p - c) + c: acá se busca de dónde viene cada píxel
            double dx = x + 0.5 - c, dy = y + 0.5 - c;
            int sx = (int) Math.floor(c + cs * dx + sn * dy), sy = (int) Math.floor(c - sn * dx + cs * dy);
            if (sx >= 0 && sy >= 0 && sx < n && sy < n) out.setRGB(x, y, img.getRGB(sx, sy));
        }
        return out;
    }

    // ---------------------------------------------------------------- utilidades

    /**
     * Corta la capa en piezas de TILE px (2 x 2 si mide 512, 4 x 4 si mide 1024) y agrega sus entradas a
     * la fuente: pieza (fila f, columna c) = carácter ch + f * piezas + c. Todas las capas terminan del
     * mismo tamaño en pantalla: las piezas de una capa de 512 son el doble de altas. Si `usadas` no es
     * null, solo se guardan esas piezas (el plugin pone un espacio en las otras).
     */
    static void glifos(StringBuilder json, Path tex, String name, BufferedImage img, int ch, boolean[] usadas) throws Exception {
        int piezas = img.getWidth() / TILE, alto = LADO / piezas;
        for (int f = 0; f < piezas; f++) for (int c = 0; c < piezas; c++) {
            int q = f * piezas + c;
            if (usadas != null && !usadas[q]) continue;
            BufferedImage pieza = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
            pieza.setRGB(0, 0, TILE, TILE, img.getRGB(c * TILE, f * TILE, TILE, TILE, null, 0, TILE), 0, TILE);
            String file = piezas == 1 ? name : name + "_" + q;
            guardar(pieza, tex.resolve(file + ".png"));
            json.append(String.format(",\n    {\"type\": \"bitmap\", \"file\": \"ruleta:font/%s.png\", \"height\": %d, \"ascent\": %d, \"chars\": [\"\\u%04X\"]}",
                    file, alto, ARRIBA - alto * f, ch + q));
        }
    }

    /** Qué piezas de 256 px tienen algo visible en alguna de las imágenes (todas del mismo tamaño). */
    static boolean[] piezasUsadas(BufferedImage[] imgs) {
        int piezas = imgs[0].getWidth() / TILE;
        boolean[] usadas = new boolean[piezas * piezas];
        for (BufferedImage img : imgs) for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++)
            if ((img.getRGB(x, y) >>> 24) > 0) usadas[(y / TILE) * piezas + x / TILE] = true;
        return usadas;
    }

    static void guardar(BufferedImage img, Path file) throws Exception {
        BufferedImage copia = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copia.setRGB(0, 0, img.getWidth(), img.getHeight(), img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth()), 0, img.getWidth());
        firmar(copia);
        ImageIO.write(copia, "png", file.toFile());
    }

    /** Centro del aro: círculo que mejor pasa por el borde de adentro del dorado. */
    static void medirAro() {
        List<double[]> pts = new ArrayList<>();
        double[] g = new double[360];
        for (int ang = 0; ang < 360; ang++) {
            g[ang] = -1;
            for (double d = 355; d < 430; d += 0.5) {
                double[] p = polar(d, ang);
                int c = src.getRGB((int) Math.round(p[0]), (int) Math.round(p[1]));
                if (((c >> 16) & 255) > 190 && ((c >> 8) & 255) > 90 && (c & 255) < 140) { g[ang] = d; break; }
            }
        }
        for (int ang = 0; ang < 360; ang++) {
            double[] w = new double[21];
            for (int k = -10; k <= 10; k++) w[k + 10] = g[(ang + k + 360) % 360];
            Arrays.sort(w);
            if (w[10] > 0 && Math.abs(g[ang] - w[10]) < 3) pts.add(polar(g[ang], ang)); // sin gemas ni flechas
        }
        // ajuste algebraico (Kasa): x² + y² = a x + b y + c
        double[][] m = new double[3][3];
        double[] v = new double[3];
        for (double[] q : pts) {
            double[] f = {q[0], q[1], 1};
            double z = q[0] * q[0] + q[1] * q[1];
            for (int i = 0; i < 3; i++) { v[i] += f[i] * z; for (int j = 0; j < 3; j++) m[i][j] += f[i] * f[j]; }
        }
        for (int i = 0; i < 3; i++) for (int j = i + 1; j < 3; j++) {
            double k = m[j][i] / m[i][i];
            for (int l = i; l < 3; l++) m[j][l] -= k * m[i][l];
            v[j] -= k * v[i];
        }
        double[] x = new double[3];
        for (int i = 2; i >= 0; i--) { double t = v[i]; for (int l = i + 1; l < 3; l++) t -= m[i][l] * x[l]; x[i] = t / m[i][i]; }
        CX = x[0] / 2; CY = x[1] / 2;
        ARO_IN = Math.sqrt(x[2] + CX * CX + CY * CY);
        System.out.printf("centro del aro: (%.1f, %.1f), corrido (%.1f, %.1f) del fénix%n", CX, CY, CX - HX, CY - HY);
    }

    /** Centro de cada gema chica: promedio de sus píxeles claros cerca de donde debería estar. */
    static void medirGemas() {
        gemas = new double[GEMAS_ANG.length][];
        for (int i = 0; i < GEMAS_ANG.length; i++) {
            double[] c0 = polarC(361, GEMAS_ANG[i]);
            double sx = 0, sy = 0, n = 0;
            for (int y = (int) c0[1] - 25; y <= c0[1] + 25; y++) for (int x = (int) c0[0] - 25; x <= c0[0] + 25; x++) {
                if (Math.hypot(x - c0[0], y - c0[1]) > 25) continue;
                int c = src.getRGB(x, y);
                if (Math.max((c >> 16) & 255, Math.max((c >> 8) & 255, c & 255)) > 110 && ((c >> 16) & 255) < 230) { sx += x; sy += y; n++; }
            }
            gemas[i] = n > 0 ? new double[]{sx / n, sy / n} : c0;
        }
    }

    /** Color de la banda oscura del aro en cada grado (mediana, sin gemas ni brillos), suavizado. */
    static void medirBanda() {
        int[] crudo = new int[360];
        boolean[] hay = new boolean[360];
        for (int a = 0; a < 360; a++) {
            List<Integer> rs = new ArrayList<>(), gs = new ArrayList<>(), bs = new ArrayList<>();
            for (double rr = MARCO_FIN + 4; rr < ARO_IN - 6; rr += 1) for (double da = -0.5; da <= 0.5; da += 0.5) {
                double[] q = polarC(rr, a + da);
                int c = bilinear(src, q[0], q[1]);
                int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
                if (Math.max(cr, Math.max(cg, cb)) < 100) { rs.add(cr); gs.add(cg); bs.add(cb); }
            }
            if (rs.size() < 5) continue;
            Collections.sort(rs); Collections.sort(gs); Collections.sort(bs);
            int k = rs.size() / 2;
            crudo[a] = (rs.get(k) << 16) | (gs.get(k) << 8) | bs.get(k);
            hay[a] = true;
        }
        for (int a = 0; a < 360; a++) {
            double r = 0, g = 0, b = 0, n = 0;
            for (int k = -8; k <= 8; k++) {
                int i = Math.floorMod(a + k, 360);
                if (!hay[i]) continue;
                r += (crudo[i] >> 16) & 255; g += (crudo[i] >> 8) & 255; b += crudo[i] & 255; n++;
            }
            bandaColor[a] = n == 0 ? 0x301840 : ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
        }
    }

    static void medirBorde() {
        double[] r = new double[3600];
        for (int i = 0; i < 3600; i++) {
            double a = i / 10.0, d = 240;
            while (d < 380) {
                double[] p = polarC(d, a), q = polarC(d + 2, a);
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
            radioMax = Math.max(radioMax, radio[i]);
        }
        radioMin = Arrays.stream(radio).min().getAsDouble();
        DISCO_R = radioMin + 2;
        MARCO_FIN = radioMax + 3;
        medirBanda();
        medirGemas();
        System.out.printf("borde del disco: %.1f a %.1f px%n", radioMin, radioMax);
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

    /**
     * Pone el color FIRMA en el píxel de arriba a la derecha. Sirve para dos cosas: el shader reconoce
     * ahí las piezas de la ruleta (y el fsh usa el píxel de al lado en su lugar), y Minecraft, que mide
     * el ancho del glifo hasta el último píxel visible, ve todas las piezas del mismo ancho.
     */
    static void firmar(BufferedImage img) {
        int w = img.getWidth() - 1, a = Math.max(1, img.getRGB(w, 0) >>> 24);
        img.setRGB(w, 0, (a << 24) | FIRMA);
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
    static double[] polarC(double d, double ang) { double t = Math.toRadians(ang); return new double[]{CX + d * Math.sin(t), CY - d * Math.cos(t)}; }
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
