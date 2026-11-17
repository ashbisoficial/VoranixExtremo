// Renderiza la animación del Ojo de la muerte desde su modelo de Blockbench (ojo_grande.bbmodel),
// en alta resolución, para el resource pack (fuente ojo:muerte).
//
// Es el mismo diseño del Ojo original (mismo modelo, misma textura y mismas animaciones), pero
// dibujado directo a ANCHO x ALTO px en vez de agrandar cuadros chicos: los bordes quedan nítidos.
// El cuadro es más ancho que alto porque el Ojo (con sus órbitas) también lo es: así se aprovecha.
//
//   spawn (aparece, abre el ojo) -> idle -> rage (se pone naranjo) -> death (se cierra y desaparece)
//
// 120 cuadros (6 s, un cuadro por tick), cada uno cortado en NX x NY piezas de 256 px (Minecraft
// 1.20.1 no acepta letras de más de 256 px). El cuadro i se escribe con la fuente ojo:muerte fila
// por fila (ver OjoMuerte.java): en cada fila P J P J P J P, y entre filas A, con
// Pq = U+E200 + NX*NY*i + q, J = U+E1F0 (-1) y A = U+E1F1 (vuelve al comienzo de la fila).
//
// Uso: node tools/ojo/RenderizarOjo.js <modelo.bbmodel> <carpeta de salida> [cuadros para vista previa...]
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const CUADROS = 120, TILE = 256, NX = 4, NY = 3, ANCHO = NX * TILE, ALTO = NY * TILE, SS = 2;
const ALTO_PIEZA = 20, CH_PIEZAS = 0xE200, CH_JUNTAR = 0xE1F0, CH_ATRAS = 0xE1F1;
/** Inclinación de la cámara (grados): un poco desde arriba, para que las órbitas se vean como elipses. */
const CAMARA_X = 14;
/** Hacia dónde mira la cámara (grados alrededor del eje vertical): 0 = desde el sur. */
const CAMARA_Y = +(process.env.OJO_GIRO || 0);
/** Resplandor suave alrededor de lo brillante. Apagado: el pack pesaba 3 veces más (OJO_BRILLO=1 lo prende). */
const BRILLO = process.env.OJO_BRILLO === "1";
/** Tramos de la animación: cuántos cuadros y qué parte de la animación del modelo recorren. */
const TRAMOS = [
    {anim: 'spawn', cuadros: 45, desde: 0, hasta: 3},
    {anim: 'idle', cuadros: 15, desde: 0, hasta: 0.75},
    {anim: 'rage', cuadros: 32, desde: 0, hasta: 1.6},
    {anim: 'death', cuadros: 28, desde: 0, hasta: 3},
];

// ------------------------------------------------------------------ PNG

