# Gestor de Clanes y Raids para MMORPG

Laboratorio 3 — Bases de Datos NoSQL con MongoDB Grupo 3 · Base de Datos Avanzada 1-2026 · Universidad de Santiago de Chile

Aplicación web para gestionar jugadores, personajes, clanes, eventos de raid y la distribución del botín. 
Es la migración a MongoDB del sistema que en los laboratorios anteriores se implementó sobre PostgreSQL.


## Tecnologías

- MongoDB 6.0 desplegado como Replica Set de dos nodos
- Java 21 con Spring Boot 4 y Spring Data MongoDB (sin JPA)
- Vue 3 con Vite y TypeScript, servido por Nginx
- Autenticación JWT y contraseñas con BCrypt; la API va sobre HTTPS
- Docker Compose para orquestar el despliegue


## Arquitectura

    Navegador
        |  HTTP + JSON (el token JWT viaja en la cabecera Authorization)
        v
    frontend  ·  Vue 3 + Nginx  ·  puerto 80
        |  proxy de /api hacia https://app:8443
        v
    app  ·  Spring Boot  ·  puerto 8443 (HTTPS)
        |  driver de MongoDB
        v
    Replica Set rs0
        mongo1 (primario, 27017)  <-- oplog -->  mongo2 (secundario, 27018)
              ^
              |
        mongo-init  ·  se ejecuta una vez y termina

El contenedor `mongo-init` prepara la base: forma el Replica Set, crea las colecciones con sus validadores, construye los índices y carga los datos de prueba. Una vez que termina se
apaga, y recién entonces arranca el backend.

El Replica Set no es opcional. MongoDB solo permite transacciones multi-documento y Change Streams sobre un conjunto replicado, porque ambas cosas se apoyan en el oplog. Sin él, dos
de las seis tareas del laboratorio no podrían implementarse.

Dentro del backend, `controller` expone los endpoints y aplica el control de acceso, `repository` concentra el acceso a datos con MongoTemplate, `service/ChangeStreamService`
escucha los cambios de la colección de raids, y `tablas` define los documentos.


## Instalación y despliegue

Lo único que hace falta es Docker con Docker Compose v2. No hay que instalar Java, Maven, Node ni MongoDB: todo se compila dentro de los contenedores.

### 1. Variables de entorno

En la raíz del repositorio hay un archivo `.env` con tres variables:

    DB_PASSWORD=123456
    JWT_SECRET=una_clave_muy_larga_y_segura_para_firmar_los_tokens_jwt
    SSL_PASSWORD=una_clave_muy_larga_y_segura_para_el_keystore_de_https

`JWT_SECRET` firma los tokens y debe tener al menos 32 caracteres. `SSL_PASSWORD` es la clave del keystore que usa el backend para HTTPS. El archivo ya viene incluido, así que no
hay que crearlo; solo asegurarse de que se guarde con saltos de línea LF y sin comillas.

### 2. Levantar el sistema

    docker compose down -v
    docker compose up --build

El `-v` borra los volúmenes de MongoDB. Es necesario porque los scripts de inicialización solo corren cuando la base está vacía: si queda un volumen de un intento anterior,
`mongo-init` se salta la creación de colecciones y el backend arranca contra una base a medio armar.

La primera vez tarda algunos minutos, porque compila el backend con Maven y el frontend con Vite. 
Está listo cuando en los logs aparece `Started Laboratorio1Application` sin errores de conexión debajo.

### 3. Configuración del Replica Set

No hay que hacer nada a mano: queda configurado automáticamente. El proceso es este.

Los dos nodos arrancan con la opción `--replSet rs0` (ver `docker-compose.yml`), lo que los deja listos para formar un 
conjunto pero todavía sin iniciar. Después, `mongo-init` ejecuta `mongo-init/init.js`, que empieza declarando la composición del conjunto:

    rs.initiate({
      _id: "rs0",
      members: [
        { _id: 0, host: "mongo1:27017", priority: 2 },
        { _id: 1, host: "mongo2:27017", priority: 1 }
      ]
    })

`mongo1` lleva mayor prioridad para que sea el primario. El script espera en un bucle hasta que la elección termina y 
hay un nodo capaz de aceptar escrituras; solo entonces crea las colecciones y carga los datos.

### 4. Acceso

