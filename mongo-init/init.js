// Inicializar el Replica Set
try {
    rs.status();
    print("Replica set ya inicializado.");
} catch (e) {
    print("Inicializando replica set rs0...");
    rs.initiate({
        _id: "rs0",
        members: [
            { _id: 0, host: "mongo1:27017", priority: 2 },
            { _id: 1, host: "mongo2:27017", priority: 1 }
        ]
    });
    var isMaster = false;
    while (!isMaster) {
        var status = db.hello();
        isMaster = status.isWritablePrimary;
        if (!isMaster) {
            print("Esperando a que mongo1 sea el primario escribible...");
            sleep(1000);
        }
    }
    print("Replica set iniciado con exito.");
}

db = db.getSiblingDB("laboratorio1");

var colecciones = db.getCollectionNames();
if (colecciones.indexOf("usuarios") !== -1) {
    print("Base de datos ya inicializada. Saltando creacion de colecciones e insercion de datos.");
    quit(0);
}

print("Creando colecciones con validadores...");

// 1. Usuarios
db.createCollection("usuarios", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["_id", "nombreUsuario", "password", "rol"],
            properties: {
                _id: { bsonType: "int" },
                nombreUsuario: { bsonType: "string" },
                password: { bsonType: "string" },
                rol: { bsonType: "string", enum: ["USER", "ADMIN"] }
            }
        }
    }
});

// 2. Clanes
db.createCollection("clanes", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["_id", "nombreClan"],
            properties: {
                _id: { bsonType: "int" },
                idLider: { bsonType: ["int", "null"] },
                nombreClan: { bsonType: "string" },
                faccion: { bsonType: ["string", "null"] },
                auditoriaLiderazgo: {
                    bsonType: "array",
                    items: {
                        bsonType: "object",
                        required: ["idAuditoria", "idLiderAnterior", "idLiderNuevo", "fechaTransferencia"],
                        properties: {
                            idAuditoria: { bsonType: "int" },
                            idLiderAnterior: { bsonType: "int" },
                            idLiderNuevo: { bsonType: "int" },
                            fechaTransferencia: { bsonType: "date" }
                        }
                    }
                }
            }
        }
    }
});

// 3. Personajes
db.createCollection("personajes", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["_id", "idUsuario", "nombrePersonaje", "clase", "faccion", "rolClan", "nivel", "itemLevel", "puntosDkpActuales", "caido"],
            properties: {
                _id: { bsonType: "int" },
                idUsuario: { bsonType: "int" },
                idClan: { bsonType: ["int", "null"] },
                nombrePersonaje: { bsonType: "string" },
                clase: { bsonType: "string" },
                faccion: { bsonType: "string" },
                rolClan: { bsonType: "string" },
                nivel: { bsonType: "int", minimum: 1 },
                itemLevel: { bsonType: "int", minimum: 0 },
                puntosDkpActuales: { bsonType: "int" },
                caido: { bsonType: "bool" },
                inventario: {
                    bsonType: "object",
                    required: ["idInventario"],
                    properties: {
                        idInventario: { bsonType: "int" },
                        armaduraEquipado: { bsonType: ["int", "null"] },
                        armaEquipado: { bsonType: ["int", "null"] },
                        accesorioEquipado: { bsonType: ["int", "null"] },
                        items: { bsonType: "array", items: { bsonType: "int" } }
                    }
                }
            }
        }
    }
});

// 4. Items
db.createCollection("items", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["_id", "nombreItem", "rareza", "tipo", "nivel", "costoDkp"],
            properties: {
                _id: { bsonType: "int" },
                nombreItem: { bsonType: "string" },
                rareza: { bsonType: "string" },
                tipo: { bsonType: "string", enum: ["ARMADURA", "ARMA", "ACCESORIO"] },
                nivel: { bsonType: "int" },
                costoDkp: { bsonType: "int" }
            }
        }
    }
});