function leerPng(buf) {
    let p = 8, ancho, alto, tipo, idat = [], paleta, trns;
    while (p < buf.length) {
        const largo = buf.readUInt32BE(p), nombre = buf.toString('ascii', p + 4, p + 8), datos = buf.subarray(p + 8, p + 8 + largo);
        if (nombre === 'IHDR') { ancho = datos.readUInt32BE(0); alto = datos.readUInt32BE(4); tipo = datos[9]; if (datos[8] !== 8 || datos[12] !== 0) throw new Error('PNG no soportado'); }
        else if (nombre === 'PLTE') paleta = datos;
        else if (nombre === 'tRNS') trns = datos;
        else if (nombre === 'IDAT') idat.push(datos);
        p += 12 + largo;
    }
    const canales = {6: 4, 2: 3, 3: 1, 0: 1, 4: 2}[tipo];
    const crudo = zlib.inflateSync(Buffer.concat(idat)), fila = ancho * canales, px = Buffer.alloc(alto * fila);
    for (let y = 0; y < alto; y++) {
        const f = crudo[y * (fila + 1)], ent = crudo.subarray(y * (fila + 1) + 1, (y + 1) * (fila + 1));
        for (let x = 0; x < fila; x++) {
            const a = x >= canales ? px[y * fila + x - canales] : 0, b = y > 0 ? px[(y - 1) * fila + x] : 0, c = x >= canales && y > 0 ? px[(y - 1) * fila + x - canales] : 0;
            let v = ent[x];
            if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
            else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
            px[y * fila + x] = v & 255;
        }
    }
    const rgba = new Uint8Array(ancho * alto * 4);
    for (let i = 0; i < ancho * alto; i++) {
        if (tipo === 6) for (let k = 0; k < 4; k++) rgba[i * 4 + k] = px[i * 4 + k];
        else if (tipo === 2) { for (let k = 0; k < 3; k++) rgba[i * 4 + k] = px[i * 3 + k]; rgba[i * 4 + 3] = 255; }
        else if (tipo === 3) { const j = px[i]; for (let k = 0; k < 3; k++) rgba[i * 4 + k] = paleta[j * 3 + k]; rgba[i * 4 + 3] = trns && j < trns.length ? trns[j] : 255; }
        else if (tipo === 0) { rgba.fill(px[i], i * 4, i * 4 + 3); rgba[i * 4 + 3] = 255; }
        else { rgba.fill(px[i * 2], i * 4, i * 4 + 3); rgba[i * 4 + 3] = px[i * 2 + 1]; }
    }
    return {ancho, alto, rgba};
}

const CRC = (() => { const t = new Int32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c; } return t; })();
function crc32(b) { let c = -1; for (const x of b) c = CRC[(c ^ x) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; }
function trozo(nombre, datos) {
    const l = Buffer.alloc(4); l.writeUInt32BE(datos.length);
    const nd = Buffer.concat([Buffer.from(nombre, 'ascii'), datos]), c = Buffer.alloc(4); c.writeUInt32BE(crc32(nd));
    return Buffer.concat([l, nd, c]);
}
function escribirPng(archivo, ancho, alto, rgba) {
    const crudo = Buffer.alloc(alto * (ancho * 4 + 1));
    for (let y = 0; y < alto; y++) { crudo[y * (ancho * 4 + 1)] = 0; Buffer.from(rgba.buffer, rgba.byteOffset + y * ancho * 4, ancho * 4).copy(crudo, y * (ancho * 4 + 1) + 1); }
    const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(ancho, 0); ihdr.writeUInt32BE(alto, 4); ihdr[8] = 8; ihdr[9] = 6;
    fs.writeFileSync(archivo, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), trozo('IHDR', ihdr),
        trozo('IDAT', zlib.deflateSync(crudo, {level: 9})), trozo('IEND', Buffer.alloc(0))]));
}

// ------------------------------------------------------------------ matrices (4x4, por filas)

const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
function mul(a, b) { const r = new Array(16).fill(0); for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j]; return r; }
const T = (x, y, z) => [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1];
const S = (x, y, z) => [x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1];
function Rx(g) { const c = Math.cos(g), s = Math.sin(g); return [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1]; }
function Ry(g) { const c = Math.cos(g), s = Math.sin(g); return [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1]; }
function Rz(g) { const c = Math.cos(g), s = Math.sin(g); return [c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; }
const rad = g => g * Math.PI / 180;
/** Rotación como la de Blockbench (orden ZYX: primero X, después Y, después Z). */
const R = (x, y, z) => mul(Rz(rad(z)), mul(Ry(rad(y)), Rx(rad(x))));
const aplicar = (m, p) => [m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3], m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7], m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11]];

// ------------------------------------------------------------------ modelo y animaciones

const [archivoModelo, salida, ...vistas] = process.argv.slice(2);
const modelo = JSON.parse(fs.readFileSync(archivoModelo, 'utf8'));
const textura = leerPng(Buffer.from(modelo.textures[0].source.split(',')[1], 'base64'));
const escalaUv = [textura.ancho / (modelo.resolution?.width || textura.ancho), textura.alto / (modelo.resolution?.height || textura.alto)];
const cubos = new Map(modelo.elements.map(e => [e.uuid, e]));