- Aplicación web: http://localhost
- API: https://localhost:8443/api (certificado autofirmado, el navegador pide aceptarlo)

Todos los usuarios de prueba tienen la contraseña `123456`. El administrador es `admin`, y
los jugadores van de `jugador1` a `jugador33`.

### Si algo falla

Si el backend muestra `Connection refused` o intenta conectarse a `localhost:27017`, casi siempre es un volumen o una imagen de un intento anterior. Conviene partir de cero:

    docker compose down -v
    docker system prune -f
    docker compose up --build --force-recreate


## Documentación de la API

Todas las rutas cuelgan de `https://localhost:8443/api`. Excepto las de `/auth`, todas exigen la cabecera `Authorization: Bearer <token>`.

### Autenticación

`POST /auth/login`

    { "username": "admin", "password": "123456" }

Responde con el token que hay que enviar en las siguientes peticiones:

    { "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbCI6..." }

También existe `POST /auth/register` para crear una cuenta nueva.

### Personajes

    GET    /personajes/mis-personajes     personajes del usuario autenticado
    GET    /personajes/{id}               detalle de un personaje
    POST   /personajes                    crear personaje
    PUT    /personajes/{id}               editar (solo el dueño)
    PUT    /personajes/asignar-rol        cambiar el rol de un miembro (Guild Master)
    GET    /personajes                    listado completo (solo ADMIN)

Ejemplo de creación:

    {
      "nombrePersonaje": "Thalia",
      "clase": "Mago",
      "faccion": "Los Primordiales de la Luz"
    }

### Clanes

    GET    /clanes                              listar clanes
    GET    /clanes/{id}/miembros                miembros del clan
    POST   /clanes/fundar                       fundar un clan (queda como Guild Master)
    POST   /clanes/unirse                       unirse a un clan de la misma facción
    POST   /clanes/salir                        abandonar el clan
    PUT    /clanes/{id}/transfer-leadership     traspasar el liderazgo (Guild Master)

### Raids

    GET    /raids/calendario           raids del clan del personaje
    POST   /raids                      crear raid (Guild Master)
    POST   /raids/inscribirse          inscribirse en una raid
    POST   /raids/invitar-raiders      invitación masiva (Guild Master)
    POST   /raids/{id}/finalizar       cerrar la raid con sus métricas (Guild Master)

Al finalizar, el Guild Master informa el tiempo que tomó el encuentro y el daño que aportó cada asistente. Estos datos son los que alimentan el ranking de clanes:

    {
      "duracionMinutos": 42,
      "danos": [
        { "idPersonaje": 1, "dano": 94000 },
        { "idPersonaje": 3, "dano": 181000 },
        { "idPersonaje": 2, "dano": 32000 }
      ]
    }

La raid queda en estado `BOSS_MUERTO`. A partir de ahí actúa el Change Stream, como se explica más abajo.

### Inscripciones y botín

    GET    /inscripciones/mias?idPersonaje=      mis inscripciones
    PUT    /inscripciones/{id}/confirmar         confirmar asistencia
    GET    /items/mi-pool/{idPersonaje}          botín pendiente de canje
    POST   /items/canjear                        canjear un ítem gastando DKP
    GET    /items/historial/{idPersonaje}        historial de entregas

Para canjear:

    { "idPool": 4, "idPersonaje": 1 }

### Consultas agregadas

Estos endpoints devuelven directamente el resultado de los Aggregation Pipelines.

    GET    /items/ranking                  ranking de clanes
    GET    /items/ranking-jugadores        ranking individual
    GET    /items/distribucion-itemlevel   distribución por item level ($bucket)
    POST   /items/ranking/refrescar        recalcula las colecciones materializadas

Respuesta de `GET /items/ranking`:

    [
      {
        "id_clan": 1,
        "nombre_clan": "Los Heraldos de Aurora",
        "raids_completadas": 1,
        "asistencia_total": 6,
        "dano_total": 682000,
        "tiempo_promedio": 42,
        "dano_por_minuto": 16238.1,
        "puntaje": 16538
      }
    ]

### Catálogo de ítems

    GET    /items          consultar el catálogo
    POST   /items          crear ítem (solo ADMIN)
    PUT    /items/{id}     editar ítem (solo ADMIN)
    DELETE /items/{id}     eliminar ítem (solo ADMIN)


## Control de acceso

