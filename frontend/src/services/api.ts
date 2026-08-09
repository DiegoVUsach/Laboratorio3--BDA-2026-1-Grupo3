// src/services/api.ts
const API_URL = '/api';

// --- Interfaces ---
export interface Usuario { idUsuario?: number; nombreUsuario: string; rol: string; }

export interface Personaje {
  idPersonaje: number; idUsuario: number; idClan: number | null;
  nombrePersonaje: string; clase: string; nivel: number; faccion: string;
  rolClan: string; itemLevel: number; puntosDkpActuales: number;
}

export interface AuthResponse { token: string; }
export interface Clan { idClan: number; idLider: number | null; nombreClan: string; faccion?: string | null; }

export interface Raid {
  idRaid: number; idClan: number; nombreRaid: string; fechaRaid: string;
  itemLevelMinimo: number; tanques: number; healers: number; dps: number; estado: string;
}
export interface RaidDTO {
  raid: Raid; cuposTanqueLibres: number; cuposHealerLibres: number; cuposDpsLibres: number;
  idsParticipantes?: number[];
}

export interface InventarioDTO {
  idInventario: number; idPersonaje: number;
  armaduraEquipado: number | null; armaEquipado: number | null; accesorioEquipado: number | null;
  nombreArmadura: string | null; nivelArmadura: number | null;
  nombreArma: string | null; nivelArma: number | null;
  nombreAccesorio: string | null; nivelAccesorio: number | null;
}
export interface InventarioItemDTO {
  idInventario: number; idItem: number; nombreItem: string;
  rareza: string; tipo: string; nivel: number; costoDkp: number;
}
export interface Item {
  idItem: number; nombreItem: string; rareza: string; tipo: string; nivel: number; costoDkp: number;
}
export interface HistorialBotin {
  idEntrega: number; idPersonaje: number; idItem: number; idRaid: number;
  nombreItem: string; nombrePersonaje: string; fechaEntrega: string;
}
export interface RankingEntry {
  id_personaje: number; nombre_personaje: string; nombre_clan: string; faccion: string;
  raids_asistidas: number; contribucion_dkp: number; item_level: number;
}
export interface Notificacion {
  id_notificacion: number; tipo: string; mensaje: string; leida: boolean; fecha: string;
}

// --- Motor de peticiones ---
async function apiRequest<T>(endpoint: string, method: string = 'GET', body?: any): Promise<T> {
  const token = localStorage.getItem('auth_token');
  const headers: HeadersInit = { 'Content-Type': 'application/json' };
  // No enviar el token JWT en los endpoints de autenticacion,
  // un token viejo/invalido guardado causaria un 403 en el login.
  const isAuthEndpoint = endpoint.startsWith('/auth/');
  if (token && !isAuthEndpoint) headers['Authorization'] = `Bearer ${token}`;
  const response = await fetch(`${API_URL}${endpoint}`, {
    method, headers, body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    if (response.status === 401) localStorage.removeItem('auth_token');
    const errorText = await response.text();
    let msg = errorText;
    try {
      const json = JSON.parse(errorText);
      msg = json.message || json.error || errorText;
    } catch {
      // not json
    }
    if (msg.includes('ERROR:')) {
      const match = msg.match(/ERROR:\s*([\s\S]*?)(Where:|;|$)/i);
      if (match && match[1]) msg = match[1].trim();
    }
    throw new Error(msg || 'Error en el servidor');
  }
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) return response.json();
  return response.text() as unknown as T;
}

// --- Servicios ---
export const authService = {
  async login(username: string, password: string) {
    const data = await apiRequest<AuthResponse>('/auth/login', 'POST', { username, password });
    localStorage.setItem('auth_token', data.token);
    return data;
  },
  register(data: { nombreUsuario: string; password: string }) {
    return apiRequest<string>('/auth/register', 'POST', data);
  },
  getMe() { return apiRequest<Usuario>('/usuarios/me'); },
};

export const personajeService = {
  getMisPersonajes() { return apiRequest<Personaje[]>('/personajes/mis-personajes'); },
  crearPersonaje(personaje: Partial<Personaje>) { return apiRequest<string>('/personajes', 'POST', personaje); },
  getById(id: number) { return apiRequest<Personaje>(`/personajes/${id}`); },
  asignarRol(data: { idEjecutor: number; idObjetivo: number; nuevoRol: string }) {
    return apiRequest<string>('/personajes/asignar-rol', 'PUT', data);
  },
};