/** Árbol de huesos: {nombre, uuid, origen, rotacion, hijos (huesos), cubos} */
function hueso(n) {
    return {nombre: n.name, uuid: n.uuid, origen: n.origin || [0, 0, 0], rotacion: n.rotation || [0, 0, 0],
        hijos: n.children.filter(c => typeof c !== 'string').map(hueso),
        cubos: n.children.filter(c => typeof c === 'string').map(c => cubos.get(c)).filter(Boolean)};
}
const raiz = {nombre: 'raiz', uuid: '', origen: [0, 0, 0], rotacion: [0, 0, 0], cubos: [], hijos: []};
for (const n of modelo.outliner) {
    if (typeof n === 'string') { if (cubos.get(n)) raiz.cubos.push(cubos.get(n)); } else raiz.hijos.push(hueso(n));
}
const animaciones = Object.fromEntries(modelo.animations.map(a => [a.name, a]));

const num = v => typeof v === 'number' ? v : parseFloat(v) || 0;
const vec = kf => ['x', 'y', 'z'].map(k => num(kf.data_points[0][k]));

/** Valor de un canal (rotation/position/scale) de un hueso en el tiempo t, o null si no tiene. */
function canal(anim, uuid, nombreCanal, t) {
    const an = anim.animators[uuid];
    if (!an) return null;
    const kf = an.keyframes.filter(k => k.channel === nombreCanal).sort((a, b) => a.time - b.time);
    if (!kf.length) return null;
    if (t <= kf[0].time) return vec(kf[0]);
    if (t >= kf[kf.length - 1].time) return vec(kf[kf.length - 1]);
    let i = 0;
    while (kf[i + 1].time < t) i++;
    const a = kf[i], b = kf[i + 1], u = (t - a.time) / (b.time - a.time);
    const va = vec(a), vb = vec(b);
    if (a.interpolation === 'catmullrom' || b.interpolation === 'catmullrom') {
        const v0 = vec(kf[Math.max(0, i - 1)]), v3 = vec(kf[Math.min(kf.length - 1, i + 2)]);
        return va.map((p1, k) => {
            const p0 = v0[k], p2 = vb[k], p3 = v3[k];
            return 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
        });
    }
    return va.map((p, k) => p + (vb[k] - p) * u);
}

/** Para cada cuadro: animación, tiempo, giro acumulado de los tramos anteriores y cuánto naranjo. */
const cuadros = [];
{
    const acumulado = {};
    let n = 0;
    for (const tramo of TRAMOS) {
        const anim = animaciones[tramo.anim];
        const base = {...acumulado};
        for (let i = 0; i < tramo.cuadros; i++) {
            const t = tramo.desde + (tramo.hasta - tramo.desde) * i / tramo.cuadros;
            cuadros.push({anim, t, giroBase: base, n: n++});
        }
        // lo que giró cada hueso en este tramo se suma al siguiente, así las órbitas no saltan
        for (const uuid of Object.keys(anim.animators)) {
            const r = canal(anim, uuid, 'rotation', tramo.hasta);
            if (r) acumulado[uuid] = (acumulado[uuid] || [0, 0, 0]).map((v, k) => v + r[k]);
        }
    }
}
const inicioRage = TRAMOS[0].cuadros + TRAMOS[1].cuadros;
const naranjo = f => Math.max(0, Math.min(1, (f - inicioRage) / 6));

// ------------------------------------------------------------------ geometría de cada cuadro

