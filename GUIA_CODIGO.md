# Guia tecnica del codigo — Laboratorio 2

Grupo 3 · Gestor de Clanes y Raids para MMORPG · Taller de Base de Datos 1-2026

Este documento explica **como esta organizado el proyecto** y **donde encontrar cada
funcionalidad**, con enfasis en la novedad del Laboratorio 2: la capa geoespacial.
Las lineas indicadas corresponden a la version entregada; si el archivo se edita,
buscar por el nombre de la funcion o del componente.

---

## 1. Arquitectura del proyecto

Arquitectura de tres capas desplegada con Docker Compose:

```
Navegador
   |
   |  HTTP + JSON (JWT en la cabecera Authorization)
   v
Frontend  Vue 3 + Vite + Leaflet          (contenedor "frontend", Nginx)
   |
   |  REST /api/...
   v
Backend   Spring Boot + JDBC puro         (contenedor "backend")
   |      (sin ORM, por exigencia del enunciado)
   |  SQL / CALL de procedimientos
   v
Base de datos  PostgreSQL 16 + PostGIS 3.4  (contenedor "db")
```

**Decision de diseño central:** la logica de negocio vive en la base de datos
(procedimientos almacenados, triggers, vistas materializadas y funciones PostGIS).
El backend es una capa delgada que autentica, autoriza por rol y traduce a HTTP;
el frontend solo consume y dibuja. Esto es lo que pide el enunciado y ademas evita
que las reglas se puedan saltar llamando a la API desde otro cliente.

### Estructura de carpetas

```
/
├── docker-compose.yml         Orquestacion: db (postgis), backend, frontend
├── script_db.sql              01 · Esquema, triggers, procedimientos, vista de ranking
├── script_gis.sql             02 · CAPA GEOESPACIAL (lo nuevo del Lab 2)
├── load_data.sql              03 · Datos de prueba (clanes, personajes, items, raids)
├── load_data_gis.sql          04 · Mundo virtual, posiciones y sedes de poder
├── README.md                  Manual de instalacion, API y guia de demostracion
├── backend/
│   └── src/main/java/usach/cl/laboratorio1/
│       ├── controller/        Endpoints REST
│       ├── repository/        Acceso a datos con JdbcTemplate
│       ├── service/           Reglas que necesitan varias consultas
│       ├── security/          JWT y filtros de autenticacion
│       ├── tablas/            Entidades planas
│       ├── dto/               Objetos de respuesta
│       └── util/ErrorUtil     Traduce errores de PostgreSQL a mensajes legibles
└── frontend/src/
    ├── App.vue                Layout, navegacion por pestañas y notificaciones
    ├── components/WorldMap.vue  Mapa del mundo virtual (reutilizable)
    ├── views/                 Una vista por pestaña
    ├── services/api.ts        Cliente HTTP y todos los servicios
    ├── utils/format.ts        Fechas en formato chileno
    └── types/world.ts         Tipos del mapa
```

### Orden de arranque de la base

`docker-compose.yml` monta los cuatro scripts numerados en
`/docker-entrypoint-initdb.d`, de modo que PostgreSQL los ejecuta en orden al crear
el volumen. Por eso, para reconstruir hay que borrarlo:

```bash
docker compose down -v && docker compose up --build
```

---

## 2. La capa geoespacial (novedad del Lab 2)

Todo el componente espacial ocurre en un **mundo virtual** llamado *Aethermoor*: un
plano cartesiano de **300 x 200 metros** en `SRID 0`. No se usan coordenadas GPS
reales porque en un MMORPG la ubicacion relevante es la del personaje dentro del
juego. El continente se divide en 9 regiones que cubren todo el mapa.

Archivo principal: **`script_gis.sql`**.

### 2.1 Componente espacial: donde se guarda cada geometria

`script_gis.sql`, lineas 17-22 (`ALTER TABLE ... ADD COLUMN`):

