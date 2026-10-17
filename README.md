# VoranixExtremo

Plugin de Minecraft (Paper/Spigot) para una serie estilo **Minecraft Extremo / Desafío**, con el formato de eventos tipo **Eufonia Studio** (fases, eliminación, mecánicas sincronizadas) como referencia a futuro.

## Requisitos

- **Java 25** (Minecraft/Paper lo requieren desde que pasaron a versionar por año, la serie 25.x/26.x)
- Maven 3.9+
- Servidor Paper (o Spigot) **26.3**

## Compilar

```bash
mvn clean package
```

El `.jar` queda en `target/VoranixExtremo-1.0.0.jar`. Cópialo a la carpeta `plugins/` del servidor.

> **Nota:** este proyecto descarga el API de Paper desde `repo.papermc.io`. Si compilas en un entorno con red restringida (como una sesión en la nube sin ese dominio permitido), añade `repo.papermc.io` a los dominios permitidos del entorno o compílalo en tu máquina local.

> **Sobre la versión:** el plugin apunta a Minecraft/Paper **26.3** (`pom.xml` → `paper.version`, rango `[26.3.build,)` para traer siempre el último build de esa versión). Si en algún momento vuelven a 1.20.4 (o cualquier otra), solo hay que cambiar ese rango y el `maven.compiler.release`; el resto del código no depende de la versión. La API usada (`PotionEffectType`, `Attribute`, etc.) está verificada contra la documentación oficial de Paper para 26.3, pero el build real todavía no se corrió en ningún entorno con Java 25 + red a `repo.papermc.io` a la vez — si el primer `mvn package` tira errores de compilación, son esperables y hay que pegarlos para corregirlos puntualmente.

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
- Permisos: `voranixextremo.vidas` (todos) y `voranixextremo.vidas.admin` (operadores).
- Los datos se guardan en `plugins/VoranixExtremo/lives.yml` y persisten entre reinicios.

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
- **Monedas** de botín, por un valor configurable (`pvp-kill-rewards.coin-reward.value`, por defecto 50), van **directo al inventario del asesino** (repartidas automáticamente en las denominaciones de `economy.coins`, reutilizando la misma lógica de "dar cambio" que usa `/banco retirar`). Si no le entran en el inventario, el sobrante cae a sus pies.

Todo se puede desactivar por separado (`enabled`, `head-drop`, `coin-reward.enabled`) en `config.yml`.

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
## Estructura del proyecto

```
src/main/java/com/voranixextremo/plugin/
├── VoranixExtremoPlugin.java   # clase principal
├── lives/                      # sistema de vidas + animaciones de muerte
├── pvp/                        # sistema de PVP
├── economy/                    # monedas físicas, balance, Vault y precios de trades
├── kills/                      # trofeos (cabeza + monedas) al matar en PVP
├── food/                       # comidas y bebidas (hambre/saturación/efectos)
├── shops/                      # aldeanos chef y su comercio
├── worldevents/                # ruleta de eventos (afecta a todo el server: pvp libre, luna roja)
└── util/                       # utilidades (mensajes, resource pack)
src/main/resources/
├── plugin.yml
└── config.yml
```

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