const CARAS = {
    north: (f, t) => [[t[0], t[1], f[2]], [f[0], t[1], f[2]], [f[0], f[1], f[2]], [t[0], f[1], f[2]]],
    south: (f, t) => [[f[0], t[1], t[2]], [t[0], t[1], t[2]], [t[0], f[1], t[2]], [f[0], f[1], t[2]]],
    east: (f, t) => [[t[0], t[1], t[2]], [t[0], t[1], f[2]], [t[0], f[1], f[2]], [t[0], f[1], t[2]]],
    west: (f, t) => [[f[0], t[1], f[2]], [f[0], t[1], t[2]], [f[0], f[1], t[2]], [f[0], f[1], f[2]]],
    up: (f, t) => [[f[0], t[1], f[2]], [t[0], t[1], f[2]], [t[0], t[1], t[2]], [f[0], t[1], t[2]]],
    down: (f, t) => [[f[0], f[1], t[2]], [t[0], f[1], t[2]], [t[0], f[1], f[2]], [f[0], f[1], f[2]]],
};

/** Polígonos del cuadro f en coordenadas de cámara (x a la derecha, y hacia abajo, z hacia la cámara). */
function poligonos(c) {
    const camara = mul(Rx(rad(CAMARA_X)), mul(Ry(rad(CAMARA_Y)), T(0, -36, 0)));
    const lista = [];
    const enIris = new Set();
    (function recorrer(h, padre, iris) {
        const rot = canal(c.anim, h.uuid, 'rotation', c.t);
        const base = c.giroBase[h.uuid];
        const pos = canal(c.anim, h.uuid, 'position', c.t) || [0, 0, 0];
        const esc = canal(c.anim, h.uuid, 'scale', c.t) || [1, 1, 1];
        let rx = h.rotacion[0], ry = h.rotacion[1], rz = h.rotacion[2];
        if (rot || base) {
            const r = rot || [0, 0, 0], b = base || [0, 0, 0];
            rx -= r[0] + b[0]; ry -= r[1] + b[1]; rz += r[2] + b[2];
        }
        const o = h.origen;
        const local = mul(T(-pos[0], pos[1], pos[2]), mul(T(o[0], o[1], o[2]), mul(R(rx, ry, rz), mul(S(esc[0], esc[1], esc[2]), T(-o[0], -o[1], -o[2])))));
        const mundo = mul(padre, local);
        const esIris = iris || h.nombre === 'iris';
        for (const cubo of h.cubos) {
            const co = cubo.origin || [0, 0, 0], cr = cubo.rotation || [0, 0, 0], inf = cubo.inflate || 0;
            const m = mul(camara, mul(mundo, mul(T(co[0], co[1], co[2]), mul(R(cr[0], cr[1], cr[2]), T(-co[0], -co[1], -co[2])))));
            const desde = cubo.from.map(v => v - inf), hasta = cubo.to.map(v => v + inf);
            for (const [nombre, cara] of Object.entries(cubo.faces || {})) {
                if (!cara || cara.texture === null || cara.texture === undefined || !cara.uv) continue;
                const v = CARAS[nombre](desde, hasta).map(p => aplicar(m, p)).map(p => [p[0], -p[1], p[2]]);
                lista.push({v, uv: cara.uv, rot: cara.rotation || 0, luz: cubo.light_emission || 0, iris: esIris});
            }
        }
        for (const hijo of h.hijos) recorrer(hijo, mundo, esIris);
    })(raiz, I(), false);
    return lista;
}

// ------------------------------------------------------------------ color

function rgbAHsv(r, g, b) {
    const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
    let h = 0;
    if (d) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
    return [(h * 60 + 360) % 360, max ? d / max : 0, max];
}
function hsvARgb(h, s, v) {
    const c = v * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c;
    const [r, g, b] = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
    return [r + m, g + m, b + m];
}
/** El morado del iris pasa a naranjo/rojo (gira el tono ~105 grados). */
function enfurecer(r, g, b, t) {
    if (t <= 0) return [r, g, b];
    const [h, s, v] = rgbAHsv(r, g, b);
    const [r2, g2, b2] = hsvARgb((h + 105) % 360, Math.min(1, s * 1.1), v);
    return [r + (r2 - r) * t, g + (g2 - g) * t, b + (b2 - b) * t];
}

// ------------------------------------------------------------------ rasterizado

const L = (() => { const l = [-0.35, -0.55, 0.76], n = Math.hypot(...l); return l.map(v => v / n); })();