| Tabla | Columna | Que representa |
|---|---|---|
| `clanes` | `sede` | Sede del clan en el mundo |
| `raids` | `ubicacion` | Lugar donde ocurre la raid |
| `raids` | `punto_muerte_boss` | Punto de muerte del jefe (centro del radio de botin) |
| `inscripciones_raid` | `posicion` | Posicion del personaje en ese encuentro |
| `personaje` | `ubicacion` | Posicion actual del personaje en el mundo |
| `auditoria_liderazgo` | `coordenadas` | Lugar del traspaso de mando (Sede de Poder) |
| `region_mapa` | `geom` | Poligono de cada region (tabla nueva, linea 25) |

### 2.2 Indices GIST (obligatorios por enunciado)

`script_gis.sql`, lineas 35-41. Son siete, uno por columna espacial. Sirven para
las consultas de proximidad (`ST_DWithin`, operador KNN `<->`) y de contencion
(`ST_Contains`).

### 2.3 Las 6 tareas del Grupo 3

| # | Tarea | Donde esta |
|---|---|---|
| 1 | Componente espacial (clanes y raids) | `script_gis.sql` L17-22 |
| 2 | Endpoint que recibe una ubicacion y devuelve los clanes mas cercanos | `fn_clanes_cercanos` — `script_gis.sql` L49 |
| 3 | Vista materializada: mapa de calor de clanes | `vista_heatmap_clanes` L82 · `fn_heatmap_clanes_geojson` L105 |
| 4 | Procedimiento que reparte el botin solo dentro de 50 m del jefe | `sp_distribuir_botin` — `script_gis.sql` L229 |
| 5 | Healers en la misma region que el tanque lider | `fn_healers_misma_region_que_tanque` L260 · `fn_tanque_lider` L186 |
| 6 | Auditoria territorial: mapa historico de Sedes de Poder | `funcion_auditar_liderazgo` L287 · `fn_sedes_poder_geojson` L298 |

**Detalle de la tarea 4 (proximidad).** `sp_distribuir_botin` sobrescribe la version
del Lab 1: recorre a los asistentes confirmados y, si la raid tiene
`punto_muerte_boss`, exige `ST_DWithin(posicion, punto_muerte_boss, 50)` antes de
entregar el item. Si la raid no tiene jefe, reparte a todos los confirmados. Las
funciones `fn_elegibles_botin` (L151) y `fn_excluidos_botin` (L167) son las que
alimentan la vista previa que ve el Guild Master antes de finalizar.

**Detalle de la tarea 5 (formacion de grupos).** El **tanque lider** se define como
el tanque inscrito con **mayor item level** (`fn_tanque_lider`). Su region se obtiene
por contencion de su posicion en un poligono de `region_mapa`, y luego se listan los
healers confirmados que caen en esa misma region.

### 2.4 Funciones auxiliares que alimentan los mapas

| Funcion | Linea | Devuelve |
|---|---|---|
| `fn_regiones_geojson` | 130 | Los 9 poligonos con nombre y tipo de terreno |
| `fn_encuentro_geojson` | 197 | Posiciones de los asistentes, distancia al jefe, si estan en rango y quien es el tanque lider |
| `fn_boss_geojson` | 326 | Punto de muerte del jefe, para dibujar el radio |

Todas devuelven `jsonb` con estructura **GeoJSON** (`FeatureCollection` o `Feature`),
que es el requisito transversal del enunciado: la API entrega formatos geoespaciales
listos para consumir.

---

## 3. Logica de negocio en la base (`script_db.sql`)

### Procedimientos almacenados

| Objeto | Linea | Que hace |
|---|---|---|
| `sp_fundar_clan`, `sp_unirse_clan`, `sp_salir_clan`, `sp_disolver_clan` | ~408-500 | Ciclo de vida del clan |
| `sp_canjear_item` | 531 | Canje del botin: valida propiedad y DKP, descuenta, deposita en el inventario, registra historial y notifica |
| `fn_invitar_raiders` / `sp_invitacion_masiva_raiders` | 630 / 679 | Invitacion masiva. La funcion **devuelve a quienes invito**, para que la API lo informe por nombre |
| `sp_distribuir_botin` | 660 | Version base (sin geometria); `script_gis.sql` la sobrescribe con el filtro de 50 m |
| `sp_finalizar_raid` | 683 | Cierra la raid, reparte botin y refresca el ranking |

### Triggers de integridad

