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


## Modelado de datos: embedding vs referencing

El enunciado exige justificar la decision para cada coleccion. El criterio es el patron
de acceso: se **embebe** lo que siempre se lee junto al documento padre y tiene tamano
acotado, y se **referencia** lo que crece sin limite o se consulta por si mismo.

| Coleccion | Decision | Justificacion |
|---|---|---|
| `personajes` | **Referenciada** | Decision central de la Tarea 1. Cada personaje participa en **multiples raids de forma independiente**, y las raids lo referencian por su id. Embebido dentro del jugador habria que duplicarlo o hacer consultas anidadas costosas; ademas su itemLevel y DKP cambian seguido y deben vivir en un solo lugar. |
| `personajes.inventario` | **Embebido** | Relacion 1:1, siempre se lee con el personaje y su tamano es acotado (tres slots y la bolsa). |
| `raids.inscripciones` | **Embebido** | Solo tienen sentido dentro de su raid y su cantidad esta acotada por el grupo. Permite leer la raid completa en una consulta y es lo que habilita el `$unwind` del pipeline de ranking. |
| `clanes.auditoriaLiderazgo` | **Embebido** | Historial corto que se consulta siempre con el clan. |
| `loot_pool` | **Referenciada** | Crece de forma independiente y se consulta por personaje; necesita validador e indice unico propios. |
| `items` | **Referenciada** | Catalogo compartido: muchos personajes apuntan al mismo item. |
| `historial_botin` | **Referenciada** | Crece sin limite; embebido haria crecer el documento del personaje indefinidamente. |
| `notificaciones` | **Referenciada** | Alto volumen y expiracion automatica por TTL. |
| `clan_rankings` / `clanes_rankeados` | **Materializadas** | Salida de los Aggregation Pipelines escrita con `$merge`. |

## Estrategia de indices

| Indice | Tipo | Para que |
|---|---|---|
| `personajes {idClan, clase, rolClan}` | Compuesto | Filtrar personajes disponibles por clase y rol dentro de un clan (Tarea 5). |
| `personajes {nombrePersonaje}` | Unico | Impide nombres de personaje repetidos (Tarea 5). |
| `loot_pool {idRaid, idItem}` | Unico compuesto | Impide que un mismo item quede asignado dos veces en la misma raid ante solicitudes concurrentes. |
| `items {nombreItem}` | Texto | Buscador del catalogo por contenido. |
| `notificaciones {fecha}`, `loot_pool {fecha}` | TTL (30 dias) | Expiracion automatica de datos temporales. |
| `raids {idClan, estado}` | Compuesto | Calendario del clan filtrado por estado. |
| `loot_pool {idPersonaje, canjeado}` | Compuesto | Pool de canje pendiente de un personaje. |
| `usuarios {nombreUsuario}`, `clanes {nombreClan}` | Unicos | Integridad de identificadores. |

## Las 6 tareas del Grupo 3

| # | Tarea | Donde |
|---|---|---|
| 1 | Modelado embedding/referencing | `mongo-init/init.js` + tabla de arriba |
| 2 | Schema Validation (`$jsonSchema`) | validador de `loot_pool`: `participoRaid: [true]`, `personajeCaido: [false]` |
| 3 | Transaccion multi-documento | `ItemRepository.distribuirBotin()` con `TransactionTemplate` |
| 4 | Aggregation Pipeline: ranking de clanes por **tiempo, asistencia y dano** | `ItemRepository.refrescarRankingClanes()` |
| 5 | Indices | seccion de indices en `init.js` |
| 6 | Change Streams + materializada | `service/ChangeStreamService.java` -> `clanes_rankeados` |

## Flujo de una raid (Change Streams)

1. El Guild Master pulsa **Finalizar** y registra el **tiempo de finalizacion** y el
   **dano de cada asistente** (`POST /api/raids/{id}/finalizar`).
2. El backend guarda las metricas y deja la raid en **`BOSS_MUERTO`**.
3. El **Change Stream** detecta el cambio y, de forma reactiva: reparte el botin en una
   **transaccion**, marca la raid `COMPLETADA` y regenera las materializadas
   `clan_rankings` (jugadores) y **`clanes_rankeados`** (clanes mejor rankeados).
4. El frontend muestra el botin nuevo y el ranking actualizado.

**POST `/api/raids/{id}/finalizar`**
```json
{
  "duracionMinutos": 42,
  "danos": [
    { "idPersonaje": 1, "dano": 148000 },
    { "idPersonaje": 2, "dano": 41000 }
  ]
}
```

**GET `/api/items/ranking`** (ranking de clanes, Tarea 4)
```json
[
  {
    "id_clan": 1, "nombre_clan": "Los Primordiales de la Luz",
    "raids_completadas": 1, "asistencia_total": 6,
    "dano_total": 704000, "tiempo_promedio": 42,
    "dano_por_minuto": 16761.9, "puntaje": 17062
  }
]
```

## Control de acceso (RBAC)

- **Middleware JWT** (`config/JwtFilter`): valida el token y publica usuario y rol.
- **Rol del sistema** (`ADMIN` / `USER`), viaja en el token y se aplica con
  `@PreAuthorize("hasRole('ADMIN')")` sobre la administracion del catalogo de items y
  el listado global de personajes.
- **Rol de juego** (`Guild Master` / `Raider` / `Member`), vive en el documento del
  personaje y se verifica en los controladores para crear, modificar y finalizar
  raids, invitar raiders, asignar roles y traspasar el liderazgo.


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