export const clanService = {
  getAll() { return apiRequest<Clan[]>('/clanes'); },
  getById(id: number) { return apiRequest<Clan>(`/clanes/${id}`); },
  getMiembros(idClan: number) { return apiRequest<Personaje[]>(`/clanes/${idClan}/miembros`); },
  // Ciclo de vida
  fundar(idPersonaje: number, nombreClan: string) {
    return apiRequest<string>('/clanes/fundar', 'POST', { idPersonaje, nombreClan });
  },
  unirse(idPersonaje: number, idClan: number) {
    return apiRequest<string>('/clanes/unirse', 'POST', { idPersonaje, idClan });
  },
  salir(idPersonaje: number) {
    return apiRequest<string>('/clanes/salir', 'POST', { idPersonaje });
  },
  update(id: number, data: { nombreClan: string }) {
    return apiRequest<string>(`/clanes/${id}`, 'PUT', data);
  },
  disolver(id: number) { return apiRequest<string>(`/clanes/${id}`, 'DELETE'); },
  transferLeadership(idClan: number, idCurrentLeader: number, idNewLeader: number) {
    return apiRequest<string>(`/clanes/${idClan}/transfer-leadership`, 'PUT', { idCurrentLeader, idNewLeader });
  },
};

export const raidService = {
  getCalendario(idClan?: number) { return apiRequest<RaidDTO[]>('/raids/calendario' + (idClan ? ('?idClan=' + idClan) : '')); },
  inscribirse(idRaid: number, idPersonaje: number, rolEnRaid: string) {
    return apiRequest<string>('/raids/inscribirse', 'POST', { idRaid, idPersonaje, rolEnRaid });
  },
  create(raidData: any) { return apiRequest<string>('/raids', 'POST', raidData); },
  update(idRaid: number, raidData: any) { return apiRequest<string>(`/raids/${idRaid}`, 'PUT', raidData); },
  remove(idRaid: number) { return apiRequest<string>(`/raids/${idRaid}`, 'DELETE'); },
  invitarRaiders(idRaid: number, idClan: number) {
    return apiRequest<string>('/raids/invitar-raiders', 'POST', { idRaid, idClan });
  },
  finalizar(idRaid: number) {
    return apiRequest<string>(`/raids/${idRaid}/finalizar`, 'POST');
  },
  desinscribirse(idRaid: number, idPersonaje: number) {
    return apiRequest<string>(`/raids/desinscribirse?idRaid=${idRaid}&idPersonaje=${idPersonaje}`, 'DELETE');
  },
};

export const inventarioService = {
  getByPersonaje(idPersonaje: number) { return apiRequest<InventarioDTO>(`/inventarios/por-personaje/${idPersonaje}`); },
  getItemsByPersonaje(idPersonaje: number) { return apiRequest<InventarioItemDTO[]>(`/inventarios/por-personaje/${idPersonaje}/items`); },
  update(idInventario: number, data: any) { return apiRequest<string>(`/inventarios/id/${idInventario}`, 'PUT', data); },
  // Equipar / desequipar: se envia el inventario completo con los 3 slots
  equipar(inv: { idInventario: number; idPersonaje: number; armaduraEquipado: number | null; armaEquipado: number | null; accesorioEquipado: number | null }) {
    return apiRequest<string>(`/inventarios/id/${inv.idInventario}`, 'PUT', inv);
  },
};

export const itemService = {
  getAll() { return apiRequest<Item[]>('/items?page=0&size=100'); },
  create(data: any) { return apiRequest<string>('/items', 'POST', data); },
  update(id: number, data: any) { return apiRequest<string>(`/items/${id}`, 'PUT', data); },
  remove(id: number) { return apiRequest<string>(`/items/${id}`, 'DELETE'); },
  getHistorial(idPersonaje: number) { return apiRequest<HistorialBotin[]>(`/items/historial/${idPersonaje}`); },
  getRanking() { return apiRequest<RankingEntry[]>('/items/ranking'); },
  // Pool de canje del jugador y canje (v3)
  miPool(idPersonaje: number) {
    return apiRequest<any[]>(`/items/mi-pool/${idPersonaje}`);
  },
  canjear(idPool: number, idPersonaje: number) {
    return apiRequest<string>('/items/canjear', 'POST', { idPool, idPersonaje });
  },
  refrescarRanking() { return apiRequest<string>('/items/ranking/refrescar', 'POST'); },
};

export const inscripcionService = {
  getByRaid(idRaid: number) { return apiRequest<any[]>(`/inscripciones/por-raid/${idRaid}`); },
  mias(idPersonaje: number) { return apiRequest<any[]>(`/inscripciones/mias?idPersonaje=${idPersonaje}`); },
  confirmarAsistencia(idInscripcion: number, idEjecutor: number) {
    return apiRequest<string>(`/inscripciones/${idInscripcion}/confirmar`, 'PUT', { idEjecutor });
  },
};

export const notificacionService = {
  mias(idPersonaje: number) { return apiRequest<Notificacion[]>(`/notificaciones/mias?idPersonaje=${idPersonaje}`); },
  noLeidas(idPersonaje: number) { return apiRequest<{ count: number }>(`/notificaciones/no-leidas?idPersonaje=${idPersonaje}`); },
  marcarLeida(id: number) { return apiRequest<string>(`/notificaciones/${id}/leida`, 'PUT'); },
  marcarTodas(idPersonaje: number) {
    return apiRequest<string>(`/notificaciones/marcar-todas?idPersonaje=${idPersonaje}`, 'PUT');
  },
};

