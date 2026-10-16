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

## Estructura del proyecto

```
src/main/java/com/voranixextremo/plugin/
├── VoranixExtremoPlugin.java   # clase principal
├── lives/                      # sistema de vidas
├── pvp/                        # sistema de PVP
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
