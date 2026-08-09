# Fusion de las dos versiones — que se conservo y que cambio

Este documento resume la integracion entre la version de Martin y la revision
paralela. **La base es la version de Martin**, sobre la que se aplicaron las
correcciones y funcionalidades que le faltaban.

---

## Lo que se CONSERVO de Martin (y por que era mejor)

### 1. `spring.mongodb.uri` — el nombre correcto de la propiedad
Martin tenia razon y la otra version estaba equivocada. En **Spring Boot 4.0** la
propiedad `spring.data.mongodb.uri` fue **renombrada a `spring.mongodb.uri`** (esta en
la guia oficial de migracion). Usar el nombre viejo hace que Spring ignore la URI y
caiga al valor por defecto `localhost:27017`, que era exactamente el error de conexion
que se venia arrastrando. Martin encontro la causa raiz real.

Por lo mismo se **descarto** el `config/MongoConfig.java` de la otra version (un
`MongoClient` explicito): con la propiedad correcta, la autoconfiguracion de Spring
basta y el codigo queda mas limpio.

### 2. `@Id private Integer idPersonaje` — el id de negocio ES el `_id`
Diseno mas limpio que tener un `_id` ObjectId y ademas un campo `idPersonaje`
duplicado. El validador `$jsonSchema` exige `_id` de forma coherente, y la API sigue
exponiendo `idPersonaje` en el JSON gracias al nombre del campo Java. Se descarto el
helper `MongoFinder` de la otra version: con este diseno, `findById` funciona nativo.

### 3. Transaccion de loot con `TransactionTemplate`
Ambas versiones llegaron a la misma solucion. Se conservo la de Martin.

### 4. Arreglo del inventario
Martin ya habia detectado que la bolsa se borraba al equipar y lo resolvio conservando
los items anteriores, devolviendo a la bolsa lo que se desequipa y recalculando el
`itemLevel`. Se conservo su implementacion tal cual.

### 5. Seed de usuarios generado por bucle
`admin` + `jugador1..jugador33` creados con un `for`. Mas limpio y mas completo que la
lista escrita a mano de la otra version.

### 6. Su `refrescarRanking()` de jugadores y su `$bucket`
Se conservaron intactos. El ranking de jugadores paso a exponerse en
`/api/items/ranking-jugadores`.

---

## Lo que se CAMBIO o AGREGO

### 1. Tarea 4: ranking de CLANES (era el hueco mas grave)
El enunciado pide el ranking **de clanes** segun *tiempo de finalizacion, asistencia y
dano total*. Ambas versiones agrupaban por personaje y no tenian esos campos.

- Se agregaron `raids.duracionMinutos` y `raids.inscripciones[].dano`.
- Nuevo `refrescarRankingClanes()`: `$match → $unwind → $match → $group(por clan) →
  $lookup(clanes) → $unwind → $project → $sort → $merge`, materializado en
  **`clanes_rankeados`** (los "clanes mejor rankeados" de la Tarea 6).
- `GET /api/items/ranking` devuelve ahora el ranking de clanes; el de jugadores quedo
  en `/api/items/ranking-jugadores`.
- El Guild Master informa las metricas en el modal de **Finalizar**.

### 2. El Change Stream estaba desconectado (Tarea 6)
En la version de Martin, `finalizarRaid` ponia la raid directamente en `COMPLETADA` y
el controlador repartia el botin de forma sincrona. Como el listener escucha
`BOSS_MUERTO`, **nunca se disparaba**: la Tarea 6 quedaba como codigo muerto.

Se restauro el flujo reactivo: `finalizar` deja la raid en `BOSS_MUERTO` y es el Change
Stream el que reparte el botin en la transaccion, marca `COMPLETADA` y regenera las
materializadas. Es lo que pide el enunciado y ademas es lo que se puede demostrar en la
defensa.

### 3. Bug al crear personaje desde la interfaz
El enum `Faccion` tenia constantes `LOS_PRIMORDIALES_DE_LA_LUZ`, pero el frontend
envia `"Los Primordiales de la Luz"`. Sin conversor, Jackson fallaba y **crear un
personaje desde la UI daba error**.

Se agrego `@JsonValue` / `@JsonCreator` al enum: en MongoDB se sigue guardando el
nombre de la constante (compatible con su seed), y hacia la API se expone el nombre
legible. Se conservo su diseno y se corrigio el error.

### 4. RBAC (requisito obligatorio que faltaba)
No habia ningun `@PreAuthorize`. Se agrego `hasRole('ADMIN')` a la administracion del
catalogo de items y al listado global de personajes, junto a los chequeos de Guild
Master que ya existian para clan y raids.

### 5. Orden de rutas
`@GetMapping("/{id}")` estaba declarado antes de rutas literales como
`/mis-personajes` y `/ranking`, con riesgo de capturarlas. Se movio al final en
`PersonajeController` e `ItemController`.

### 6. Validacion: rangos validos
El enunciado exige "rangos validos" en el `$jsonSchema`. Se agregaron `minimum` y
`maximum` a nivel, itemLevel, DKP, costo, composicion del grupo, duracion y dano.

### 7. Indices que faltaban
Se agrego el indice de **texto** sobre `items.nombreItem` (exigido) y uno de apoyo
sobre `historial_botin`.

### 8. Documentacion
- Se elimino del README una seccion completa del **Laboratorio 2 sobre PostGIS** que
  habia quedado y no corresponde a esta entrega.
- Se agrego la **justificacion embedding/referencing por coleccion**, la **estrategia
  de indices**, el mapa de las **6 tareas**, el flujo de Change Streams y la seccion de
  **RBAC**, con ejemplos JSON.

---

## Pendiente

- **Carpeta `Presentacion/`**: contiene el PDF del *Laboratorio 1*. Hay que reemplazarlo
  por las diapositivas de este laboratorio, que son entregable obligatorio.
- **Verificacion en maquina**: el arranque real del replica set y la compilacion Maven
  deben confirmarse con `docker compose down -v && docker compose up --build`.