// 5. Raids
db.createCollection("raids", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["_id", "idClan", "nombreRaid", "fechaRaid", "itemLevelMinimo", "tanques", "healers", "dps", "estado"],
            properties: {
                _id: { bsonType: "int" },
                idClan: { bsonType: "int" },
                nombreRaid: { bsonType: "string" },
                fechaRaid: { bsonType: "date" },
                itemLevelMinimo: { bsonType: "int" },
                tanques: { bsonType: "int" },
                healers: { bsonType: "int" },
                dps: { bsonType: "int" },
                estado: { bsonType: "string", enum: ["PROGRAMADA", "BOSS_MUERTO", "COMPLETADA"] },
                inscripciones: {
                    bsonType: "array",
                    items: {
                        bsonType: "object",
                        required: ["idInscripcion", "idPersonaje", "rolEnRaid", "confirmado"],
                        properties: {
                            idInscripcion: { bsonType: "int" },
                            idPersonaje: { bsonType: "int" },
                            rolEnRaid: { bsonType: "string" },
                            confirmado: { bsonType: "bool" }
                        }
                    }
                }
            }
        }
    }
});

// 6. Loot Pool
db.createCollection("loot_pool", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["_id", "idPersonaje", "idItem", "idRaid", "participoRaid", "personajeCaido", "canjeado", "fecha"],
            properties: {
                _id: { bsonType: "int" },
                idPersonaje: { bsonType: "int" },
                idItem: { bsonType: "int" },
                idRaid: { bsonType: "int" },
                participoRaid: { bsonType: "bool", description: "El personaje debe haber participado en la raid", enum: [true] },
                personajeCaido: { bsonType: "bool", description: "El personaje no debe estar caido", enum: [false] },
                canjeado: { bsonType: "bool" },
                fecha: { bsonType: "date" }
            }
        }
    }
});

// 7. Historial de Botin
db.createCollection("historial_botin", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["_id", "idPersonaje", "idItem", "idRaid", "fechaEntrega", "nombreItem", "nombrePersonaje"],
            properties: {
                _id: { bsonType: "int" },
                idPersonaje: { bsonType: "int" },
                idItem: { bsonType: "int" },
                idRaid: { bsonType: "int" },
                fechaEntrega: { bsonType: "date" },
                nombreItem: { bsonType: "string" },
                nombrePersonaje: { bsonType: "string" }
            }
        }
    }
});

// 8. Notificaciones
db.createCollection("notificaciones", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["_id", "idPersonaje", "tipo", "mensaje", "leida", "fecha"],
            properties: {
                _id: { bsonType: "int" },
                idPersonaje: { bsonType: "int" },
                tipo: { bsonType: "string" },
                mensaje: { bsonType: "string" },
                leida: { bsonType: "bool" },
                fecha: { bsonType: "date" }
            }
        }
    }
});

db.createCollection("database_sequences");
db.createCollection("clan_rankings");

// ============================================================
// INDICES
// ============================================================
print("Creando indices...");
db.personajes.createIndex({ idClan: 1, clase: 1, rolClan: 1 });
db.personajes.createIndex({ nombrePersonaje: 1 }, { unique: true });
db.usuarios.createIndex({ nombreUsuario: 1 }, { unique: true });
db.clanes.createIndex({ nombreClan: 1 }, { unique: true });
db.notificaciones.createIndex({ fecha: 1 }, { expireAfterSeconds: 2592000 });
db.loot_pool.createIndex({ fecha: 1 }, { expireAfterSeconds: 2592000 });
db.raids.createIndex({ idClan: 1, estado: 1 });
db.loot_pool.createIndex({ idPersonaje: 1, canjeado: 1 });
db.loot_pool.createIndex({ idRaid: 1, idItem: 1 }, { unique: true });
print("Indices creados.");

// ============================================================
// POBLADO DE DATOS (MIGRACION EXACTA DESDE EL SQL)
// ============================================================
print("Insertando datos de prueba...");

// ---- 1. USUARIOS (admin + jugador1 al jugador33) ----
// password BCrypt para "123456"
var bcryptPass = "$2b$10$19GqzS29tj2iBe5dgvq7yuPsdXhCX3DLA4Zn3nLzUNvmsuq7JnIG6";
var usuariosSeed = [
    { _id: 1, nombreUsuario: "admin", password: bcryptPass, rol: "ADMIN" }
];
for (var i = 1; i <= 33; i++) {
    usuariosSeed.push({
        _id: i + 1,
        nombreUsuario: "jugador" + i,
        password: bcryptPass,
        rol: "USER"
    });
}
db.usuarios.insertMany(usuariosSeed);

