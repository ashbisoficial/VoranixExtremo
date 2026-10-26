#version 150

// Shader de texto de Minecraft 1.20.1 con un solo cambio, para la ruleta de colores:
// en 1.20.1 los titles siempre llevan sombra y la ruleta se vería con un fantasma oscuro detrás.
// El plugin pinta la ruleta de color (76, 92, 36):
//   - ese color se dibuja blanco, o sea, la imagen con sus colores reales;
//   - su sombra (Minecraft la hace con el color x 0,25 = (19, 23, 9)) no se dibuja.
// Cualquier otro texto del juego se ve igual que siempre.

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

bool esColor(vec3 c, vec3 rgb) {
    return all(lessThan(abs(c * 255.0 - rgb), vec3(0.6)));
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * Position, FogShape);
    vec4 color = Color;
    if (esColor(Color.rgb, vec3(76.0, 92.0, 36.0))) {
        color = vec4(1.0, 1.0, 1.0, Color.a);
    } else if (esColor(Color.rgb, vec3(19.0, 23.0, 9.0))) {
        // la sombra de la ruleta: se manda fuera de la pantalla
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    }
    vertexColor = color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