function renderizar(c, escala) {
    const W = ANCHO * SS, H = ALTO * SS, cx = W / 2, cy = H / 2, k = escala * SS;
    const color = new Float32Array(W * H * 4), prof = new Float32Array(W * H).fill(-Infinity);
    const furia = naranjo(c.n);
    for (const p of poligonos(c)) {
        const pts = p.v.map(q => [cx + q[0] * k, cy + q[1] * k, q[2]]);
        // normal (en cámara) para la luz; las caras de espaldas igual se dibujan (sin culling, como Blockbench)
        const a = p.v[0], b = p.v[1], d = p.v[3];
        const u = [b[0] - a[0], b[1] - a[1], b[2] - a[2]], w = [d[0] - a[0], d[1] - a[1], d[2] - a[2]];
        let nx = u[1] * w[2] - u[2] * w[1], ny = u[2] * w[0] - u[0] * w[2], nz = u[0] * w[1] - u[1] * w[0];
        const nl = Math.hypot(nx, ny, nz) || 1; nx /= nl; ny /= nl; nz /= nl;
        if (nz < 0) { nx = -nx; ny = -ny; nz = -nz; }
        // casi a plena luz, como se ve en Blockbench con la textura brillante
        const luz = p.luz > 0 ? 1 : 0.8 + 0.2 * Math.max(0, nx * L[0] + ny * L[1] + nz * L[2]);
        // esquinas de la textura: arriba-izq, arriba-der, abajo-der, abajo-izq (con la rotación de la cara)
        let esq = [[p.uv[0], p.uv[1]], [p.uv[2], p.uv[1]], [p.uv[2], p.uv[3]], [p.uv[0], p.uv[3]]];
        for (let r = 0; r < (p.rot / 90) % 4; r++) esq = [esq[3], esq[0], esq[1], esq[2]];
        esq = esq.map(e => [e[0] * escalaUv[0], e[1] * escalaUv[1]]);
        const uMin = Math.min(...esq.map(e => e[0])), uMax = Math.max(...esq.map(e => e[0])), vMin = Math.min(...esq.map(e => e[1])), vMax = Math.max(...esq.map(e => e[1]));
        for (const [i0, i1, i2] of [[0, 1, 2], [0, 2, 3]]) {
            const A = pts[i0], B = pts[i1], C = pts[i2];
            const area = (B[0] - A[0]) * (C[1] - A[1]) - (B[1] - A[1]) * (C[0] - A[0]);
            if (Math.abs(area) < 1e-9) continue;
            const x0 = Math.max(0, Math.floor(Math.min(A[0], B[0], C[0]))), x1 = Math.min(W - 1, Math.ceil(Math.max(A[0], B[0], C[0])));
            const y0 = Math.max(0, Math.floor(Math.min(A[1], B[1], C[1]))), y1 = Math.min(H - 1, Math.ceil(Math.max(A[1], B[1], C[1])));
            for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
                const px = x + 0.5, py = y + 0.5;
                const w0 = ((B[0] - px) * (C[1] - py) - (B[1] - py) * (C[0] - px)) / area;
                const w1 = ((C[0] - px) * (A[1] - py) - (C[1] - py) * (A[0] - px)) / area;
                const w2 = 1 - w0 - w1;
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
                const z = w0 * A[2] + w1 * B[2] + w2 * C[2];
                const idx = y * W + x;
                if (z <= prof[idx]) continue;
                const uu = w0 * esq[i0][0] + w1 * esq[i1][0] + w2 * esq[i2][0];
                const vv = w0 * esq[i0][1] + w1 * esq[i1][1] + w2 * esq[i2][1];
                const tx = Math.min(Math.ceil(uMax) - 1, Math.max(Math.floor(uMin), Math.floor(uu)));
                const ty = Math.min(Math.ceil(vMax) - 1, Math.max(Math.floor(vMin), Math.floor(vv)));
                const ti = ((ty % textura.alto + textura.alto) % textura.alto * textura.ancho + (tx % textura.ancho + textura.ancho) % textura.ancho) * 4;
                if (textura.rgba[ti + 3] < 128) continue;
                let r = textura.rgba[ti] / 255, g = textura.rgba[ti + 1] / 255, bl = textura.rgba[ti + 2] / 255;
                if (p.iris) [r, g, bl] = enfurecer(r, g, bl, furia);
                prof[idx] = z;
                color[idx * 4] = r * luz; color[idx * 4 + 1] = g * luz; color[idx * 4 + 2] = bl * luz; color[idx * 4 + 3] = 1;
            }
        }
    }
    // promedio de SS x SS (bordes suaves) en color premultiplicado
    const out = new Float32Array(ANCHO * ALTO * 4);
    for (let y = 0; y < ALTO; y++) for (let x = 0; x < ANCHO; x++) {
        const o = (y * ANCHO + x) * 4;
        for (let dy = 0; dy < SS; dy++) for (let dx = 0; dx < SS; dx++) {
            const i = ((y * SS + dy) * W + x * SS + dx) * 4;
            for (let k2 = 0; k2 < 4; k2++) out[o + k2] += color[i + k2];
        }
        for (let k2 = 0; k2 < 4; k2++) out[o + k2] /= SS * SS;
    }
    if (!BRILLO) return Uint8Array.from(out, (v, i) => Math.round(Math.min(1, i % 4 === 3 ? v : v / Math.max(out[i - i % 4 + 3], 1e-6)) * 255));
    return brillo(out);
}

