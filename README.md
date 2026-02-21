# DrakesWorlds

Plugin de generacion avanzada para Paper 1.20.6 orientado a DrakesCraft.

## Vision
- Menos planicies.
- Mas bosques densos y taiga/pinos.
- Montanas mas agresivas con zonas nevadas.
- Pantanos y manglares mas frecuentes.
- Claros naturales dentro de bosques para evitar terreno monotono.
- Base para evolucionar a `DrakesBiomes` + `DrakesWoods` como subsistemas.

## Estado actual (MVP funcional)
- `Terrain Engine` custom con relieve fuerte (valles, montanas, mesetas locales).
- `Biome Painter` custom con sesgo hacia:
  - `TAIGA`, `OLD_GROWTH_PINE_TAIGA`, `GROVE`, `SNOWY_SLOPES`, `JAGGED_PEAKS`
  - `SWAMP`, `MANGROVE_SWAMP`
  - `CHERRY_GROVE`
  - `PLAINS` reducido al minimo
- `Flora Engine` inicial:
  - pinos custom mas altos
  - arboles mixtos por bioma
  - arboles muertos
  - troncos caidos
  - sotobosque/bushes
- Comando admin `/drakesworlds`.
- Config central en `worlds.yml` con comentarios educativos.

## Comandos
- `/drakesworlds create <world_name> [profile] [seed]`
- `/drakesworlds listprofiles`
- `/drakesworlds worldinfo <world>`
- `/drakesworlds reload`

Permiso:
- `drakesworlds.admin` (default: op)

## Configuracion
Archivo: `src/main/resources/worlds.yml`

Contiene:
- `default-profile`
- `auto-create-on-startup`
- `startup-worlds`
- `profiles`
  - `terrain`
  - `biome-weights`
  - `decoration`

## Notas importantes
- Cambios de generacion solo afectan chunks nuevos.
- Si el mundo ya existe, no se reescribe automaticamente.
- Para ver cambios grandes:
  1. Crear un mundo nuevo con otro nombre, o
  2. Regenerar/purgar regiones antiguas.

## Build
```powershell
cd Plugins\DrakesWorlds
mvn clean package
```

Jar esperado:
- `target/DrakesWorlds-1.0-SNAPSHOT.jar`

