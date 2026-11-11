#version 150

// Shader de texto de Minecraft 1.20.1 con dos cambios. Cualquier otro texto del juego se ve igual que siempre.
//
// 1) Ruleta de colores: en 1.20.1 los titles siempre llevan sombra y la ruleta se vería con un
//    fantasma oscuro detrás. El plugin pinta la ruleta de color (76, 92, 36):
//      - ese color se dibuja blanco, o sea, la imagen con sus colores reales;
//      - su sombra (Minecraft la hace con el color x 0,25 = (19, 23, 9)) no se dibuja.
//
// 2) Vidas (cristales): el plugin las manda como título de una boss bar (que sale arriba al
//    centro) pintado de color (80, 92, 40). Acá se mueven a la esquina de arriba a la derecha:
//      - el título llega con ancho total 0, así que la boss bar lo centra justo en la mitad de la
//        pantalla y los cristales quedan todos a la izquierda de ese punto (ver LivesHud.java);
//      - el ancho de la pantalla en unidades del HUD sale de la proyección (2 / ProjMat[0][0]),
//        así que se corre a la derecha lo justo para que el último cristal quede a MARGEN del
//        borde, con cualquier resolución o escala de interfaz;
//      - la altura se fija acá (arriba de todo), no importa si hay otras boss bars antes;
//      - su sombra (20, 23, 10) tampoco se dibuja.

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

// Tiene que coincidir con la fuente voranixextremo:vidas (vidas.json) y LivesHud.java
const float VIDAS_MARGEN = 4.0;      // separación con el borde derecho y con el de arriba
const float VIDAS_BORDE_DER = -3.0;  // dónde termina el cristal de más a la derecha, relativo al centro
const float VIDAS_ALTO = 13.0;       // alto de cada cristal

bool esColor(vec3 c, vec3 rgb) {
    return all(lessThan(abs(c * 255.0 - rgb), vec3(0.6)));
}

void main() {
    vec3 posicion = Position;
    vec4 color = Color;
    bool fuera = false;

    if (esColor(Color.rgb, vec3(76.0, 92.0, 36.0))) {
        color = vec4(1.0, 1.0, 1.0, Color.a);
    } else if (esColor(Color.rgb, vec3(19.0, 23.0, 9.0))) {
        // la sombra de la ruleta: se manda fuera de la pantalla
        fuera = true;
    } else if (esColor(Color.rgb, vec3(80.0, 92.0, 40.0))) {
        float anchoHud = 2.0 / ProjMat[0][0];
        posicion.x += anchoHud - VIDAS_MARGEN - VIDAS_BORDE_DER - floor(anchoHud / 2.0);
        // cada letra es un cuadrado de 4 vértices: arriba-izq, abajo-izq, abajo-der, arriba-der
        int esquina = gl_VertexID % 4;
        posicion.y = (esquina == 1 || esquina == 2) ? VIDAS_MARGEN + VIDAS_ALTO : VIDAS_MARGEN;
        color = vec4(1.0, 1.0, 1.0, Color.a);
    } else if (esColor(Color.rgb, vec3(20.0, 23.0, 10.0))) {
        // la sombra de las vidas
        fuera = true;
    }

    gl_Position = fuera ? vec4(2.0, 2.0, 2.0, 1.0) : ProjMat * ModelViewMat * vec4(posicion, 1.0);
    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * Position, FogShape);
    vertexColor = color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
