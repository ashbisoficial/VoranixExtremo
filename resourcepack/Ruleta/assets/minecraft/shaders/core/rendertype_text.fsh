#version 150

// Shader de texto de Minecraft 1.20.1 con dos cambios; el resto del texto del juego queda igual que siempre.
//
// 1) El juego descarta todo píxel con menos de 10% de opacidad, y eso le cortaba en seco los brillos
//    suaves al Ojo de la muerte y a la ruleta (rendertype_text.vsh avisa con esOjo / esRuleta): a ellos
//    solo se les descarta lo que es casi invisible.
// 2) La ruleta se dibuja suavizada: las letras del juego se pintan píxel a píxel (por eso se veía
//    pixelada al agrandarse o girar); a la ruleta se le mezclan los 4 píxeles vecinos (y, cuando se ve
//    más chica que su imagen, varias muestras), como una imagen normal. El píxel (255, 0) de cada pieza
//    es la firma (ver GenerarRuleta.java): en su lugar se usa el de al lado.

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
in float esRuleta;

out vec4 fragColor;

// un píxel de la pieza, con el color multiplicado por su opacidad (para mezclar sin bordes oscuros)
vec4 pixel(ivec2 p, ivec2 tam) {
    p = clamp(p, ivec2(0), tam - 1);
    if (p.x == tam.x - 1 && p.y == 0) p.x -= 1;
    vec4 c = texelFetch(Sampler0, p, 0);
    return vec4(c.rgb * c.a, c.a);
}

vec4 bilineal(vec2 uv, ivec2 tam) {
    vec2 p = uv * vec2(tam) - 0.5;
    vec2 base = floor(p);
    vec2 f = p - base;
    ivec2 i = ivec2(base);
    return mix(mix(pixel(i, tam), pixel(i + ivec2(1, 0), tam), f.x),
               mix(pixel(i + ivec2(0, 1), tam), pixel(i + ivec2(1, 1), tam), f.x), f.y);
}

vec4 suave(vec2 uv) {
    ivec2 tam = textureSize(Sampler0, 0);
    // cuántos píxeles de la imagen caen en un píxel de la pantalla
    vec2 dx = dFdx(uv), dy = dFdy(uv);
    float paso = max(length(dx * vec2(tam)), length(dy * vec2(tam)));
    vec4 c;
    if (paso <= 1.25) {
        c = bilineal(uv, tam);
    } else {
        // se ve más chica que su imagen: 4 muestras repartidas en el píxel, para que no titile al girar
        c = 0.25 * (bilineal(uv + 0.25 * (dx + dy), tam) + bilineal(uv + 0.25 * (dx - dy), tam)
                  + bilineal(uv - 0.25 * (dx - dy), tam) + bilineal(uv - 0.25 * (dx + dy), tam));
    }
    return c.a > 0.0 ? vec4(c.rgb / c.a, c.a) : vec4(0.0);
}

void main() {
    vec4 color;
    float corte;
    if (esRuleta > 0.5) {
        color = suave(texCoord0) * vertexColor * ColorModulator;
        corte = 0.004;
    } else {
        color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
        corte = esOjo > 0.5 ? 0.004 : 0.1;
    }
    if (color.a < corte) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
