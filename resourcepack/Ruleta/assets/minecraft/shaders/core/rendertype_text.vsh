#version 150

// Shader de texto de Minecraft 1.20.1 con tres cambios. Cualquier otro texto del juego se ve igual que siempre.
//
// 1) Ruleta de colores: el plugin la manda como title, una capa encima de otra (disco, aro, fuego,
//    tinte, flecha). Sus piezas se reconocen porque traen el color FIRMA en su píxel (255, 0) (lo pone
//    tools/ruleta/GenerarRuleta.java). El color del texto de cada capa no es un color: trae cuánto hay
//    que agrandarla y girarla (ver RuletaManager.java):
//      - verde 93..96 (los 2 bits bajos del giro) y rojo (los 8 altos): giro en 1024 pasos por vuelta;
//      - azul: tamaño (200 = normal, 0 = un punto).
//    Así aparece agrandándose, el disco gira suave (sin cuadros sueltos) y se va achicándose, todo en
//    vivo. Se dibuja blanco (sus colores reales) y su sombra (que sale con el verde x 0,25) no se dibuja.
//
// 2) Vidas (cristales): el plugin las manda como título de una boss bar (que sale arriba al
//    centro) pintado de color (80, 92, 40). Acá se mueven a la esquina de abajo a la derecha:
//      - el título llega con ancho total 0, así que la boss bar lo centra justo en la mitad de la
//        pantalla y los cristales quedan todos a la izquierda de ese punto (ver LivesHud.java);
//      - el ancho de la pantalla en unidades del HUD sale de la proyección (2 / ProjMat[0][0]),
//        así que se corre a la derecha lo justo para que el último cristal quede a MARGEN del
//        borde, con cualquier resolución o escala de interfaz;
//      - la altura se fija acá (abajo de todo, con el alto de 2 / ProjMat[1][1]), no importa si
//        hay otras boss bars antes;
//      - su sombra (20, 23, 10) tampoco se dibuja.
//
// 3) Ojo de la muerte: el plugin lo manda como title de color (84, 92, 44). Se dibuja blanco (sus
//    colores reales), sin sombra (21, 23, 11), y se agranda desde su centro para que ocupe casi
//    todo el alto de la pantalla, sea cual sea la resolución o la escala de interfaz.
//
// Dónde queda cada pieza de un title (Minecraft 1.20.1): el title se dibuja a 4x desde el medio de la
// pantalla, en y = -10; la parte de arriba de cada glifo queda en y + 7 - ascent y mide "height".

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float esOjo;    // para rendertype_text.fsh: al Ojo no se le cortan los brillos suaves
out float esRuleta; // para rendertype_text.fsh: la ruleta se dibuja suavizada

// Tiene que coincidir con la fuente voranixextremo:vidas (vidas.json) y LivesHud.java
const float VIDAS_MARGEN = 2.0;      // separación con el borde derecho y con el de abajo (el aura ya trae su margen)
const float VIDAS_BORDE_DER = 3.0;   // dónde termina el cuadro (cristal + aura) de más a la derecha, relativo al centro
const float VIDAS_ALTO = 26.0;       // alto de cada cuadro (cristal de 20 + aura)

// Tiene que coincidir con la fuente ojo:muerte (tools/ojo/RenderizarOjo.js) y OjoMuerte.java
const float OJO_PANTALLA = 0.7;      // alto del cuadro del Ojo en pantalla (1.0 = todo el alto)
const float OJO_ALTO = 240.0;        // alto que le da el juego: 3 piezas de 20 en la fuente x 4 (los titles van a x4)
const float OJO_CENTRO_Y = 28.0;     // su centro: piezas de y = -23 a 37 en la fuente -> 7 x 4 más abajo del medio

// Tiene que coincidir con tools/ruleta/GenerarRuleta.java (FIRMA, LADO, ARRIBA)
const vec3 RULETA_FIRMA = vec3(61.0, 167.0, 94.0);
const float RULETA_CENTRO_Y = 52.0;  // piezas de y = -19 a 45 en la fuente -> su centro, 13 x 4 más abajo del medio
const float RULETA_TAMANO = 1.05;    // cuadro un 5 % más grande: con escala de GUI 4 en 1080p ocupa justo el alto de la pantalla