/** Un resplandor suave alrededor de lo más brillante (el iris, las runas, los anillos). */
function brillo(img) {
    const n = ANCHO * ALTO, fuente = new Float32Array(n * 4);
    for (let i = 0; i < n; i++) {
        const a = img[i * 4 + 3]; if (!a) continue;
        const r = img[i * 4] / a, g = img[i * 4 + 1] / a, b = img[i * 4 + 2] / a;
        const luma = 0.3 * r + 0.55 * g + 0.15 * b, peso = Math.max(0, Math.min(1, (Math.max(r, g, b) - 0.45) / 0.35)) * a;
        for (let k = 0; k < 3; k++) fuente[i * 4 + k] = img[i * 4 + k] / a * peso;
        fuente[i * 4 + 3] = peso * (0.5 + 0.5 * luma);
    }
    let borroso = fuente;
    for (let pasada = 0; pasada < 3; pasada++) borroso = cajaBlur(cajaBlur(borroso, 9, true), 9, false);
    const out = new Uint8Array(n * 4);
    for (let i = 0; i < n; i++) {
        const a = img[i * 4 + 3], ga = Math.min(1, borroso[i * 4 + 3] * 2.2) * (1 - a);
        const tot = a + ga;
        if (tot <= 0) continue;
        for (let k = 0; k < 3; k++) {
            const gc = borroso[i * 4 + 3] > 0 ? borroso[i * 4 + k] / Math.max(borroso[i * 4 + 3], 1e-6) * 0.9 : 0;
            out[i * 4 + k] = Math.round(Math.min(1, (img[i * 4 + k] + Math.min(1, gc) * ga) / tot) * 255);
        }
        out[i * 4 + 3] = Math.round(Math.min(1, tot) * 255);
    }
    return out;
}

function cajaBlur(src, r, horizontal) {
    const out = new Float32Array(src.length), d = 2 * r + 1;
    const largo = horizontal ? ANCHO : ALTO, otro = horizontal ? ALTO : ANCHO;
    for (let a = 0; a < otro; a++) {
        const suma = [0, 0, 0, 0];
        const idx = b => (horizontal ? a * ANCHO + b : b * ANCHO + a) * 4;
        for (let b = -r; b <= r; b++) if (b >= 0 && b < largo) for (let k = 0; k < 4; k++) suma[k] += src[idx(b) + k];
        for (let b = 0; b < largo; b++) {
            for (let k = 0; k < 4; k++) out[idx(b) + k] = suma[k] / d;
            const sale = b - r, entra = b + r + 1;
            if (sale >= 0) for (let k = 0; k < 4; k++) suma[k] -= src[idx(sale) + k];
            if (entra < largo) for (let k = 0; k < 4; k++) suma[k] += src[idx(entra) + k];
        }
    }
    return out;
}