La autenticación se resuelve con un filtro JWT que valida el token en cada petición y publica el usuario y su rol en el contexto de seguridad.

La autorización funciona en dos niveles, porque el dominio tiene dos tipos de rol distintos.
El rol del sistema, `ADMIN` o `USER`, viaja dentro del token y se aplica con `@PreAuthorize("hasRole('ADMIN')")` sobre la administración del catálogo de ítems
y el listado global de personajes. El rol de juego, `Guild Master`, `Raider` o `Member`, vive en el documento del personaje y cambia dentro del clan, 
así que se verifica en los controladores de clan y raid.


## Modelado de datos

Los **personajes** están referenciados en su propia colección. El argumento es que cada personaje participa en muchas raids de forma independiente: 
embebidos dentro del jugador, cada raid tendría que duplicar sus datos y actualizar el item level obligaría a tocar varios documentos.

Las **inscripciones** están embebidas dentro de la raid, porque solo tienen sentido ahí y su cantidad está acotada por el grupo. 
Además es lo que permite el `$unwind` del pipeline de ranking sin un `$lookup` extra. Por la misma razón va embebido el **inventario** dentro del personaje, 
que es una relación uno a uno.

El **historial de botín** se referencia porque crece indefinidamente.


## Índices

    personajes    { idClan, clase, rolClan }      compuesto, para filtrar por clase y rol dentro de un clan
    personajes    { nombrePersonaje }             único
    loot_pool     { idRaid, idItem }              único, impide asignar dos veces el mismo ítem
    items         { nombreItem }                  de texto, para el buscador del catálogo
    notificaciones{ fecha }                       TTL de 30 días
    loot_pool     { fecha }                       TTL de 30 días
    raids         { idClan, estado }              compuesto, para el calendario del clan
    usuarios      { nombreUsuario }               único
    clanes        { nombreClan }                  único

El orden del índice compuesto de personajes no es casual: los índices compuestos se aprovechan de izquierda a derecha, y toda consulta del dominio empieza filtrando por clan.


## Las seis tareas

    1. Modelado embedding/referencing    init.js y la sección de modelado
    2. Schema Validation                 validador de loot_pool en init.js
    3. Transacción multi-documento       ItemRepository.distribuirBotin()
    4. Aggregation Pipeline              ItemRepository.refrescarRankingClanes()
    5. Índices                           sección de índices en init.js
    6. Change Streams                    service/ChangeStreamService.java

La validación resuelve la regla  con `enum` de un solo valor: en `loot_pool`, `participoRaid` solo acepta `true` y `personajeCaido` solo acepta `false`. 
Un documento de botín no puede existir si el personaje no participó o estaba caído, aunque alguien escriba directamente en la base.


## Flujo de una raid

Cuando el Guild Master finaliza una raid, el controlador guarda las métricas y deja la raid en estado `BOSS_MUERTO`. 
Ahí termina su trabajo: no reparte el botín ni recalcula nada.

El Change Stream, que está suscrito a los cambios de la colección `raids`, detecta ese cambio de estado y dispara el resto. 
Distribuye el botín dentro de una transacción, marca la raid como `COMPLETADA` y regenera las colecciones materializadas con `$merge`. 
El frontend refleja el botín nuevo y el ranking actualizado.

Es donde se ven las tareas 3, 4 y 6 funcionando juntas.


## Ver la base de datos

El Replica Set se anuncia con los nombres internos de Docker, que no se resuelven desde la máquina anfitriona; 
por eso los clientes externos deben conectarse con `directConnection=true`.

La opción más simple es la interfaz web incluida, que corre dentro de la red de Docker y no necesita ajustes. 
No se levanta con el despliegue normal, solo con el perfil `tools`:

    docker compose --profile tools up -d mongo-express

Queda en http://localhost:8081, usuario y clave `admin`. Para MongoDB Compass o IntelliJ la cadena es 
`mongodb://localhost:27017/?directConnection=true`, y desde la consola, `docker exec -it mongo1 mongosh laboratorio1`.


## Estructura del repositorio

    docker-compose.yml      orquestación de los cinco contenedores
    .env                    variables de entorno
    mongo-init/init.js      Replica Set, colecciones, validadores, índices y datos
    backend/                API REST en Spring Boot
    frontend/               interfaz en Vue 3
    Presentacion/           diapositivas