| Trigger | Linea | Regla |
|---|---|---|
| `trg_validar_item_level` | ~208 | No inscribirse sin el item level minimo |
| `trg_validar_raid_mismo_clan` | ~248 | No inscribirse en raids de otro clan |
| `trg_bloquear_raid_cerrada` | ~271 | Una raid completada queda congelada |
| `trg_recalcular_item_level` | ~318 | El item level es la suma de los 3 slots equipados |
| `trg_equipado_en_bolsa` | 299 | Lo que se equipa queda registrado en la bolsa (evita que un item desaparezca al desequiparlo) |
| `trg_validar_faccion_clan` | ~346 | Un personaje solo entra a clanes de su faccion |
| `trg_validar_rol_raider` | 380 | Solo Raiders y Guild Master participan en raids |
| `trg_bloquear_degradar_raider` | 412 | No se puede quitar el rol de Raider a alguien con raids sin completar |
| `trg_auditoria_liderazgo` | ~200 | Registra el traspaso de mando **con sus coordenadas** |
| `trg_reset_roles_al_disolver` | ~430 | Libera a los miembros al disolver el clan |

---

## 4. Backend (Spring Boot, JDBC puro)

### Capa geoespacial

- **`controller/GeoController.java`** — los 19 endpoints `/api/geo/...`.
  Los que devuelven mapas responden con `Content-Type: application/json` y el
  GeoJSON tal cual llega de PostGIS (metodo privado `json(...)`).

  | Linea | Endpoint |
  |---|---|
  | 34 | `GET /regiones` |
  | 40 | `GET /clanes/cercanos?x=&y=&limit=` |
  | 48 | `GET /clanes/cercanos-de/{idPersonaje}` |
  | 55 | `GET /clanes/heatmap` |
  | 60 | `POST /clanes/heatmap/refrescar` |
  | 67 | `GET /clanes/{id}/sedes-poder` |
  | 73 | `GET /raids/mapa?idClan=` |
  | 79 | `GET /raids/{id}/encuentro` |
  | 85 | `GET /personajes/{id}/ubicacion` |
  | 91 | `GET /raids/{id}/boss` |
  | 99 | `GET /raids/{id}/elegibles-botin` |
  | 105 | `GET /raids/{id}/excluidos-botin` |
  | 111 | `GET /raids/{id}/tanque-lider` |
  | 117 | `GET /raids/{id}/healers-region` |
  | 124-152 | `PUT` para fijar sede del clan, posicion del personaje, geometria de la raid y posicion en el encuentro |

- **`repository/GeoRepository.java`** — todas las consultas PostGIS. Es el unico
  lugar del backend donde aparece SQL espacial (`ST_MakePoint`, `ST_SetSRID`,
  llamadas a `fn_*`).

### Resto del backend

| Archivo | Rol |
|---|---|
| `controller/RaidController.java` | Calendario **filtrado por clan**, crear, finalizar, invitar raiders (devuelve los nombres invitados) |
| `controller/InscripcionController.java` | Inscribirse, `/mias` (para confirmar desde la lista) y confirmar asistencia (solo el propio jugador) |
| `controller/ItemController.java` | `/items/mi-pool/{id}` y `/items/canjear` |
| `controller/InventarioController.java` | `PUT /inventarios/id/{id}` — equipar y desequipar |
| `controller/NotificacionController.java` | Bandeja, contador de no leidas y marcar leidas |
| `service/InscripcionService.java` | Valida que el personaje pertenezca al usuario del token |
| `security/` | Filtro JWT y reglas de acceso por rol (RBAC) |
| `util/ErrorUtil.java` | Baja hasta la causa real de la excepcion para mostrar el mensaje del trigger en vez de `PreparedStatementCallback` |

---

## 5. Frontend (Vue 3 + Leaflet)

### El mapa del mundo: `components/WorldMap.vue`

Componente reutilizable, usado por todas las vistas. Puntos clave:

- **`L.CRS.Simple`** (funcion `init`): Leaflet trabaja como plano cartesiano, sin
  tiles ni mapas de calles. El mundo se acota con `L.latLngBounds(0,0 - 200,300)`.
  Ojo con el orden de coordenadas: en Leaflet es `latLng(y, x)`, mientras que
  GeoJSON entrega `[x, y]`.