// ---- 2. CLANES ----
db.clanes.insertMany([
    { _id: 1, nombreClan: "Los Heraldos de Aurora", idLider: 1, faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", auditoriaLiderazgo: [] },
    { _id: 2, nombreClan: "Sombras del Ocaso", idLider: 13, faccion: "LOS_HIJOS_DEL_GRIS", auditoriaLiderazgo: [] },
    { _id: 3, nombreClan: "Culto del Abismo", idLider: 23, faccion: "LOS_MARCADOS_POR_EL_ABISMO", auditoriaLiderazgo: [] }
]);

// ---- 3. ITEMS (CATALOGO DE 20 ITEMS) ----
var itemsData = [
    { _id: 1, nombreItem: "Agonia de Escarcha", rareza: "Legendario", tipo: "ARMA", nivel: 280, costoDkp: 500 },
    { _id: 2, nombreItem: "Filo de Tormenta", rareza: "Epico", tipo: "ARMA", nivel: 260, costoDkp: 350 },
    { _id: 3, nombreItem: "Daga del Vacio", rareza: "Raro", tipo: "ARMA", nivel: 230, costoDkp: 180 },
    { _id: 4, nombreItem: "Baculo del Arcano", rareza: "Epico", tipo: "ARMA", nivel: 270, costoDkp: 400 },
    { _id: 5, nombreItem: "Hacha de Lenador", rareza: "Comun", tipo: "ARMA", nivel: 150, costoDkp: 40 },
    { _id: 6, nombreItem: "Pechera de Lordaeron", rareza: "Epico", tipo: "ARMADURA", nivel: 260, costoDkp: 300 },
    { _id: 7, nombreItem: "Tunica de Telar Arcano", rareza: "Raro", tipo: "ARMADURA", nivel: 240, costoDkp: 150 },
    { _id: 8, nombreItem: "Coraza del Berserker", rareza: "Legendario", tipo: "ARMADURA", nivel: 290, costoDkp: 550 },
    { _id: 9, nombreItem: "Cota de Mallas Oxidada", rareza: "Comun", tipo: "ARMADURA", nivel: 160, costoDkp: 30 },
    { _id: 10, nombreItem: "Vestiduras de las Sombras", rareza: "Epico", tipo: "ARMADURA", nivel: 250, costoDkp: 280 },
    { _id: 11, nombreItem: "Anillo del Destino", rareza: "Epico", tipo: "ACCESORIO", nivel: 250, costoDkp: 300 },
    { _id: 12, nombreItem: "Capa de las Sombras", rareza: "Raro", tipo: "ACCESORIO", nivel: 235, costoDkp: 120 },
    { _id: 13, nombreItem: "Amuleto de Fuego Solar", rareza: "Legendario", tipo: "ACCESORIO", nivel: 275, costoDkp: 480 },
    { _id: 14, nombreItem: "Trinket de Novato", rareza: "Comun", tipo: "ACCESORIO", nivel: 140, costoDkp: 25 },
    { _id: 15, nombreItem: "Pendiente del Oraculo", rareza: "Epico", tipo: "ACCESORIO", nivel: 255, costoDkp: 320 },
    { _id: 16, nombreItem: "Martillo del Alba", rareza: "Epico", tipo: "ARMA", nivel: 265, costoDkp: 380 },
    { _id: 17, nombreItem: "Arco de Vientos", rareza: "Raro", tipo: "ARMA", nivel: 245, costoDkp: 200 },
    { _id: 18, nombreItem: "Grebas de Obsidiana", rareza: "Epico", tipo: "ARMADURA", nivel: 270, costoDkp: 420 },
    { _id: 19, nombreItem: "Yelmo del Vigia", rareza: "Raro", tipo: "ARMADURA", nivel: 230, costoDkp: 140 },
    { _id: 20, nombreItem: "Sello del Abismo", rareza: "Legendario", tipo: "ACCESORIO", nivel: 265, costoDkp: 460 }
];
db.items.insertMany(itemsData);

// Guardar niveles de items en un mapa para calculo automatico de itemLevel
var itemLevelMap = {};
itemsData.forEach(function (item) {
    itemLevelMap[item._id] = item.nivel;
});

// ---- 4. PERSONAJES CON SUS INVENTARIOS EQUIPADOS ----
// Mapeo directo del SQL: (pjId, idUsuario, idClan, nombre, clase, faccion, rol, nivel, dkp, arm, arma, acc)
var personajesRaw = [
    // Clan 1: Los Heraldos de Aurora
    { _id: 1, idUsuario: 1, idClan: 1, nombrePersonaje: "Arthon", clase: "Paladin", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Guild Master", nivel: 60, puntosDkpActuales: 1500, arm: 8, arma: 1, acc: 13 },
    { _id: 2, idUsuario: 2, idClan: 1, nombrePersonaje: "Elyndra", clase: "Sacerdote", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Raider", nivel: 58, puntosDkpActuales: 980, arm: 10, arma: 4, acc: 15 },
    { _id: 3, idUsuario: 3, idClan: 1, nombrePersonaje: "Kaelen", clase: "Mago", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Raider", nivel: 59, puntosDkpActuales: 910, arm: 7, arma: 4, acc: 11 },
    { _id: 4, idUsuario: 4, idClan: 1, nombrePersonaje: "Sylara", clase: "Cazador", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Raider", nivel: 57, puntosDkpActuales: 860, arm: 6, arma: 2, acc: 12 },
    { _id: 5, idUsuario: 5, idClan: 1, nombrePersonaje: "Bran", clase: "Guerrero", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Raider", nivel: 58, puntosDkpActuales: 940, arm: 6, arma: 1, acc: 11 },
    { _id: 6, idUsuario: 6, idClan: 1, nombrePersonaje: "Mira", clase: "Chaman", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Raider", nivel: 55, puntosDkpActuales: 640, arm: 7, arma: 3, acc: 14 },
    { _id: 7, idUsuario: 7, idClan: 1, nombrePersonaje: "Dorn", clase: "Guerrero", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Member", nivel: 45, puntosDkpActuales: 320, arm: 9, arma: 5, acc: 14 },
    { _id: 8, idUsuario: 8, idClan: 1, nombrePersonaje: "Lira", clase: "Sacerdote", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Raider", nivel: 50, puntosDkpActuales: 480, arm: 9, arma: 5, acc: 12 },
    { _id: 9, idUsuario: 9, idClan: 1, nombrePersonaje: "Theron", clase: "Paladin", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Raider", nivel: 60, puntosDkpActuales: 1120, arm: 18, arma: 16, acc: 13 },
    { _id: 10, idUsuario: 10, idClan: 1, nombrePersonaje: "Isolde", clase: "Mago", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Raider", nivel: 59, puntosDkpActuales: 890, arm: 7, arma: 4, acc: 20 },
    { _id: 11, idUsuario: 11, idClan: 1, nombrePersonaje: "Gareth", clase: "Picaro", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Member", nivel: 52, puntosDkpActuales: 560, arm: 19, arma: 17, acc: 12 },
    { _id: 12, idUsuario: 12, idClan: 1, nombrePersonaje: "Nessa", clase: "Chaman", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Raider", nivel: 57, puntosDkpActuales: 830, arm: 10, arma: 16, acc: 15 },
    // Clan 2: Sombras del Ocaso
    { _id: 13, idUsuario: 13, idClan: 2, nombrePersonaje: "Vorlok", clase: "Brujo", faccion: "LOS_HIJOS_DEL_GRIS", rolClan: "Guild Master", nivel: 60, puntosDkpActuales: 1340, arm: 10, arma: 4, acc: 11 },
    { _id: 14, idUsuario: 14, idClan: 2, nombrePersonaje: "Nyx", clase: "Picaro", faccion: "LOS_HIJOS_DEL_GRIS", rolClan: "Raider", nivel: 54, puntosDkpActuales: 620, arm: 7, arma: 3, acc: 12 },
    { _id: 15, idUsuario: 15, idClan: 2, nombrePersonaje: "Draven", clase: "Guerrero", faccion: "LOS_HIJOS_DEL_GRIS", rolClan: "Raider", nivel: 60, puntosDkpActuales: 1180, arm: 8, arma: 1, acc: 11 },
    { _id: 16, idUsuario: 16, idClan: 2, nombrePersonaje: "Selene", clase: "Sacerdote", faccion: "LOS_HIJOS_DEL_GRIS", rolClan: "Raider", nivel: 58, puntosDkpActuales: 900, arm: 10, arma: 16, acc: 15 },
    { _id: 17, idUsuario: 17, idClan: 2, nombrePersonaje: "Korrin", clase: "Cazador", faccion: "LOS_HIJOS_DEL_GRIS", rolClan: "Raider", nivel: 58, puntosDkpActuales: 870, arm: 6, arma: 17, acc: 13 },
    { _id: 18, idUsuario: 18, idClan: 2, nombrePersonaje: "Ashka", clase: "Chaman", faccion: "LOS_HIJOS_DEL_GRIS", rolClan: "Raider", nivel: 57, puntosDkpActuales: 810, arm: 7, arma: 4, acc: 20 },
    { _id: 19, idUsuario: 19, idClan: 2, nombrePersonaje: "Ryn", clase: "Picaro", faccion: "LOS_HIJOS_DEL_GRIS", rolClan: "Member", nivel: 44, puntosDkpActuales: 260, arm: 9, arma: 5, acc: 14 },
    { _id: 20, idUsuario: 20, idClan: 2, nombrePersonaje: "Halvar", clase: "Guerrero", faccion: "LOS_HIJOS_DEL_GRIS", rolClan: "Member", nivel: 53, puntosDkpActuales: 540, arm: 19, arma: 2, acc: 12 },
    { _id: 21, idUsuario: 21, idClan: 2, nombrePersonaje: "Petra", clase: "Mago", faccion: "LOS_HIJOS_DEL_GRIS", rolClan: "Raider", nivel: 58, puntosDkpActuales: 850, arm: 7, arma: 4, acc: 11 },
    { _id: 22, idUsuario: 22, idClan: 2, nombrePersonaje: "Osric", clase: "Paladin", faccion: "LOS_HIJOS_DEL_GRIS", rolClan: "Raider", nivel: 59, puntosDkpActuales: 1040, arm: 18, arma: 16, acc: 20 },
    // Clan 3: Culto del Abismo
    { _id: 23, idUsuario: 23, idClan: 3, nombrePersonaje: "Malketh", clase: "Brujo", faccion: "LOS_MARCADOS_POR_EL_ABISMO", rolClan: "Guild Master", nivel: 60, puntosDkpActuales: 1420, arm: 8, arma: 1, acc: 13 },
    { _id: 24, idUsuario: 24, idClan: 3, nombrePersonaje: "Zaira", clase: "Mago", faccion: "LOS_MARCADOS_POR_EL_ABISMO", rolClan: "Raider", nivel: 58, puntosDkpActuales: 880, arm: 7, arma: 4, acc: 15 },
    { _id: 25, idUsuario: 25, idClan: 3, nombrePersonaje: "Grimm", clase: "Guerrero", faccion: "LOS_MARCADOS_POR_EL_ABISMO", rolClan: "Raider", nivel: 59, puntosDkpActuales: 1060, arm: 18, arma: 2, acc: 11 },
    { _id: 26, idUsuario: 26, idClan: 3, nombrePersonaje: "Velka", clase: "Sacerdote", faccion: "LOS_MARCADOS_POR_EL_ABISMO", rolClan: "Raider", nivel: 58, puntosDkpActuales: 920, arm: 10, arma: 16, acc: 20 },
    { _id: 27, idUsuario: 27, idClan: 3, nombrePersonaje: "Torvin", clase: "Cazador", faccion: "LOS_MARCADOS_POR_EL_ABISMO", rolClan: "Raider", nivel: 56, puntosDkpActuales: 780, arm: 6, arma: 17, acc: 12 },
    { _id: 28, idUsuario: 28, idClan: 3, nombrePersonaje: "Sable", clase: "Picaro", faccion: "LOS_MARCADOS_POR_EL_ABISMO", rolClan: "Raider", nivel: 55, puntosDkpActuales: 700, arm: 7, arma: 3, acc: 12 },
    { _id: 29, idUsuario: 29, idClan: 3, nombrePersonaje: "Umbra", clase: "Brujo", faccion: "LOS_MARCADOS_POR_EL_ABISMO", rolClan: "Member", nivel: 43, puntosDkpActuales: 240, arm: 9, arma: 5, acc: 14 },
    { _id: 30, idUsuario: 30, idClan: 3, nombrePersonaje: "Kessa", clase: "Chaman", faccion: "LOS_MARCADOS_POR_EL_ABISMO", rolClan: "Raider", nivel: 57, puntosDkpActuales: 830, arm: 10, arma: 4, acc: 15 },
    { _id: 31, idUsuario: 31, idClan: 3, nombrePersonaje: "Rurik", clase: "Paladin", faccion: "LOS_MARCADOS_POR_EL_ABISMO", rolClan: "Raider", nivel: 60, puntosDkpActuales: 1150, arm: 8, arma: 16, acc: 13 },
    { _id: 32, idUsuario: 32, idClan: 3, nombrePersonaje: "Nyra", clase: "Sacerdote", faccion: "LOS_MARCADOS_POR_EL_ABISMO", rolClan: "Raider", nivel: 51, puntosDkpActuales: 500, arm: 19, arma: 17, acc: 20 },
    // Sin clan
    { _id: 33, idUsuario: 33, idClan: null, nombrePersonaje: "Aeliana", clase: "Cazador", faccion: "LOS_PRIMORDIALES_DE_LA_LUZ", rolClan: "Member", nivel: 20, puntosDkpActuales: 60, arm: 9, arma: 5, acc: 14 },
    { _id: 34, idUsuario: 34, idClan: null, nombrePersonaje: "Kodrun", clase: "Guerrero", faccion: "LOS_HIJOS_DEL_GRIS", rolClan: "Member", nivel: 18, puntosDkpActuales: 40, arm: 9, arma: 5, acc: 14 }
];

var personajesInsert = [];
personajesRaw.forEach(function (pj) {
    // Calcular el itemLevel sumando los niveles de los items equipados
    var ilvl = 0;
    if (pj.arm) ilvl += itemLevelMap[pj.arm] || 0;
    if (pj.arma) ilvl += itemLevelMap[pj.arma] || 0;
    if (pj.acc) ilvl += itemLevelMap[pj.acc] || 0;

    // Generar 3 items aleatorios que no esten equipados para meter a la bolsa
    var equipped = [pj.arm, pj.arma, pj.acc];
    var bagItems = [];
    while (bagItems.length < 3) {
        var randItem = Math.floor(Math.random() * 20) + 1;
        if (equipped.indexOf(randItem) === -1 && bagItems.indexOf(randItem) === -1) {
            bagItems.push(randItem);
        }
    }

    personajesInsert.push({
        _id: pj._id,
        idUsuario: pj.idUsuario,
        idClan: pj.idClan,
        nombrePersonaje: pj.nombrePersonaje,
        clase: pj.clase,
        faccion: pj.faccion,
        rolClan: pj.rolClan,
        nivel: pj.nivel,
        itemLevel: ilvl,
        puntosDkpActuales: pj.puntosDkpActuales,
        caido: false,
        inventario: {
            idInventario: pj._id,
            armaduraEquipado: pj.arm || null,
            armaEquipado: pj.arma || null,
            accesorioEquipado: pj.acc || null,
            items: bagItems
        }
    });
});
db.personajes.insertMany(personajesInsert);

// ---- 5. RAIDS (CON SUS INSCRIPCIONES) ----
var hace1Semana = new Date(); hace1Semana.setDate(hace1Semana.getDate() - 7);
var hace2Semanas = new Date(); hace2Semanas.setDate(hace2Semanas.getDate() - 14);
var hace3Semanas = new Date(); hace3Semanas.setDate(hace3Semanas.getDate() - 21);
var manana = new Date(); manana.setDate(manana.getDate() + 1);
var pasadoManana = new Date(); pasadoManana.setDate(pasadoManana.getDate() + 2);

db.raids.insertMany([
    // Raid 1: Clan 1, PROGRAMADA
    {
        _id: 1, idClan: 1, nombreRaid: "Fortaleza de Elderwood", fechaRaid: manana,
        itemLevelMinimo: 600, tanques: 2, healers: 3, dps: 6, estado: "PROGRAMADA",
        inscripciones: [
            { idInscripcion: 1, idPersonaje: 1, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 2, idPersonaje: 9, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 3, idPersonaje: 2, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 4, idPersonaje: 12, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 5, idPersonaje: 3, rolEnRaid: "DPS", confirmado: true },
            { idInscripcion: 6, idPersonaje: 4, rolEnRaid: "DPS", confirmado: true }
        ]
    },
    // Raid 2: Clan 1, PROGRAMADA
    {
        _id: 2, idClan: 1, nombreRaid: "Cavernas de Murkmire", fechaRaid: pasadoManana,
        itemLevelMinimo: 700, tanques: 2, healers: 2, dps: 6, estado: "PROGRAMADA",
        inscripciones: [
            { idInscripcion: 7, idPersonaje: 1, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 8, idPersonaje: 5, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 9, idPersonaje: 2, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 10, idPersonaje: 12, rolEnRaid: "HEALER", confirmado: false },
            { idInscripcion: 11, idPersonaje: 3, rolEnRaid: "DPS", confirmado: true },
            { idInscripcion: 12, idPersonaje: 10, rolEnRaid: "DPS", confirmado: true }
        ]
    },
    // Raid 3: Clan 1, COMPLETADA (se completa)
    {
        _id: 3, idClan: 1, nombreRaid: "Asalto a la Ciudadela", fechaRaid: hace1Semana,
        itemLevelMinimo: 500, tanques: 2, healers: 3, dps: 6, estado: "COMPLETADA",
        inscripciones: [
            { idInscripcion: 13, idPersonaje: 1, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 14, idPersonaje: 5, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 15, idPersonaje: 2, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 16, idPersonaje: 8, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 17, idPersonaje: 3, rolEnRaid: "DPS", confirmado: true },
            { idInscripcion: 18, idPersonaje: 4, rolEnRaid: "DPS", confirmado: true },
            { idInscripcion: 19, idPersonaje: 12, rolEnRaid: "HEALER", confirmado: false }
        ]
    },
    // Raid 4: Clan 2, PROGRAMADA
    {
        _id: 4, idClan: 2, nombreRaid: "Guarida Sombria", fechaRaid: manana,
        itemLevelMinimo: 600, tanques: 2, healers: 2, dps: 5, estado: "PROGRAMADA",
        inscripciones: [
            { idInscripcion: 20, idPersonaje: 15, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 21, idPersonaje: 22, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 22, idPersonaje: 16, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 23, idPersonaje: 18, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 24, idPersonaje: 17, rolEnRaid: "DPS", confirmado: true }
        ]
    },
    // Raid 5: Clan 2, COMPLETADA (se completa)
    {
        _id: 5, idClan: 2, nombreRaid: "Cripta de Ceniza", fechaRaid: hace2Semanas,
        itemLevelMinimo: 500, tanques: 2, healers: 2, dps: 5, estado: "COMPLETADA",
        inscripciones: [
            { idInscripcion: 25, idPersonaje: 15, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 26, idPersonaje: 22, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 27, idPersonaje: 16, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 28, idPersonaje: 18, rolEnRaid: "HEALER", confirmado: false },
            { idInscripcion: 29, idPersonaje: 17, rolEnRaid: "DPS", confirmado: true },
            { idInscripcion: 30, idPersonaje: 21, rolEnRaid: "DPS", confirmado: true },
            { idInscripcion: 31, idPersonaje: 14, rolEnRaid: "DPS", confirmado: true }
        ]
    },
    // Raid 6: Clan 3, PROGRAMADA
    {
        _id: 6, idClan: 3, nombreRaid: "Ritual del Vacio", fechaRaid: manana,
        itemLevelMinimo: 600, tanques: 2, healers: 2, dps: 5, estado: "PROGRAMADA",
        inscripciones: [
            { idInscripcion: 32, idPersonaje: 25, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 33, idPersonaje: 31, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 34, idPersonaje: 26, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 35, idPersonaje: 30, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 36, idPersonaje: 24, rolEnRaid: "DPS", confirmado: true }
        ]
    },
    // Raid 7: Clan 3, COMPLETADA (se completa)
    {
        _id: 7, idClan: 3, nombreRaid: "Fauces del Abismo", fechaRaid: hace3Semanas,
        itemLevelMinimo: 500, tanques: 2, healers: 2, dps: 5, estado: "COMPLETADA",
        inscripciones: [
            { idInscripcion: 37, idPersonaje: 25, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 38, idPersonaje: 31, rolEnRaid: "TANQUE", confirmado: true },
            { idInscripcion: 39, idPersonaje: 26, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 40, idPersonaje: 30, rolEnRaid: "HEALER", confirmado: true },
            { idInscripcion: 41, idPersonaje: 24, rolEnRaid: "DPS", confirmado: true },
            { idInscripcion: 42, idPersonaje: 28, rolEnRaid: "DPS", confirmado: true },
            { idInscripcion: 43, idPersonaje: 32, rolEnRaid: "HEALER", confirmado: false },
            { idInscripcion: 44, idPersonaje: 27, rolEnRaid: "DPS", confirmado: false }
        ]
    }
]);

// ---- 6. LOOT POOL (ITEMS DISPONIBLES EN LAS COMPLETED RAIDS) ----
db.loot_pool.insertMany([
    // Raid 3 (COMPLETADA): Item 2 (Filo de Tormenta) libre para canjear por Elyndra
    { _id: 1, idPersonaje: 2, idItem: 2, idRaid: 3, participoRaid: true, personajeCaido: false, canjeado: false, fecha: hace1Semana },
    // Raid 5 (COMPLETADA): Item 16 (Martillo del Alba) libre para Draven
    { _id: 2, idPersonaje: 15, idItem: 16, idRaid: 5, participoRaid: true, personajeCaido: false, canjeado: false, fecha: hace2Semanas },
    // Raid 7 (COMPLETADA): Item 20 (Sello del Abismo) ya canjeado por Grimm
    { _id: 3, idPersonaje: 25, idItem: 20, idRaid: 7, participoRaid: true, personajeCaido: false, canjeado: true, fecha: hace3Semanas }
]);

// ---- 7. HISTORIAL DE BOTIN ----
db.historial_botin.insertMany([
    // Grimm canjeo el Sello del Abismo en la Raid 7
    { _id: 1, idPersonaje: 25, idItem: 20, idRaid: 7, fechaEntrega: hace3Semanas, nombreItem: "Sello del Abismo", nombrePersonaje: "Grimm" },
    // Vorlok canjeo el Baculo del Arcano en la Raid 5 (hace 2 semanas)
    { _id: 2, idPersonaje: 13, idItem: 4, idRaid: 5, fechaEntrega: hace2Semanas, nombreItem: "Baculo del Arcano", nombrePersonaje: "Vorlok" }
]);

// ---- 8. NOTIFICACIONES ----
db.notificaciones.insertMany([
    { _id: 1, idPersonaje: 2, tipo: "LOOT_DISPONIBLE", mensaje: "Tienes un item disponible en el Loot Pool de Asalto a la Ciudadela.", leida: false, fecha: hace1Semana },
    { _id: 2, idPersonaje: 15, tipo: "LOOT_DISPONIBLE", mensaje: "Tienes un item disponible en el Loot Pool de Cripta de Ceniza.", leida: false, fecha: hace2Semanas },
    { _id: 3, idPersonaje: 25, tipo: "ITEM_ENTREGADO", mensaje: "El item Sello del Abismo ha sido agregado a tu inventario.", leida: true, fecha: hace3Semanas }
]);

// ---- CONTADORES DE SECUENCIA FINALIZADOS ----
db.database_sequences.insertMany([
    { _id: "usuarioId", seq: 34 },
    { _id: "clanId", seq: 3 },
    { _id: "personajeId", seq: 34 },
    { _id: "itemId", seq: 20 },
    { _id: "raidId", seq: 7 },
    { _id: "inscripcionId", seq: 44 },
    { _id: "lootPoolId", seq: 3 },
    { _id: "historialBotinId", seq: 2 },
    { _id: "notificacionId", seq: 3 }
]);

print("Datos de prueba insertados.");
print("=================================================");
print("RESUMEN DE LA BASE DE DATOS:");
print("  Usuarios:     " + db.usuarios.countDocuments());
print("  Clanes:       " + db.clanes.countDocuments());
print("  Items:        " + db.items.countDocuments());
print("  Personajes:   " + db.personajes.countDocuments());
print("  Raids:        " + db.raids.countDocuments());
print("  Loot Pool:    " + db.loot_pool.countDocuments());
print("  Historial:    " + db.historial_botin.countDocuments());
print("  Notificaciones:" + db.notificaciones.countDocuments());
print("=================================================");
