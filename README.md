# VoranixExtremo

Plugin de Minecraft (Paper/Spigot) para una serie estilo **Minecraft Extremo / Desafío**, con el formato de eventos tipo **Eufonia Studio** (fases, eliminación, mecánicas sincronizadas) como referencia a futuro.

## Requisitos

- **Java 17** o más nuevo para compilar (el plugin se compila para Java 17, que es lo que usa la 1.20.1)
- Maven 3.9+
- Servidor **Spigot 1.20.1** (también corre en Paper 1.20.1)

## Compilar

```bash
mvn clean package
```

El `.jar` queda en `target/VoranixExtremo-1.0.0.jar`. Cópialo a la carpeta `plugins/` del servidor.

> **Nota:** este proyecto descarga el API de Spigot desde `hub.spigotmc.org`. Si compilas en un entorno con red restringida (como una sesión en la nube sin ese dominio permitido), añade `hub.spigotmc.org` a los dominios permitidos del entorno o compílalo en tu máquina local.

> **Sobre la versión:** el plugin apunta a **Spigot 1.20.1** (`pom.xml` → `spigot.version`, Java 17). Antes apuntaba a Paper 26.3; al bajarlo se volvió a los nombres de la 1.20.1 (`Attribute.GENERIC_MAX_HEALTH`, `Particle.REDSTONE`, `Particle.TOTEM`, `setBasePotionData`), los títulos usan `Player#sendTitle` de Spigot, y la ruleta de colores trae Adventure metido dentro del jar (Spigot no lo incluye) para mandar su title con fuente propia. Como la 1.20.1 acepta **un solo resource pack del servidor**, el pack que sirve el plugin junta la ruleta (`resourcepack/Ruleta`) con las monedas y la comida (`resourcepack/assets`), e incluye un shader (`resourcepack/Ruleta/assets/minecraft/shaders/core/rendertype_text.vsh`) que le quita la sombra al title de la ruleta.

## Funcionalidades actuales

### Sistema de vidas (`/vidas`)

- Cada jugador empieza con **5 vidas** (configurable en `config.yml` → `lives.max`).
- Al morir, pierde una vida y se avisa en el chat.
- Al llegar a 0 vidas queda **eliminado**: pasa a modo espectador (configurable en `lives.elimination-gamemode`) y se mantiene así aunque se reconecte.
- Comandos:
  - `/vidas` — ver tus propias vidas.
  - `/vidas <jugador>` — ver las vidas de otro jugador.
  - `/vidas set <jugador> <cantidad>` — (admin) establece las vidas de un jugador.
  - `/vidas reset <jugador>` — (admin) restablece las vidas al máximo y lo saca de espectador si estaba eliminado.
  - `/vidas revivir <jugador>` — (admin) el "revivir" de verdad: le da **una sola vida** (no el máximo) y dispara la tormenta (`/fx tormenta`, nubes + rayos) justo donde está parado el jugador — pensado para el momento de traerlo de vuelta con el ítem legendario. Necesita que esté conectado (para la ubicación de la tormenta); si está offline, usá `/vidas set` o `/vidas reset`.
- Permisos: `voranixextremo.vidas` (todos) y `voranixextremo.vidas.admin` (operadores).
- Los datos se guardan en `plugins/VoranixExtremo/lives.yml` y persisten entre reinicios.
- El HUD en pantalla con las vidas (ícono custom, posición) lo maneja el mod `VoranixVisualFX` aparte — el plugin ya no trae un sidebar propio para esto.

### Tope de tiempo jugado (`/tiempo`)

Todos arrancan con el mismo presupuesto de horas (**3 por defecto**, configurable), que se va gastando segundo a segundo mientras cada uno está conectado — así nadie se adelanta farmeando/avanzando más que el resto solo por jugar más horas reales. Cada jugador ve **únicamente su propia** barra (una BossBar arriba de la pantalla, no un sidebar compartido) vaciándose de verde → amarillo → rojo a medida que gasta su tiempo.

Al llegar a 0, el jugador pasa a **modo espectador** automáticamente (se avisa en pantalla) hasta que staff le resetee el tiempo o le agregue más — no hay otra forma de "pausar" el reloj (no cuenta nada mientras está desconectado, eso sí).