bool esColor(vec3 c, vec3 rgb) {
    return all(lessThan(abs(c * 255.0 - rgb), vec3(0.6)));
}

void main() {
    vec3 posicion = Position;
    vec4 color = Color;
    bool fuera = false;
    esOjo = 0.0;
    esRuleta = 0.0;

    // las piezas de la ruleta ocupan cada una su página de textura entera: su píxel (255, 0) es la firma
    vec4 firma = texelFetch(Sampler0, ivec2(textureSize(Sampler0, 0).x - 1, 0), 0);
    vec3 dato = floor(Color.rgb * 255.0 + 0.5);

    if (firma.a > 0.0 && esColor(firma.rgb, RULETA_FIRMA) && dato.g >= 93.0 && dato.g <= 96.0) {
        float giro = (dato.r * 4.0 + dato.g - 93.0) * (6.28318530718 / 1024.0);
        float escala = dato.b / 200.0 * RULETA_TAMANO;
        float anchoHud = 2.0 / ProjMat[0][0];
        float altoHud = 2.0 / abs(ProjMat[1][1]);
        // el title sale desde la mitad de la pantalla (en píxeles enteros del HUD)
        vec2 mitad = floor(vec2(ceil(anchoHud - 0.01), ceil(altoHud - 0.01)) / 2.0);
        vec2 d = posicion.xy - (mitad + vec2(0.0, RULETA_CENTRO_Y));
        float c = cos(giro), s = sin(giro);
        // se agranda y gira desde su centro, y ese centro queda justo en el medio de la pantalla
        posicion.xy = mitad + escala * vec2(d.x * c - d.y * s, d.x * s + d.y * c);
        color = vec4(1.0, 1.0, 1.0, Color.a);
        esRuleta = 1.0;
    } else if (firma.a > 0.0 && esColor(firma.rgb, RULETA_FIRMA) && dato.g >= 23.0 && dato.g <= 24.0) {
        // la sombra de la ruleta
        fuera = true;
    } else if (esColor(Color.rgb, vec3(80.0, 92.0, 40.0))) {
        float anchoHud = 2.0 / ProjMat[0][0];
        posicion.x += anchoHud - VIDAS_MARGEN - VIDAS_BORDE_DER - floor(anchoHud / 2.0);
        // cada letra es un cuadrado de 4 vértices: arriba-izq, abajo-izq, abajo-der, arriba-der
        // abajo de todo: el alto de la pantalla sale igual que el ancho (ProjMat[1][1] = -2 / alto)
        float altoHud = 2.0 / abs(ProjMat[1][1]);
        int esquina = gl_VertexID % 4;
        posicion.y = (esquina == 1 || esquina == 2) ? altoHud - VIDAS_MARGEN : altoHud - VIDAS_MARGEN - VIDAS_ALTO;
        color = vec4(1.0, 1.0, 1.0, Color.a);
    } else if (esColor(Color.rgb, vec3(20.0, 23.0, 10.0))) {
        // la sombra de las vidas
        fuera = true;
    } else if (esColor(Color.rgb, vec3(84.0, 92.0, 44.0))) {
        // el Ojo: se agranda desde su centro hasta ocupar OJO_PANTALLA del alto de la pantalla
        float anchoHud = 2.0 / ProjMat[0][0];
        float altoHud = 2.0 / abs(ProjMat[1][1]);
        vec2 mitad = vec2(floor(anchoHud / 2.0), floor(altoHud / 2.0));
        vec2 centro = mitad + vec2(0.0, OJO_CENTRO_Y);
        // se agranda desde su centro y ese centro se lleva justo al medio de la pantalla
        posicion.xy = mitad + (posicion.xy - centro) * (OJO_PANTALLA * altoHud / OJO_ALTO);
        color = vec4(1.0, 1.0, 1.0, Color.a);
        esOjo = 1.0;
    } else if (esColor(Color.rgb, vec3(21.0, 23.0, 11.0))) {
        // la sombra del Ojo
        fuera = true;
    }

    gl_Position = fuera ? vec4(2.0, 2.0, 2.0, 1.0) : ProjMat * ModelViewMat * vec4(posicion, 1.0);
    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * Position, FogShape);
    vertexColor = color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