- **Regiones** (`dibujarRegiones`): pinta el GeoJSON de `fn_regiones_geojson` con un
  color por tipo de terreno y una etiqueta con el nombre.
- **Paneles de dibujo** (`map.createPane`): `paneRegiones` (350), `paneEtiquetas`
  (380) y `paneCirculos` (450). Como las regiones se cargan de forma asincrona,
  sin paneles terminaban tapando los circulos de radio.
- **Marcadores** (`iconoDe`): iconos SVG generados con centrado calculado
  (`off = (S - 24*k)/2`) para que el glifo quede alineado con el circulo.
- **Capa de calor** (`dibujarHeat`): usa `leaflet.heat` con un degradado azul →
  verde → amarillo → naranja → rojo.
- **Modo seleccion** (`pickable`): emite `pick` con las coordenadas del clic; asi se
  eligen la ubicacion del personaje, la sede del clan, el lugar de una raid y el
  punto de la ceremonia de traspaso.

### Vistas

| Vista | Que contiene | Funcionalidad de Lab 2 |
|---|---|---|
| `views/PersonajeView.vue` | Ficha, equipamiento, bolsa, botin, historial | Canje que descuenta DKP y equipamiento que recalcula el item level |
| `views/ClanesView.vue` | Clan, miembros, roles, liderazgo | **Sedes de Poder**: mapa historico + tabla con fechas (`abrirSedesPoder`, `markersSedes`) |
| `views/RaidsView.vue` | Raids del clan | Mapa de raids con radio de botin y posicion del jugador (`markersMundo`); detalle del encuentro (`markersEncuentro`); vista previa de proximidad al finalizar (`abrirFinalizar`); healers de la region del tanque lider |
| `views/StatsView.vue` | Pestaña Mundo | **Mapa de calor** por item level (`intensidad`, `radioDe`, `heatPoints`), ranking con filtros y clanes cercanos |
| `views/CharacterSelection.vue` | Eleccion y creacion de personaje | Se elige la posicion inicial en el mundo sobre el mapa |
| `App.vue` | Layout y navegacion | Notificaciones globales y **RBAC de interfaz**: `puedeVerRaids` oculta la pestaña Raids a los Member |

### Escalado del mapa de calor

En `StatsView.vue`, funcion `intensidad()`: se normaliza el poder de cada clan
entre el minimo y el maximo observados, con un piso de 0.15 para que el clan mas
debil siga siendo visible. El radio se calcula con `radioDe()` y queda **acotado
entre 18 y 60 metros**, de modo que ningun clan crezca sin limite aunque su item
level se dispare. El color y la opacidad tambien escalan con la misma intensidad.

### Otros archivos

- **`services/api.ts`** — cliente HTTP (agrega el JWT) y todos los servicios.
  `geoService` agrupa la API geoespacial completa.
- **`utils/format.ts`** — `fechaCL` y `fechaHoraCL` muestran todo en dd/mm/aaaa;
  `clAIso` convierte lo que escribe el usuario al formato que espera la API.
- **`types/world.ts`** — el tipo `WorldMarker` que consumen las vistas y el mapa.

---

## 6. Recorrido rapido para la defensa

1. **Componente espacial e indices** → `script_gis.sql` L17-41.
2. **Clanes cercanos en GeoJSON** → `fn_clanes_cercanos` (L49) y el panel de la
   pestaña Mundo.
3. **Vista materializada / mapa de calor** → `vista_heatmap_clanes` (L82) y el mapa
   de la pestaña Mundo, con el boton de refrescar.
4. **Proximidad de 50 m** → `sp_distribuir_botin` (L229) y el modal de Finalizar en
   la pestaña Raids, que muestra quienes reciben botin y quienes no.
5. **Healers por region** → `fn_healers_misma_region_que_tanque` (L260) y el detalle
   de la raid, donde ademas se ve sobre el mapa.
6. **Auditoria territorial** → `funcion_auditar_liderazgo` (L287) y el boton Sedes
   de Poder en la pestaña Mi Clan.

La guia con los usuarios concretos para probar cada punto esta al final del
`README.md`, en la seccion **Guia de demostracion**.