- `/tiempo` — ver tu propio tiempo restante.
- `/tiempo <jugador>` — ver el de otro jugador.
- `/tiempo set <jugador> <horasUsadas>` — (admin) fija directamente cuántas horas lleva usadas ese jugador.
- `/tiempo agregar <jugador> <horas>` — (admin) le suma esa cantidad de horas al tiempo **disponible** (le resta al tiempo usado) — para dar un bonus sin tener que calcular el total a mano.
- `/tiempo reset <jugador>` — (admin) vuelve a dejarlo con el presupuesto completo (0 horas usadas) y lo saca de espectador si estaba ahí por esto.
- Permisos: `voranixextremo.tiempo` (todos, incluye consultar a otros) y `voranixextremo.tiempo.admin` (operadores).
- El tope se cambia en `config.yml` → `playtime.limite-horas` (y el texto de la barra en `playtime.titulo`) — cambiarlo no toca lo que cada jugador ya tiene jugado.
- Los datos se guardan en `plugins/VoranixExtremo/playtime.yml` y persisten entre reinicios.

### Control de PVP (`/pvp`)

- `/pvp`, `/pvp toggle`, `/pvp on`, `/pvp off` — activa/desactiva el PVP del servidor.
- Al cambiar el estado se reproduce un sonido a todos los jugadores conectados (configurable en `config.yml` → `pvp.sound`, por defecto `ENTITY_ENDER_DRAGON_GROWL`; cualquier valor del [enum `Sound`](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html) sirve).
- Con PVP desactivado se cancela el daño entre jugadores (incluye proyectiles).
- Permiso: `voranixextremo.pvp` (operadores por defecto).

Todos los mensajes (vidas, eliminación, PVP, errores) son editables en `config.yml` bajo `messages:` y admiten códigos de color con `&`.

### Animaciones/efectos de muerte

Al morir un jugador (sea que pierda una vida o quede eliminado) se reproduce a **todos los jugadores conectados**:

- Una partícula en el punto de la muerte (`death-effects.particle`, por defecto `EXPLOSION_HUGE`).
- Un sonido global (`death-effects.sound`, por defecto `ENTITY_GENERIC_EXPLODE`).
- Un rayo decorativo en ese punto, sin daño ni fuego (`death-effects.lightning-effect`).
- Un título grande en pantalla (`death-effects.title`), con texto distinto si fue una muerte normal o una eliminación.

Todo se puede desactivar o cambiar en `config.yml` → `death-effects` sin tocar código (cualquier [`Particle`](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Particle.html) o [`Sound`](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html) válido sirve). Para una animación más elaborada (ej. un fuego artificial real, un esqueleto decorativo, un modelo con un resource pack) hay que escribir código específico — avisá qué tipo de animación tenés en mente y la agrego.

### Economía (`/dinero`, `/banco`, `/moneda`)

Sistema de monedas físicas + balance, pensado para que funcione junto al comercio vanilla de aldeanos (esmeraldas) sin tocarlo — esta es una moneda aparte para tiendas, kits y cosas que agreguemos al plugin más adelante.

