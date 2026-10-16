# VoranixExtremo

Plugin de Minecraft (Paper/Spigot) para una serie estilo **Minecraft Extremo / Desafío**, con el formato de eventos tipo **Eufonia Studio** (fases, eliminación, mecánicas sincronizadas) como referencia a futuro.

## Requisitos

- Java 21
- Maven 3.9+
- Servidor Paper (o Spigot) 1.20.4

## Compilar

```bash
mvn clean package
```

El `.jar` queda en `target/VoranixExtremo-1.0.0.jar`. Cópialo a la carpeta `plugins/` del servidor.

> **Nota:** este proyecto descarga el API de Paper desde `repo.papermc.io`. Si compilas en un entorno con red restringida (como una sesión en la nube sin ese dominio permitido), añade `repo.papermc.io` a los dominios permitidos del entorno o compílalo en tu máquina local.

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
    ├── minecraft/models/item/iron_nugget.json   # overrides por custom-model-data
    └── voranixextremo/
        ├── models/item/coin_*.json              # un modelo por moneda
        └── textures/item/coin_*.png             # las texturas originales
```

Para usarlo en el servidor:

1. Empaquetarlo en un `.zip` (el contenido de `pack.mcmeta` y `assets/` tiene que quedar en la RAÍZ del zip, no dentro de una subcarpeta):
   ```bash
   cd resourcepack && zip -r ../VoranixExtremo-resourcepack.zip . && cd ..
   ```
2. Subir ese `.zip` a algún lugar con URL directa de descarga (ej. un [Release de GitHub](https://docs.github.com/es/repositories/releasing-projects-on-github)).
3. Pegar esa URL en `config.yml` → `resourcepack.url`. Al conectarse, cada jugador recibe automáticamente la oferta de instalar el resource pack (no es obligatorio aceptarla, pero si no la aceptan ven las monedas como un lingote de hierro genérico en vez de la textura real).

### Trofeos de asesinato PVP (`pvp-kill-rewards`)

Cuando un jugador mata a otro **en combate** (se detecta con `getKiller()`, que Bukkit solo pone si el último golpe fue de un jugador — no aplica a caídas, lava, mobs, etc.), además de la animación de muerte normal caen al piso en el lugar de la muerte:

- La **cabeza de la víctima**, con su skin real (`pvp-kill-rewards.head-drop`).
- **Monedas físicas** de botín, por un valor configurable (`pvp-kill-rewards.coin-reward.value`, por defecto 50), repartidas automáticamente en las denominaciones de `economy.coins` — reutiliza la misma lógica de "dar cambio" que usa `/banco retirar`.

Caen al suelo (no van directo al inventario del asesino), así que es botín que cualquiera puede levantar — útil para el estilo de riesgo/recompensa de la serie. Todo se puede desactivar por separado (`enabled`, `head-drop`, `coin-reward.enabled`) en `config.yml`.

## Estructura del proyecto

```
src/main/java/com/voranixextremo/plugin/
├── VoranixExtremoPlugin.java   # clase principal
├── lives/                      # sistema de vidas + animaciones de muerte
├── pvp/                        # sistema de PVP
├── economy/                    # monedas físicas, balance y Vault
├── kills/                      # trofeos (cabeza + monedas) al matar en PVP
└── util/                       # utilidades (mensajes)
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
