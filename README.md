# Gestor de Clanes y Raids para MMORPG (Grupo 3)

> ### Actualización v2 (importante para levantar el proyecto)
> 1. **Reinicia el volumen de la base de datos** al desplegar: los scripts SQL solo corren
>    con el volumen vacío.
>    ```bash
>    docker compose down -v && docker compose up --build
>    ```
> 2. **Guarda el archivo `.env` con finales de línea LF** (no CRLF) y sin comillas.
> 3. **Usuario admin:** `admin` / `123456`. Jugadores: `jugador1`..`jugador7` / `123456`.
> 4. La interfaz corre en **modo claro**. El backend se sirve por HTTPS (certificado
>    autofirmado); accede por `http://localhost` (el frontend hace proxy al backend).

Base de Datos Avanzada, 1-2026. Universidad de Santiago de Chile.

## Tecnologías usadas
- PostgreSQL 16
- Backend: Java 21, Spring Boot 3, JdbcTemplate (sin ORM)
- Frontend: Vue 3, Vite, TypeScript
- Seguridad: JWT, BCrypt, HTTPS interno
- Despliegue: Docker Compose

## Cómo levantar el proyecto

**Requisitos previos:**
- Tener Docker y **Docker Compose V2** instalados.
    - **Windows / Mac:** Basta con tener Docker Desktop instalado.
    - **Linux:** Instalar Docker y el plugin de Compose (puedes revisar la guía oficial en https://docs.docker.com/engine/install).

*(Nota: Si al ejecutar los pasos tu terminal no reconoce el comando `docker compose`, significa que tienes una versión antigua. En ese caso, simplemente usa el comando con guion: `docker-compose`).*

**Pasos:**
1. Clonar repositorio y dirigirse a la carpeta:
```bash
git clone https://github.com/DiegoVUsach/Laboratorio1--BDA-2026-1-Grupo3
cd Laboratorio1--BDA-2026-1-Grupo3
```
Una vez en la raiz del proyecto, procedemos a:
2. Levanta los servicios:
```bash
docker compose up --build
```

3. Espera a que la terminal muestre que la base de datos está lista (`database system is ready to accept connections`) y que el backend de Spring Boot haya arrancado (aparecerá el logo por consola).

4. Abre tu navegador y entra a: `http://localhost`

Para detener el sistema normalmente, usa `docker compose down`.

Una vez en la plataforma:
## Usuarios de prueba
Todos los usuarios tienen la contraseña: `123456`
- **admin**: Rol de administrador.
- **jugador1** a **jugador7**: Tienen el rol de usuario normal. Cada uno ya viene con personajes creados en distintos clanes para poder probar las funciones.

## Solución de problemas

- **Error "port is already allocated" o similar en la terminal:**
  Esto pasa si un puerto ya está en uso en tu PC. Abre el archivo `docker-compose.yml` y busca el servicio que falló. Cambia el primer número de la línea de puertos (ejemplo: cambia `"80:80"` a `"8080:80"`, o `"5555:5432"` a `"5556:5432"`). Luego vuelve a levantar con `docker compose up --build`.

- **El sistema se quedó pegado, hay datos corruptos o conflictos con contenedores viejos:**
  Abre la terminal en la carpeta del proyecto y ejecuta una limpieza profunda con:
  `docker compose down -v --remove-orphans`
  Después de eso, vuelve a levantar el proyecto normalmente.

- **Error de SSL o certificado en el navegador:**
  El backend usa HTTPS internamente, pero el contenedor de Nginx hace el proxy automáticamente. No intentes acceder a `https://localhost` o al puerto 8443 directamente, usa siempre `http://localhost`.

## Resumen de la API
Para usar los endpoints (excepto login y registro), necesitas mandar un token JWT en el header de tu petición: `Authorization: Bearer <token>`.

- **/api/auth**: Login y registro.
- **/api/personajes**: Crear, editar, listar personajes y cambiar roles dentro de un clan.
- **/api/clanes**: Crear y administrar clanes. Al transferir el liderazgo, se dispara un trigger de auditoría en la BD.
- **/api/raids**: Ver el calendario semanal, crear raids e inscribirse. El líder puede usar un endpoint de invitación masiva que corre por un Procedimiento Almacenado.
- **/api/items e /inventarios**: Repartir botín (descuenta DKP), ver historiales y equipar items (esto dispara un trigger que recalcula tu Item Level).

## Detalles de la Base de Datos
El proyecto incluye lógica directamente en PostgreSQL:
- **Triggers:** Auditan los cambios de liderazgo de los clanes, validan el Item Level antes de dejarte entrar a una raid, impiden mezclar facciones en un mismo clan y recalculan las estadísticas al equipar un item.
- **Procedimientos Almacenados:** Hay un SP para el reparto de botín (transacción atómica que revisa y descuenta DKP) y otro para hacer inscripciones masivas a las raids manejando errores individuales.
- **Vista Materializada:** Existe una vista `vista_ranking_clan` que arma el ranking de jugadores por asistencia y DKP. Tiene su propio endpoint para refrescarse.
- **Índices:** Creados estratégicamente en columnas muy consultadas, como la clase del personaje y el ID de las raids en las inscripciones, para agilizar el armado de grupos.

---

## Lab 2 — Capa geoespacial (PostGIS) y mundo virtual

### Motor y despliegue
La imagen de base de datos es `postgis/postgis:16-3.4` (PostgreSQL 16 con PostGIS 3.4).
Los scripts se ejecutan automaticamente en este orden al levantar el contenedor:

| Orden | Archivo | Contenido |
|---|---|---|
| 01 | `script_db.sql` | Esquema relacional, triggers, procedimientos, vista de ranking |
| 02 | `script_gis.sql` | Extension PostGIS, columnas geometricas, indices GIST, funciones espaciales |
| 03 | `load_data.sql` | Datos de prueba: 3 clanes, 34 personajes, 20 items, 10 raids |
| 04 | `load_data_gis.sql` | Mundo virtual: regiones, sedes, encuentros y sedes de poder |

**Importante:** para reconstruir la base desde cero hay que borrar el volumen:

```bash
docker compose down -v
docker compose up --build
```

### El mundo virtual "Aethermoor"
Todo el componente espacial ocurre dentro del mundo del juego, modelado como un plano
cartesiano de **300 x 200 metros** (SRID 0). No se usan coordenadas GPS reales: la
"ubicacion" de un jugador es su posicion dentro del mundo, que es lo que tiene sentido
en un MMORPG. El continente se divide en **9 regiones** (poligonos en `region_mapa`) que cubren el mapa
completo en una grilla de 3 x 3: Pantano de Murkmire, Llanuras de Ceniza y Abismo Sombrio
al sur; Bosque de Elderwood, Ciudadela de Aurora y Desierto de Zar'Kuun al centro; Picos
Helados de Kaltharn, Estepas de Hierro e Islas del Alba al norte. Todas las distancias se
expresan en **metros del mundo**; el radio de botin del jefe es de 50 m, una porcion
significativa de una region, de modo que la mecanica de proximidad se aprecia con claridad.
El frontend dibuja este mapa con Leaflet en modo `L.CRS.Simple` (sin tiles ni mapas de calles).

### Columnas espaciales e indices GIST
| Tabla | Columna | Uso |
|---|---|---|
| `clanes` | `sede` | Sede del clan (clanes cercanos, heatmap) |
| `raids` | `ubicacion` | Lugar de la raid en el mundo |
| `raids` | `punto_muerte_boss` | Punto de muerte del jefe (radio de botin) |
| `inscripciones_raid` | `posicion` | Posicion del personaje en el encuentro |
| `personaje` | `ubicacion` | Posicion del jugador en el mundo |
| `auditoria_liderazgo` | `coordenadas` | Sede de Poder (auditoria territorial) |
| `region_mapa` | `geom` | Poligonos de las regiones |

Los 7 indices son GIST (`idx_gist_*`), obligatorios por enunciado para optimizar las
consultas de proximidad (`ST_DWithin`, operador KNN `<->`) y de contencion (`ST_Contains`).

### Las 6 tareas del Grupo 3
1. **Componente espacial** — ubicacion de clanes y de raids (tabla anterior).
2. **Endpoint de proximidad** — `GET /api/geo/clanes/cercanos?x=&y=` devuelve los clanes
   mas cercanos en **GeoJSON**, ordenados con el operador KNN sobre el indice GIST.
3. **Vista materializada** — `vista_heatmap_clanes` agrega por clan el **item level total**
   (poder militar), el promedio, los miembros y los DKP. El mapa de calor usa el item level
   total como peso, porque los DKP son la moneda para canjear items y no miden poder. Se
   expone como GeoJSON en `GET /api/geo/clanes/heatmap`.
4. **Mecanica de proximidad** — `sp_distribuir_botin` reparte el botin **solo** a los
   asistentes confirmados cuya `posicion` esta dentro de 50 metros del
   `punto_muerte_boss` (`ST_DWithin`). Antes de finalizar, el lider ve la lista de quienes
   califican y quienes quedan fuera (`fn_elegibles_botin` / `fn_excluidos_botin`).
5. **Formacion de grupos** — el **tanque lider** es el tanque inscrito con mayor item level
   (`fn_tanque_lider`). `fn_healers_misma_region_que_tanque` determina su region por
   contencion (`ST_Contains`) y lista los healers confirmados de esa misma region. En el
   detalle de la raid esto se ve tambien sobre el mapa del encuentro.
6. **Auditoria territorial** — al transferir el liderazgo, el trigger
   `funcion_auditar_liderazgo` guarda las coordenadas del acto en `auditoria_liderazgo`;
   `fn_sedes_poder_geojson` arma el mapa historico de "Sedes de Poder" del clan.

### API geoespacial
Todos los endpoints de mapa devuelven **GeoJSON** (`FeatureCollection`) y todos reciben
coordenadas `x`/`y` del mundo virtual.

| Metodo | Endpoint | Descripcion |
|---|---|---|
| GET | `/api/geo/regiones` | Regiones del mundo (poligonos) |
| GET | `/api/geo/clanes/cercanos?x=&y=&limit=` | Clanes mas cercanos a una posicion |
| GET | `/api/geo/clanes/cercanos-de/{idPersonaje}` | Clanes cercanos al personaje |
| GET | `/api/geo/clanes/heatmap` | Mapa de calor de clanes (vista materializada) |
| POST | `/api/geo/clanes/heatmap/refrescar` | Refresca la vista materializada |
| GET | `/api/geo/clanes/{id}/sedes-poder` | Mapa historico de Sedes de Poder |
| GET | `/api/geo/raids/mapa?idClan=` | Raids ubicadas en el mundo |
| GET | `/api/geo/raids/{id}/encuentro` | Posiciones de los asistentes, distancia al jefe y marca del tanque lider |
| GET | `/api/geo/raids/{id}/boss` | Punto de muerte del jefe |
| GET | `/api/geo/raids/{id}/tanque-lider` | Tanque inscrito con mayor item level |
| GET | `/api/geo/personajes/{id}/ubicacion` | Posicion del personaje en el mundo |
| GET | `/api/geo/raids/{id}/elegibles-botin` | Asistentes dentro del radio de 50u |
| GET | `/api/geo/raids/{id}/excluidos-botin` | Asistentes fuera del radio |
| GET | `/api/geo/raids/{id}/healers-region` | Healers en la region del tanque lider |
| PUT | `/api/geo/clanes/{id}/ubicacion` | `{ x, y }` sede del clan |
| PUT | `/api/geo/personajes/{id}/ubicacion` | `{ x, y }` posicion del jugador |
| PUT | `/api/geo/raids/{id}/geo` | `{ x, y, bossX, bossY }` |
| PUT | `/api/geo/raids/{idRaid}/posicion/{idPersonaje}` | `{ x, y }` posicion en el encuentro |

Ejemplo de respuesta (`/api/geo/clanes/cercanos?x=600&y=400&limit=2`):

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": { "type": "Point", "coordinates": [600, 400] },
      "properties": {
        "id_clan": 1,
        "nombre_clan": "Los Heraldos de Aurora",
        "distancia": 0.0,
        "region": "Ciudadela de Aurora"
      }
    }
  ]
}
```

### Frontend
Interfaz Vue 3 + Vite organizada en cuatro pestañas, de lo privado a lo global:
- **Mi Personaje** — ficha, equipamiento (equipar y desequipar, que recalcula el item
  level), bolsa, botin por canjear e historial.
- **Mi Clan** — miembros, asignacion de roles, transferencia de liderazgo y el mapa
  historico de **Sedes de Poder**.
- **Raids** — solo las raids del clan, con el mapa del mundo mostrando cada raid, su radio
  de botin y la posicion del jugador; filtro pendientes/completadas; confirmacion de
  invitaciones desde la propia lista; detalle del encuentro con el radio del jefe, los
  asistentes confirmados, el tanque lider y los healers de su region; y finalizacion con
  la vista previa de quienes reciben botin.
- **Mundo** — mapa de poder de los clanes (el propio destacado y la posicion del jugador),
  ranking de jugadores con filtros (mi clan / mi faccion / global), ranking de clanes por
  item level y consulta de clanes cercanos.

Las notificaciones estan siempre disponibles desde la barra lateral, en cualquier pestaña.
La posicion del personaje en el mundo se define al crearlo y es la que se usa al inscribirse
en una raid. Todas las fechas se muestran en formato chileno (dd/mm/aaaa). Todos los mapas
se dibujan sobre el mundo virtual con Leaflet (`L.CRS.Simple`), sin mapas de calles ni
servicios externos de tiles.

### Datos de prueba
3 clanes con plantilla completa y 34 personajes (incluye 2 sin clan para probar el
alta en clan), 20 items, 10 raids (3 de ellas encuentros espaciales con jefe, una por
clan) y 9 registros de traspaso de mando para el mapa de Sedes de Poder.

**Credenciales:** todos los usuarios usan la contraseña `123456`.
`admin` (rol ADMIN, controla a *Arthon*, Guild Master del clan 1) y
`jugador1` ... `jugador33` (rol USER).


---

## Guia de demostracion

Todos los usuarios tienen la contraseña **`123456`**. El usuario `admin` controla a
*Arthon*, Guild Master del clan principal.

### Reparto de roles en el poblado

| Clan | Guild Master (usuario) | Raiders | Members (sin acceso a raids) |
|---|---|---|---|
| Los Heraldos de Aurora | *Arthon* — `admin` | Elyndra, Kaelen, Sylara, Bran, Mira, Theron, Isolde, Nessa, Lira | *Dorn* (`jugador6`), *Gareth* (`jugador10`) |
| Sombras del Ocaso | *Vorlok* — `jugador12` | Nyx, Draven, Selene, Korrin, Ashka, Petra, Osric | *Ryn* (`jugador18`), *Halvar* (`jugador19`) |
| Culto del Abismo | *Malketh* — `jugador22` | Zaira, Grimm, Velka, Torvin, Sable, Kessa, Rurik, Nyra | *Umbra* (`jugador28`) |

Ademas hay dos personajes **sin clan** para probar el ingreso a un clan:
*Aeliana* (`jugador32`) y *Kodrun* (`jugador33`).

### Que revisar con cada usuario

**1. Control de acceso por rol (RBAC).**
Entra como `jugador6` (*Dorn*, Member): **no aparece la pestaña Raids**. Entra como
`jugador4` (*Bran*, Raider) y si aparece. En la base, un Member tampoco puede ser
inscrito: el trigger `trg_validar_rol_raider` lo rechaza.

**2. El Guild Master no puede degradar a un Raider con raids pendientes.**
Con `admin` (*Arthon*), en **Mi Clan → Asignar rol**, intenta pasar a *Elyndra* de Raider
a Member: el trigger `trg_bloquear_degradar_raider` lo impide y explica cuantas raids
pendientes tiene.

**3. Invitacion masiva y notificaciones.**
Con `admin`, en **Raids → Cavernas de Murkmire → Invitar raiders**: la respuesta indica
por nombre a quienes se invito, y cada invitado recibe una notificacion.
Entra despues como `jugador4` (*Bran*): vera la notificacion y, en la tarjeta de la raid,
el aviso "Estas invitado... Confirmar asistencia" sin necesidad de abrir el detalle.
Invitaciones ya cargadas en el poblado: *Bran*, *Isolde* y *Mira* en Fortaleza de
Elderwood; *Nessa* en Cavernas de Murkmire; *Nyx* y *Petra* en Guarida Sombria;
*Torvin*, *Sable* y *Nyra* en Ritual del Vacio.

**4. Mecanica de proximidad (radio de 50 m).**
Con `admin`, abre **Raids → Caida del Rey Liche → Ver detalle**: el mapa muestra el jefe
con su radio, los asistentes confirmados (verde dentro, rojo fuera) y el tanque lider en
morado. Al pulsar **Finalizar** aparece la lista de quienes reciben botin y quienes no.

| Encuentro | Clan | Tanque lider | Reciben botin | Quedan fuera |
|---|---|---|---|---|
| Caida del Rey Liche | Heraldos de Aurora | *Arthon* (iLvl 845) | 5 | 3 (*Isolde*, *Sylara*, *Mira*) |
| El Devorador de Almas | Sombras del Ocaso | *Draven* (iLvl 820) | 4 | 3 (*Nyx*, *Petra*, *Ashka*) |
| Heraldo del Vacio | Culto del Abismo | *Rurik* (iLvl 830) | 5 | 3 (*Torvin*, *Sable*, *Nyra*) |

**5. Formacion de grupos (healers en la region del tanque lider).**
En el mismo detalle, mas abajo: en *Caida del Rey Liche* el tanque lider *Arthon* esta en
Bosque de Elderwood y los healers de su region son *Elyndra* y *Nessa* (*Mira* queda
descartada porque esta en Llanuras de Ceniza). En *Heraldo del Vacio* son *Velka* y
*Kessa*, y en *El Devorador de Almas*, *Selene*.

**6. Botin, DKP e item level.**
Entra como `jugador14` (*Draven*) o `jugador24` (*Grimm*): ambos tienen 2 items pendientes
en **Mi Personaje → Mi botin por canjear**. Al canjear se descuentan los DKP en el acto.
En la misma pestaña, equipa o desequipa un item de la bolsa y observa como cambia el
item level del personaje.

**7. Mapa de poder (vista materializada).**
En **Mundo** se dibuja el mapa de calor por item level total del clan. Con el poblado
inicial: Los Heraldos de Aurora 8.595 (Ciudadela de Aurora), Culto del Abismo 7.410
(Abismo Sombrio) y Sombras del Ocaso 7.355 (Pantano de Murkmire). Tu clan aparece
destacado en morado y tu personaje en azul. El boton **Refrescar vistas materializadas**
recalcula el ranking y el mapa.

**8. Clanes cercanos (consulta de proximidad).**
Tambien en **Mundo**, el panel "Clanes mas cercanos a ti" ordena los clanes por distancia
en metros desde la posicion de tu personaje, usando el indice GIST.

**9. Auditoria territorial (Sedes de Poder).**
Con `admin`, en **Mi Clan → Sedes de Poder**: los tres traspasos historicos del clan sobre
el mapa, con lider entrante, lider saliente, region, coordenadas y fecha. Para generar uno
nuevo, usa **Transferir liderazgo** y marca en el mapa el lugar de la ceremonia.

**10. Fundar o unirse a un clan.**
Entra como `jugador32` (*Aeliana*, sin clan): en **Mi Clan** puede fundar un clan eligiendo
la sede sobre el mapa, o unirse a uno de su misma faccion (solo se muestran esos).