- **Monedas físicas**: 6 denominaciones (`economy.coins` en `config.yml`), todas sobre `IRON_NUGGET` + un `custom-model-data` distinto para que el resource pack le ponga la textura real: **Estaño** (1), **Plata** (10), **Oro** (100), **Mithril** (1.000), **Oricalco** (10.000) y **Ajna** (100.000) — escala x10 por nivel, tal cual está definida en `CoinDenomination.java` del repo [voranixmod](https://github.com/ashbisoficial/voranixmod). Las texturas y colores (por rareza: común/poco común/raro/épico) también vienen de ahí.
- **Balance ("banco")**: cada jugador tiene un balance guardado en `economy.yml`, independiente de las monedas físicas que tenga en el inventario.
- Comandos:
  - `/dinero` — ver tu balance.
  - `/dinero pagar <jugador> <cantidad>` — transferir balance a otro jugador.
  - `/dinero dar|quitar|set <jugador> <cantidad>` — (admin) administrar el balance de cualquiera.
  - `/banco depositar` — convierte todas las monedas físicas de tu inventario en balance.
  - `/banco retirar <cantidad>` — te da monedas físicas por ese monto (usando las denominaciones configuradas, de mayor a menor, como dar cambio; si el monto no se puede armar exacto, avisa y no retira nada).
  - `/moneda dar <jugador> <tipo> [cantidad]` — (admin) entrega monedas físicas directamente (ej. `/moneda dar Ash oro 5`).
- Permisos: `voranixextremo.economia` (todos, para `/dinero` y `/banco` propios), `voranixextremo.dinero.admin` y `voranixextremo.moneda.admin` (operadores).
- **Compatibilidad con Vault**: si el plugin [Vault](https://www.spigotmc.org/resources/vault.34315/) está instalado en el servidor, VoranixExtremo se registra como su proveedor de economía automáticamente (`economy.vault-integration: true`). Así, cualquier plugin de tiendas/subastas/trabajos que use Vault (ChestShop, etc.) va a leer y modificar este mismo balance sin configuración adicional.
- **Protección**: como las monedas son ítems vanilla con una textura encima, el plugin bloquea que se usen como ingrediente de crafteo, combustible de horno, o en yunque/mesa de herrería, para que no se "gasten" por accidente ni generen exploits.

> Nota sobre los modelos: hoy el ítem se construye con material base + `custom-model-data` (el método estándar sin plugins extra, vía resource pack). Si en algún momento definen las monedas en un plugin de ítems personalizados (Oraxen/ItemsAdder/Nexo), hay que avisar — se cambia solo `CoinItemFactory` para pedirle el ítem a ese plugin en vez de crearlo acá; el resto (balance, comandos, Vault) no cambia.

#### Resource pack de las monedas

El mod de Forge "Voranix Coins" que armaron antes **no se puede usar tal cual** en un servidor Paper (son dos ecosistemas distintos: un mod de Forge necesita que todos instalen Forge + el mod; un plugin de Paper no necesita que el jugador instale nada). Lo que se reaprovechó de ese mod son los **6 diseños/texturas** (`estano`, `silver`, `gold`, `mithril`, `oricalco`, `ajna`), reempaquetados como un resource pack vanilla en [`resourcepack/`](resourcepack/):

```
resourcepack/
├── pack.mcmeta
└── assets/
    ├── minecraft/models/item/
    │   ├── iron_nugget.json      # overrides por custom-model-data (monedas)
    │   ├── cooked_porkchop.json  # overrides por custom-model-data (banderillas)
    │   └── potion.json           # overrides por custom-model-data (bebidas)
    └── voranixextremo/
        ├── models/item/*.json    # un modelo por moneda/comida/bebida
        └── textures/item/*.png  # las texturas originales (del mod Forge)
```

Para usarlo en el servidor:

1. Empaquetarlo en un `.zip` (el contenido de `pack.mcmeta` y `assets/` tiene que quedar en la RAÍZ del zip, no dentro de una subcarpeta):
   ```bash
   cd resourcepack && zip -r ../VoranixExtremo-resourcepack.zip . && cd ..
   ```
2. Subir ese `.zip` a algún lugar con URL directa de descarga (ej. un [Release de GitHub](https://docs.github.com/es/repositories/releasing-projects-on-github)).
3. Pegar esa URL en `config.yml` → `resourcepack.url`. Al conectarse, cada jugador recibe automáticamente la oferta de instalar el resource pack (no es obligatorio aceptarla, pero si no la aceptan ven las monedas como un lingote de hierro genérico en vez de la textura real).

### Trofeos de asesinato PVP (`pvp-kill-rewards`)

Cuando un jugador mata a otro **en combate** (se detecta con `getKiller()`, que Bukkit solo pone si el último golpe fue de un jugador — no aplica a caídas, lava, mobs, etc.), además de la animación de muerte normal pasa esto en el lugar de la muerte:

- Se arma un mini monumento: un bloque de `pvp-kill-rewards.trophy-wall-material` (por defecto `DEEPSLATE_BRICK_WALL`) con la **cabeza de la víctima** montada encima, con su skin real (`pvp-kill-rewards.head-drop`).
- Al ponerse: sonido + un remolino breve de partículas de alma alrededor de la cabeza (unos segundos, `trophy-particle-duration-ticks`), y queda flotando sobre la cabeza un **Cristal del Fin** permanente (`trophy-crystal`) — da el brillo/haz de luz giratorio nativo del juego gratis, sin que el plugin tenga que animar nada tick a tick. Es invulnerable para que nadie lo haga explotar a golpes.
- **Monedas** de botín, por un valor configurable (`pvp-kill-rewards.coin-reward.value`, por defecto 50), van **directo al inventario del asesino** (repartidas automáticamente en las denominaciones de `economy.coins`, reutilizando la misma lógica de "dar cambio" que usa `/banco retirar`). Si no le entran en el inventario, el sobrante cae a sus pies.

Todo se puede desactivar por separado (`enabled`, `head-drop`, `trophy-crystal`, `coin-reward.enabled`) en `config.yml`.

> **Sobre el look del cristal:** por defecto se ve como un Cristal del Fin normal (blanco/verde). Para un look más oscuro/rojo (tipo los monumentos de DEDsafio) no hace falta modelar nada — alcanza con poner una textura propia en `resourcepack/assets/minecraft/textures/entity/end_crystal/end_crystal.png` (y `end_crystal_beam.png` para el haz). **Ojo:** esto reemplaza la textura de **todos** los cristales del fin del server (incluidos los reales, si pelean contra el Dragón del End) — es un cambio global del resource pack, no algo que afecte solo a estos trofeos.

### Comida, bebidas y el chef Ottoke (`food`, `shops`, `/chef`, `/comida`)

Portado del módulo `ottoke` del repo [voranixmod](https://github.com/ashbisoficial/voranixmod) (el restaurante de comida coreana "Ottoke Street Food"). Ahí el chef es un aldeano con modelo/renderer propio de Forge — eso **no existe en Paper** (Bukkit no permite reskinear una entidad), así que se usa un `Villager` vanilla con la profesión `BUTCHER` (ya trae puesto un delantal) y un nombre visible como aproximación. Todo lo demás (comida real, hambre/saturación/efectos, precios, comercio) es fiel al original.

- **Comidas** (`food.items`, 5 Banderillas): ítems reales y comestibles, con hambre/saturación/efectos de poción exactos a `ModItems.java`/`OttokeConfig.java` del mod. Cada una se construye sobre un material vanilla comestible (`COOKED_PORKCHOP` para la mayoría; `GOLDEN_APPLE` para la Premium, porque necesita ser "siempre comestible" como el original) + `custom-model-data` para la textura real. Como Paper 1.20.4 no tiene un sistema de nutrición "a medida" por ítem, `FoodConsumeListener` corrige el hambre/saturación/efectos un tick después de comer, sin importar qué valores de nutrición tenga el material base.
- **Bebidas** (`food.drinks`, 5 gaseosas/té): se construyen sobre `Material.POTION` (con `PotionMeta`), para que beberlas sea gratis — la animación de "beber" y que al terminar quede un frasco de cristal son comportamiento nativo de las pociones. Los efectos los aplica Minecraft solo (están en el `PotionMeta`); el listener solo corrige el hambre. **Todavía no las vende ningún chef** (tampoco en el diseño original) — se consiguen con `/comida dar`.
- **Precios**: igual que el mod original, el comercio no cobra en una sola moneda fija ni en una mezcla — elige automáticamente la denominación más conveniente (`CoinEconomy.bestDenominationFor`, redondeando siempre hacia arriba) porque un trade de aldeano de Minecraft solo admite hasta 2 tipos de ítem como costo.
- `/chef spawn <tienda>` — (admin) hace aparecer un aldeano-chef en tu posición, con su comercio ya armado. Por ahora la única tienda es `ottoke`.
- `/chef remove` — (admin) elimina el chef más cercano (8 bloques).
- `/comida dar <jugador> <id> [cantidad]` — (admin) entrega una comida o bebida directamente, sin pasar por el chef.
- Permisos: `voranixextremo.chef.admin`, `voranixextremo.comida.admin`.

**Lo que quedó afuera de este portado** (ver el repo voranixmod si se quiere retomar más adelante): las Banderillas "crudas" y la Freidora (la estación de cocina donde se fríen) — no hacen falta para que el chef las venda listas; el módulo hermano `maipizza` (pizzas, que en el original son bloques con porciones, no ítems simples — pedirían más diseño); bloques personalizados (mostrador, cartel, menú); y los efectos de crecer/encoger (no hay atributo de escala en la versión de Minecraft que usa este plugin, 1.20.4 — recién existe desde 1.20.5+).

### Ruleta de eventos (`/ruleta`)

Al estilo de las ruletas de Desafío/Extremo, pero **para todo el servidor a la vez**: no es un suceso por jugador, es un evento único que cae al azar (ponderado por `weight`) y cambia las reglas del juego por un tiempo — la animación se ve en la pantalla de todos los conectados en el mismo instante. Pensado para que lo dispare producción/staff en un momento puntual del stream, no algo que cualquier jugador pueda tirar (por eso es 100% admin, sin cooldown — lo manejan ustedes a criterio).

**La animación** simula una ruleta de verdad frenando: va mostrando nombres al azar cada vez más espaciados (empieza cambiando varias veces por segundo y se va frenando hasta una pausa final antes de revelar el resultado), con una barra de progreso (boss bar) arriba de la pantalla que se va vaciando en sincro, y un sonido de "clic" en cada cambio y uno distinto al final. Cada suceso tiene una `category` que define su color en el título y en la barra — **rojo (`PELIGRO`)**, **verde (`BUENO`)**, **amarillo (`NEUTRAL`)** — para que de un vistazo se entienda qué tipo de cosa está por tocar.

- `/ruleta` o `/ruleta girar` — gira y aplica un resultado al azar.
- `/ruleta estado` — qué evento está activo y cuánto le queda.
- `/ruleta detener` — corta el evento activo antes de tiempo.
- Permiso: `voranixextremo.ruleta.admin`.

No se puede girar si ya hay un evento activo (hay que esperar a que termine o usar `/ruleta detener`), para no superponer dos a la vez. Los sucesos son 100% configurables en `config.yml` → `world-events.outcomes`, sin tocar código. Vienen 4 de ejemplo:

- **`pvp_libre`** (`PELIGRO`, `PVP_WINDOW`) — fuerza el PVP activado por `duration-seconds` (300 por defecto) y lo devuelve a como estaba al terminar (reutiliza el mismo `PvpManager`, con su sonido de activar/desactivar).
- **`luna_roja`** (`PELIGRO`, `RED_MOON`) — pone de noche y en tormenta en todos los mundos; mientras dura, todo mob que nace sale con la salud y el daño multiplicados (`mob-health-multiplier`, `mob-damage-multiplier`) y al morir tiene una chance extra de soltar ítems de `bonus-drops` (ender pearls, huesos, pólvora, carne podrida — además de su loot normal, no en vez de).
- **`buena_racha`** (`BUENO`, `COMMAND`) — le da Velocidad II a todos los jugadores conectados por 5 minutos (`/effect give @a`).
- **`tormenta`** (`NEUTRAL`, `COMMAND`) — ejemplo del escape genérico: ejecuta cualquier comando de consola al empezar (acá, `/weather rain`) — la forma de agregar eventos nuevos sin tocar código.
- **`momento_revill`** (`PELIGRO`, `REVILL`) — inspirado en el "Momento Revill" de DEDsafio: tormenta + lluvia de meteoritos (`meteor-interval-ticks`, `meteor-yield`, `meteor-break-blocks`) + un objetivo (`objective-description`) con cuenta regresiva en una boss bar propia. Cuando se acaba el tiempo (`duration-seconds`), se evalúa a cada jugador conectado según `objective-type` (`Y_ABOVE`/`Y_BELOW` contra `objective-y`, ej. "bajá a Y=40"): quien no lo cumple queda **eliminado directo** (no pierde 1 vida, va a 0), el resto sobrevive. Ajustá `objective-y` a algo real de tu mapa antes de usarlo en serio.

### Ruleta de colores animada (`/ruletacolor`)

La ruleta "de verdad" al estilo DEDsafio/Ruleta del Fénix: un disco de colores que **gira con desenfoque, acelera, frena, se tiñe del color ganador, tiembla y desaparece** — todo dibujado con una fuente custom (`resourcepack/Ruleta`) superpuesta en el título, así que no hace falta ningún mod ni ventana externa: **se ve igual, al mismo tiempo, en la pantalla de todos los jugadores conectados**, igual que `/ruleta`. El pack se manda solo (por un mini servidor HTTP interno del plugin) y no reemplaza el pack de monedas/comida, se suma encima.

- `/ruletacolor girar <color|random> [mensaje]` — gira y cae en ese color (o al azar), con un mensaje de resultado opcional.
- `/ruletacolor parar` — corta el giro en seco.
- `/ruletacolor pack` — reenvía el resource pack de la ruleta a todos (por si alguien lo rechazó).
- `/ruletacolor reload` — recarga `config.yml` sin reiniciar el servidor.
- Permiso: `voranixextremo.ruletacolor.admin`.

**Cada color puede disparar comandos de consola.** En `config.yml` → `ruleta-colores.colores.<color>.comandos` (lista) se ponen los comandos que se ejecutan **por consola, con permisos completos** (exactamente igual a como `/ruleta` ejecuta su `command`), en el instante en que el disco se frena en ese color — antes de que tiemble y desaparezca. Esto es lo que te permite trigguear cualquier evento (efectos, clima, mobs, lo que sea) directamente desde la ruleta visual, sin depender de nada externo:

```yaml
rojo:
  nombre: "<#ff2b2b><bold>ROJO"
  mensaje: "<red>¡ROJO! El PVP cambia de estado..."
  comandos:
    - "pvp toggle"
```

Vienen los 8 colores (rojo/naranjo/amarillo/verde/celeste/azul/morado/rosa) **sin `mensaje` ni `comandos` por defecto** — a propósito: son el esqueleto, pero qué hace cada color es decisión de producción, no algo fijo en el código.

#### Cómo cargar los eventos del día (sin tocar código, sin recompilar)

Como los efectos/mensajes/comandos de cada color van a cambiar todos los días (distinto evento cada stream), esto se hace 100% editando `config.yml` en el servidor y recargando — nunca hay que tocar Java ni volver a compilar el plugin:

1. En el servidor, abrí `plugins/VoranixExtremo/config.yml` con el editor que uses para administrar el server (el editor de archivos del panel de hosting, FTP/SFTP, o directamente en la consola/terminal del server con `nano`/`vim` si tenés acceso por SSH).
2. Buscá `ruleta-colores:` → `colores:` y completá el/los color(es) del día, por ejemplo:
   ```yaml
   naranjo:
     nombre: "<#ff8a1c><bold>NARANJO"
     mensaje: "<gold>¡NARANJO! Se viene tormenta."
     comandos:
       - "weather thunder"
       - "effect give @a minecraft:slowness 200 0"
   ```
   - `mensaje` es opcional (si lo dejás vacío, usa el genérico de `mensajes.broadcast`).
   - `comandos` es una lista: podés poner uno o varios, cualquier comando de servidor (`effect`, `weather`, `summon`, `give`, comandos del propio plugin como `pvp toggle`, etc.), uno por línea con `-`.
3. Guardá el archivo.
4. Ejecutá `reload` de la ruleta: en la consola del servidor escribís `ruletacolor reload` (sin barra); en el juego, como admin, `/ruletacolor reload`. Recarga `config.yml` al vuelo — **no hace falta reiniciar el servidor**.
5. Probá el color antes del stream con `/ruletacolor girar <color>` para confirmar que el mensaje y los comandos quedaron como querías.

Si un color se queda sin `mensaje`/`comandos` (como vienen todos ahora), `/ruletacolor girar` igual gira y anuncia el nombre del color — simplemente no dispara nada extra ni manda un mensaje especial.

> **Sobre la idea de "Ruleta del Fénix" (web + RCON):** ese proyecto es una página HTML/WebGL que se abre en el navegador (pensada como overlay de stream) y habla con el servidor por un puente Node.js que usa RCON. Funciona, pero agrega una pieza externa que hay que tener corriendo aparte (el puente), un puerto RCON abierto y una contraseña que proteger. Como la idea de fondo — una ruleta vistosa que al caer dispara comandos — **ya está resuelta dentro del plugin** con `/ruletacolor` (misma animación "de ruleta de verdad", comandos por color, visible a todos a la vez, sin servidor aparte ni RCON), no se portó esa arquitectura: sería redundante y menos seguro que lo que ya tenés corriendo en el propio Paper.

### Efectos visuales: cielo y niebla por zonas (`/fx`) — necesita el mod VoranixVisualFX

Minecraft no deja recolorear el cielo de verdad (el degradado por bioma) desde comandos de servidor — eso es render del cliente. Como el server es **híbrido** (Mohist/Arclight/Magma: carga mods Forge y plugins Paper a la vez) y ya hay un instalador que mantiene actualizado un mod Forge en las máquinas de quienes participan, el efecto de cielo vive en un mod chiquito aparte: **[VoranixVisualFX](https://github.com/ashbisoficial/VoranixVisualFX)** (Forge 47.2.0 / MC 1.20.1, mismo toolchain que `voranixmod`). Ese mod **solo** hace efectos visuales — no agrega bloques, items ni mecánicas.

- `/fx cielo <color|#rrggbb> [segundos] [fundido] [colorCenit]` — manda el color a todos los conectados (por defecto 10s, con un fundido de entrada/salida de hasta 2s). Cambia **todo el cielo**, no solo la niebla del horizonte: también lo que se ve mirando derecho para arriba. `colorCenit` es opcional — si no se pone, el cielo queda parejo con el primer color; si se pone uno distinto, queda un degradado (el primer color en el horizonte, `colorCenit` arriba de todo). Usa los mismos 8 nombres de color que `/ruletacolor` (`rojo`, `naranjo`, `amarillo`, `verde`, `celeste`, `azul`, `morado`, `rosa`), o un hex (`#9b30ff`), para cualquiera de los dos colores.
- `/fx parar` — corta el efecto ya mismo en todos.
- Permiso: `voranixextremo.fx.admin`.

**A quien no tenga el mod instalado no le pasa nada** — el plugin le manda el paquete igual, pero Minecraft lo descarta en silencio porque ese cliente nunca registró el canal. No hace falta chequear quién lo tiene puesto.

Como `/fx` es un comando más, se puede meter directo en los `comandos:` de un color de `/ruletacolor` (ver la sección de arriba) para que el cielo cambie solo cuando toque ese color:

```yaml
morado:
  nombre: "<#6a3bff><bold>MORADO"
  comandos:
    - "fx cielo morado 15"              # cielo morado parejo, 15s
    # - "fx cielo morado 15 2 rosa"      # ejemplo con degradado: morado en el horizonte, rosa arriba
```

#### Niebla por zonas (`/fx niebla`)

A diferencia de `/fx cielo` (que es siempre para todo el server), esto es **por ubicación**: se define una zona del mapa, caja (como la selección de WorldEdit — dos esquinas) o esfera (un centro y un radio). No es un muro de "adentro sí, afuera no": a medida que te acercás (por defecto 4 bloques antes del borde, configurable por zona) la niebla se va notando de a poco, cada vez más fuerte, como una bruma de verdad — no aparece de golpe al cruzar una línea exacta. Adentro de la zona, la niebla está a pleno (se ve menos lejos, no solo cambia de color). Pensado para un pantano, una cueva, la entrada a una estructura, una bomba de humo, etc.

**Cómo se calcula (importante):** el plugin NO escanea jugadores ni empuja niebla por red todo el rato — solo le manda al mod la geometría de cada zona (al crearla/borrarla, y a cada jugador cuando se conecta o cambia de mundo) y es el **mod, del lado del cliente, el que calcula en vivo cada frame** qué tan adentro está la cámara de cada zona. Por eso la niebla se nota perfectamente suave al moverse, sin el "salto" de un valor que se actualiza cada medio segundo. **Sin el mod instalado no se ve nada** — ya no hay partículas vanilla de respaldo (se sacaron, ver más abajo).

Para crear una zona **caja**: parado en una esquina (cualquiera de las 8 de la caja que querés), `/fx niebla pos1`; parado en la esquina opuesta, `/fx niebla pos2`; después `/fx niebla crear <nombre> [color] [cercana] [lejana] [margen]`. Si marcás las dos posiciones caminando por el piso (alturas parecidas), la zona igual queda con **al menos 6 bloques de alto** automáticamente (centrados en el punto medio) — así un escalón del terreno no te saca de la zona sin querer.

Para crear una zona **esfera**, sin selección previa: `/fx niebla crearradio <nombre> <radio> [color] [cercana] [lejana] [margen]` — se centra en donde esté parado quien ejecuta el comando.

- `/fx niebla pos1` / `/fx niebla pos2` — marcan las dos esquinas (solo para la caja), en la posición actual del jugador.
- `/fx niebla crear <nombre> [color] [cercana] [lejana] [margen]` — arma la caja con las últimas pos1/pos2 marcadas por ese jugador.
- `/fx niebla crearradio <nombre> <radio> [color] [cercana] [lejana] [margen]` — arma la esfera centrada en el jugador.
- `color` son los mismos 8 nombres de `/ruletacolor` o un hex (por defecto, gris). `cercana`/`lejana` son las distancias en bloques donde arranca y donde ya no se ve nada **adentro de la zona, a pleno** (por defecto 0 y 4 — pensado como humo de granada tipo Valorant/CoD: bien adentro, prácticamente no se ve nada a 4 bloques; antes era 2 y 18, que dejaba ver estructuras a decenas de bloques). `margen` son los bloques de transición ANTES de llegar al borde, por fuera de la zona (por defecto 4 — un número muy alto como 20 da niebla sin que se note ninguna nube de humo).
- `/fx niebla borrar <nombre>` — la borra.
- `/fx niebla lista` — las lista todas, con su tipo (caja/esfera).
- Mismo permiso que `/fx cielo`: `voranixextremo.fx.admin`.

Las zonas quedan guardadas en `plugins/VoranixExtremo/fogzones.yml` y sobreviven un reinicio del servidor.

**Ya no hay partículas vanilla del lado del plugin.** Hasta esta versión, cada zona spameaba `Particle.SMOKE_LARGE`/`Particle.REDSTONE` por todo su volumen — en la práctica se veían como puntitos negros sueltos flotando, no como una nube, así que se sacó (`animarHumo()` ya no existe). Hoy, sin el mod instalado, una zona no se nota en absoluto. Lo visible es 100% del mod: la niebla de distancia (gradual, calculada en vivo) **más una capa de color a pantalla completa** cuando estás adentro de una zona (así tampoco se ve el cielo/sol/nubes de fondo) — ver el README de `VoranixVisualFX` para el detalle. Una partícula de humo de verdad, generada por el mod, queda para la Fase 4.

El protocolo exacto entre el plugin y el mod (qué bytes manda `VisualFxBridge.java` de acá, para cielo y para niebla) está documentado en el README de `VoranixVisualFX` — si alguna vez se cambia alguno de los dos formatos, hay que tocar los dos repos a la vez. **Este sistema de niebla se reescribió de raíz** (antes el servidor escaneaba jugadores cada 0.5s y empujaba el resultado por red; ahora solo sincroniza geometría y el cliente calcula todo en vivo) — probado en el servidor real, la sincronización (Fase 1+2) funciona.

#### Tormenta puntual (`/fx tormenta`) — 100% vanilla, no necesita el mod

Nubes (partículas) y rayos (`World#strikeLightningEffect`, visual + sonido, **sin daño ni fuego, sin tocar el clima real del mundo** — no activa lluvia ni truenos globales) en el punto donde se ejecuta el comando. Pensado para momentos puntuales: matar un boss, conseguir un ítem legendario.

- `/fx tormenta [segundos]` — la dispara ahí mismo donde está parado quien ejecuta el comando (por defecto 15s). La ven todos los que estén cerca, tengan el mod o no.
- Mismo permiso: `voranixextremo.fx.admin`.

Como es vanilla, también sirve para dispararla **desde código**, no solo por comando — `StormEffect.disparar(Location, segundos)` queda expuesto vía `VoranixExtremoPlugin#getStormEffect()`, pensado para engancharlo el día que haya un listener de "boss muerto" o "ítem legendario recogido".

## Estructura del proyecto

```
src/main/java/com/voranixextremo/plugin/
├── VoranixExtremoPlugin.java   # clase principal
├── lives/                      # sistema de vidas + animaciones de muerte
├── playtime/                   # tope de tiempo jugado (barra personal que se vacía)
├── pvp/                        # sistema de PVP
├── economy/                    # monedas físicas, balance, Vault y precios de trades
├── kills/                      # trofeos (cabeza + monedas) al matar en PVP
├── food/                       # comidas y bebidas (hambre/saturación/efectos)
├── shops/                      # aldeanos chef y su comercio
├── worldevents/                # ruleta de eventos (afecta a todo el server: pvp libre, luna roja)
├── ruleta/                     # ruleta de colores animada (fuente custom + comandos por color)
├── visualfx/                   # /fx: cielo y niebla (necesitan el mod) + tormenta puntual (vanilla)
└── util/                       # utilidades (mensajes, resource pack)
src/main/resources/
├── plugin.yml
└── config.yml
```

## Plugins complementarios recomendados

VoranixExtremo no depende de ninguno de estos (el plugin funciona solo), pero se pueden instalar al lado sin pisarse:

- **[Vault](https://www.spigotmc.org/resources/vault.34315/)** — ver [Economía](#economía-dinero-banco-moneda) más arriba: si está instalado, VoranixExtremo se registra solo como su proveedor de economía.
- **[ConditionalEvents](https://modrinth.com/plugin/conditionalevents)** — plugin de Paper/Purpur/Spigot (por Ajneb97, mantenido al día hasta Paper 26.x) que deja armar reglas tipo *"cuando pase el evento X, si se cumple la condición Y, hacé la acción Z"* editando config, sin programar. Útil para que el equipo arme automatizaciones chicas propias (dar un ítem al hacer tal cosa, mensajes custom, etc.) sin tener que pedir código nuevo cada vez. No hace falta ninguna configuración especial de nuestro lado para que convivan.

## Flujo de trabajo con ramas

Para ir sumando mecánicas sin romper lo que ya funciona en el servidor:

- `main` — siempre estable, es lo que corre en el servidor de la serie.
- `feature/<nombre>` — una rama por mecánica nueva (ej. `feature/economia`, `feature/kits`, `feature/fases-evento`). Se mergea a `main` cuando está probada.
- Tags (`v1.0.0`, `v1.1.0`, ...) para marcar las versiones que efectivamente se usaron en grabación/stream.

## Ideas para siguientes iteraciones

Pensando en el estilo Extremo/Desafío/Eufonia (fases, tensión, espectáculo):

- Fases de evento con temporizador y mensajes sincronizados (tipo "Grace Period" / "Deathmatch").
- Kits o ventajas iniciales por jugador/equipo.
- Efectos visuales/sonoros en eliminaciones, inicio de fases o eventos especiales.
- Scoreboard/BossBar mostrando vidas restantes y estado del PVP en tiempo real.
- Integración con WorldBorder para achicar el mapa en fases avanzadas.
