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
    // Esperar a que se establezca el primario
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

// Conectar a la base de datos de la aplicacion
db = db.getSiblingDB("laboratorio1");

// Crear colecciones con validadores Schema Validation
print("Creando colecciones con validadores...");

// 1. Usuarios
db.createCollection("usuarios", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["idUsuario", "nombreUsuario", "password", "rol"],
            properties: {
                idUsuario: { bsonType: "int" },
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
            required: ["idClan", "nombreClan"],
            properties: {
                idClan: { bsonType: "int" },
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
            required: ["idPersonaje", "idUsuario", "nombrePersonaje", "clase", "faccion", "rolClan", "nivel", "itemLevel", "puntosDkpActuales", "caido"],
            properties: {
                idPersonaje: { bsonType: "int" },
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
                        items: {
                            bsonType: "array",
                            items: { bsonType: "int" }
                        }
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
            required: ["idItem", "nombreItem", "rareza", "tipo", "nivel", "costoDkp"],
            properties: {
                idItem: { bsonType: "int" },
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
            required: ["idRaid", "idClan", "nombreRaid", "fechaRaid", "itemLevelMinimo", "tanques", "healers", "dps", "estado"],
            properties: {
                idRaid: { bsonType: "int" },
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

// 6. Loot Pool (con validaciones de negocio especificas del Lab 3)
db.createCollection("loot_pool", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["idPool", "idPersonaje", "idItem", "idRaid", "participoRaid", "personajeCaido", "canjeado", "fecha"],
            properties: {
                idPool: { bsonType: "int" },
                idPersonaje: { bsonType: "int" },
                idItem: { bsonType: "int" },
                idRaid: { bsonType: "int" },
                participoRaid: {
                    bsonType: "bool",
                    description: "El personaje debe haber participado en la raid",
                    enum: [true]
                },
                personajeCaido: {
                    bsonType: "bool",
                    description: "El personaje no debe estar caido",
                    enum: [false]
                },
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
            required: ["idEntrega", "idPersonaje", "idItem", "idRaid", "fechaEntrega", "nombreItem", "nombrePersonaje"],
            properties: {
                idEntrega: { bsonType: "int" },
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
            required: ["idNotificacion", "idPersonaje", "tipo", "mensaje", "leida", "fecha"],
            properties: {
                idNotificacion: { bsonType: "int" },
                idPersonaje: { bsonType: "int" },
                tipo: { bsonType: "string" },
                mensaje: { bsonType: "string" },
                leida: { bsonType: "bool" },
                fecha: { bsonType: "date" }
            }
        }
    }
});

// 9. Secuencias para auto-increment
db.createCollection("database_sequences");

// 10. Clanes mejor rankeados (Coleccion materializada)
db.createCollection("clan_rankings");

// ============================================================
// ESTRATEGIA DE INDICES (Tarea 5)
// ============================================================
print("Creando indices...");

// compuesto para filtrar rápidamente personajes por clase y rol dentro de un clan (requisito de indices)
db.personajes.createIndex({ idClan: 1, clase: 1, rolClan: 1 });

// unico sobre el nombre del personaje (requisito de indices)
db.personajes.createIndex({ nombrePersonaje: 1 }, { unique: true });

// unico sobre nombre de usuario y clan
db.usuarios.createIndex({ nombreUsuario: 1 }, { unique: true });
db.clanes.createIndex({ nombreClan: 1 }, { unique: true });

// TTL index para auto-expirar notificaciones y loot_pool viejos despues de 30 dias (2592000 segundos)
db.notificaciones.createIndex({ fecha: 1 }, { expireAfterSeconds: 2592000 });
db.loot_pool.createIndex({ fecha: 1 }, { expireAfterSeconds: 2592000 });

// indices compuestos frecuentes
db.raids.createIndex({ idClan: 1, estado: 1 });
db.loot_pool.createIndex({ idPersonaje: 1, canjeado: 1 });

print("Indices creados.");

// ============================================================
// DATOS DE PRUEBA (SEED)
// ============================================================
print("Insertando datos de prueba...");

// Inicializar contadores
db.database_sequences.insertMany([
    { _id: "usuarioId", seq: 3 },
    { _id: "clanId", seq: 3 },
    { _id: "personajeId", seq: 4 },
    { _id: "itemId", seq: 5 },
    { _id: "raidId", seq: 2 },
    { _id: "lootPoolId", seq: 0 },
    { _id: "historialBotinId", seq: 0 },
    { _id: "notificacionId", seq: 0 }
]);

// 1. Usuarios
db.usuarios.insertMany([
    { idUsuario: 1, nombreUsuario: "admin", password: "$2a$10$tP6P6c6G/n0N1Z2.2E2.2.p.q.r.s.t.u.v.w.x.y.z", rol: "ADMIN" }, // pass: 123456
    { idUsuario: 2, nombreUsuario: "martin", password: "$2a$10$tP6P6c6G/n0N1Z2.2E2.2.p.q.r.s.t.u.v.w.x.y.z", rol: "USER" },
    { idUsuario: 3, nombreUsuario: "seba", password: "$2a$10$tP6P6c6G/n0N1Z2.2E2.2.p.q.r.s.t.u.v.w.x.y.z", rol: "USER" }
]);

// 2. Clanes
db.clanes.insertMany([
    { idClan: 1, nombreClan: "Los Heraldos de Aurora", idLider: 1, faccion: "Los Primordiales de la Luz", auditoriaLiderazgo: [] },
    { idClan: 2, nombreClan: "Los Hijos del Gris", idLider: 2, faccion: "Los Hijos del Gris", auditoriaLiderazgo: [] },
    { idClan: 3, nombreClan: "Los Marcados por el Abismo", idLider: 3, faccion: "Los Marcados por el Abismo", auditoriaLiderazgo: [] }
]);

// 3. Items
db.items.insertMany([
    { idItem: 1, nombreItem: "Espada de Acero Sagrado", rareza: "Rara", tipo: "ARMA", nivel: 60, costoDkp: 50 },
    { idItem: 2, nombreItem: "Escudo del Guardian Celestino", rareza: "Epica", tipo: "ARMADURA", nivel: 70, costoDkp: 100 },
    { idItem: 3, nombreItem: "Anillo del Viento Gris", rareza: "Poco Comun", tipo: "ACCESORIO", nivel: 45, costoDkp: 20 },
    { idItem: 4, nombreItem: "Amuleto de las Profundidades", rareza: "Legendaria", tipo: "ACCESORIO", nivel: 80, costoDkp: 250 },
    { idItem: 5, nombreItem: "Baston de Sanacion Divina", rareza: "Rara", tipo: "ARMA", nivel: 60, costoDkp: 60 }
]);

// 4. Personajes
db.personajes.insertMany([
    {
        idPersonaje: 1, idUsuario: 1, idClan: 1, nombrePersonaje: "Arthon", clase: "Paladin",
        faccion: "Los Primordiales de la Luz", rolClan: "Guild Master", nivel: 60, itemLevel: 230, puntosDkpActuales: 500, caido: false,
        inventario: { idInventario: 1, armaduraEquipado: 2, armaEquipado: 1, accesorioEquipado: 3, items: [3, 4] }
    },
    {
        idPersonaje: 2, idUsuario: 2, idClan: 1, nombrePersonaje: "Elyndra", clase: "Sacerdote",
        faccion: "Los Primordiales de la Luz", rolClan: "Raider", nivel: 58, itemLevel: 120, puntosDkpActuales: 150, caido: false,
        inventario: { idInventario: 2, armaduraEquipado: null, armaEquipado: 5, accesorioEquipado: null, items: [1] }
    },
    {
        idPersonaje: 3, idUsuario: 3, idClan: 2, nombrePersonaje: "Dorn", clase: "Guerrero",
        faccion: "Los Hijos del Gris", rolClan: "Guild Master", nivel: 45, itemLevel: 90, puntosDkpActuales: 80, caido: false,
        inventario: { idInventario: 3, armaduraEquipado: null, armaEquipado: null, accesorioEquipado: null, items: [] }
    },
    {
        idPersonaje: 4, idUsuario: 2, idClan: null, nombrePersonaje: "Kiiro", clase: "Paladin",
        faccion: "Los Hijos del Gris", rolClan: "Member", nivel: 1, itemLevel: 0, puntosDkpActuales: 0, caido: true, // caido por defecto para pruebas
        inventario: { idInventario: 4, armaduraEquipado: null, armaEquipado: null, accesorioEquipado: null, items: [] }
    }
]);

// 5. Raids
db.raids.insertMany([
    {
        idRaid: 1, idClan: 1, nombreRaid: "Asalto a la Ciudadela de Ceniza", fechaRaid: new Date(),
        itemLevelMinimo: 100, tanques: 1, healers: 1, dps: 3, estado: "PROGRAMADA",
        inscripciones: [
            { idInscripcion: 1, idPersonaje: 1, rolEnRaid: "Tanque", confirmado: true },
            { idInscripcion: 2, idPersonaje: 2, rolEnRaid: "Healer", confirmado: true }
        ]
    },
    {
        idRaid: 2, idClan: 2, nombreRaid: "El Despertar del Dragon Sombrio", fechaRaid: new Date(),
        itemLevelMinimo: 80, tanques: 1, healers: 1, dps: 2, estado: "COMPLETADA",
        inscripciones: [
            { idInscripcion: 3, idPersonaje: 3, rolEnRaid: "Dps", confirmado: true }
        ]
    }
]);

print("Datos de prueba insertados.");
