#version 150

// Shader de texto de Minecraft 1.20.1 con un solo cambio: el juego descarta todo píxel con menos de
// 10% de opacidad, y eso le cortaba en seco los brillos suaves al Ojo de la muerte. Para el Ojo
// (rendertype_text.vsh avisa con esOjo) solo se descarta lo que es casi invisible; el resto del
// texto del juego queda igual que siempre.

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float esOjo;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < (esOjo > 0.5 ? 0.004 : 0.1)) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