// ------------------------------------------------------------------ armar el pack

// escala: lo más grande que entre en el cuadro en todos los cuadros (con un margen para el brillo)
let extX = 1, extY = 1;
for (const c of cuadros) for (const p of poligonos(c)) for (const q of p.v) { extX = Math.max(extX, Math.abs(q[0])); extY = Math.max(extY, Math.abs(q[1])); }
const escala = Math.min((ANCHO / 2 - 22) / extX, (ALTO / 2 - 22) / extY);
console.log('escala ' + escala.toFixed(2) + ' px por unidad (extensión ' + extX.toFixed(1) + ' x ' + extY.toFixed(1) + ')');

const tex = path.join(salida, 'assets/ojo/textures/muerte'), fuente = path.join(salida, 'assets/ojo/font');
fs.mkdirSync(tex, {recursive: true});
fs.mkdirSync(fuente, {recursive: true});
for (const f of fs.readdirSync(tex)) fs.unlinkSync(path.join(tex, f));

for (const c of cuadros) {
    const img = renderizar(c, escala);
    if (vistas.includes(String(c.n))) escribirPng(path.join(salida, `vista_${c.n}.png`), ANCHO, ALTO, img);
    for (let q = 0; q < NX * NY; q++) {
        const pieza = new Uint8Array(TILE * TILE * 4);
        for (let y = 0; y < TILE; y++) {
            const desde = (((q / NX | 0) * TILE + y) * ANCHO + (q % NX) * TILE) * 4;
            pieza.set(img.subarray(desde, desde + TILE * 4), y * TILE * 4);
        }
        // Minecraft mide el ancho hasta el último píxel visible: así todas las piezas miden lo mismo
        if (pieza[(TILE - 1) * 4 + 3] === 0) pieza[(TILE - 1) * 4 + 3] = 1;
        escribirPng(path.join(tex, `f${String(c.n).padStart(3, '0')}_${q}.png`), TILE, TILE, pieza);
    }
    if (c.n % 20 === 0) console.log('cuadro ' + c.n);
}

// Minecraft no acepta ascent mayor que el alto: la fila de arriba va con ascent = alto y cada fila
// de abajo baja una pieza. El cuadro va de ALTO_PIEZA arriba de la línea a 2 abajo y su centro queda
// ALTO_PIEZA / 2 más abajo (el shader lo sabe: OJO_CENTRO_Y).
const hex = n => '\\u' + n.toString(16).toUpperCase().padStart(4, '0');
const filaAncho = NX * (ALTO_PIEZA + 1) - (NX - 1);
const proveedores = [`    {"type": "space", "advances": {"${hex(CH_JUNTAR)}": -1, "${hex(CH_ATRAS)}": ${-filaAncho}}}`];
for (let i = 0; i < CUADROS; i++) for (let q = 0; q < NX * NY; q++) {
    proveedores.push(`    {"type": "bitmap", "file": "ojo:muerte/f${String(i).padStart(3, '0')}_${q}.png", "height": ${ALTO_PIEZA}, "ascent": ${ALTO_PIEZA - (q / NX | 0) * ALTO_PIEZA}, "chars": ["${hex(CH_PIEZAS + NX * NY * i + q)}"]}`);
}
fs.writeFileSync(path.join(fuente, 'muerte.json'), '{\n  "providers": [\n' + proveedores.join(',\n') + '\n  ]\n}\n');
console.log(CUADROS + ' cuadros de ' + ANCHO + 'x' + ALTO + ' px en ' + NX * NY + ' piezas -> ' + path.resolve(salida));
