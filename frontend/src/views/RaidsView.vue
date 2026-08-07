<script setup lang="ts">
/**
 * Pestaña RAIDS (privada del clan del personaje).
 * Incluye el mapa de las raids del clan junto a la posicion del jugador,
 * la confirmacion de invitaciones, y el detalle del encuentro con el radio
 * de botin, el tanque lider y los healers de su region.
 */
import { ref, onMounted, computed } from 'vue';
import { raidService, inscripcionService, type RaidDTO } from '../services/api';
import { fechaCL, fechaHoraCL, clAIso } from '../utils/format';
import { fechaCL, fechaHoraCL, clAIso } from '../utils/format';

const props = defineProps<{ character: any; token: string }>();
const emit = defineEmits<{ (e: 'refrescar'): void }>();

const raids = ref<RaidDTO[]>([]);
const misInscripciones = ref<any[]>([]);

const loading = ref(true);
const error = ref('');
const success = ref('');
const filtroEstado = ref<'todas' | 'pendientes' | 'completadas'>('todas');

const esGM = computed(() => props.character?.rolClan === 'Guild Master');
const tieneClan = computed(() => props.character?.idClan !== null && props.character?.idClan !== undefined);
const raidsFiltradas = computed(() => {
  if (filtroEstado.value === 'pendientes') return raids.value.filter(r => r.raid.estado !== 'COMPLETADA');
  if (filtroEstado.value === 'completadas') return raids.value.filter(r => r.raid.estado === 'COMPLETADA');
  return raids.value;
});

onMounted(cargarTodo);

async function cargarTodo() {
  loading.value = true; error.value = '';
  try {
    if (!tieneClan.value) { raids.value = []; return; }
    raids.value = await raidService.getCalendario(props.character.idClan);
    misInscripciones.value = await inscripcionService.mias(props.character.idPersonaje).catch(() => []);

  } catch (e: any) { error.value = e.message; }
  finally { loading.value = false; }
}

function inscripcionDe(idRaid: number) {
  return misInscripciones.value.find(i => i.id_raid === idRaid) || null;
}


// ---------- Crear raid (GM) ----------
const showCrear = ref(false);
const crearError = ref('');
const newRaid = ref<any>({ nombreRaid: '', fechaRaid: '', itemLevelMinimo: 0, tanques: 2, healers: 2, dps: 5 });

function abrirCrear() {
  crearError.value = '';
  // Fecha minima = ahora (formato yyyy-MM-ddThh:mm para datetime-local)
  const hoy = new Date();
  const yyyy = hoy.getFullYear();
  const mm = String(hoy.getMonth() + 1).padStart(2, '0');
  const dd = String(hoy.getDate()).padStart(2, '0');
  const hh = String(hoy.getHours()).padStart(2, '0');
  const min = String(hoy.getMinutes()).padStart(2, '0');
  const hoyIso = `${yyyy}-${mm}-${dd}T${hh}:${min}`;
  newRaid.value = { nombreRaid: '', fechaRaid: hoyIso, itemLevelMinimo: 0, tanques: 2, healers: 2, dps: 5, minFecha: hoyIso };
  showCrear.value = true;
}

async function crearRaid() {
  crearError.value = '';
  if (!newRaid.value.nombreRaid) { crearError.value = 'Escribe el nombre de la raid'; return; }
  if (!newRaid.value.fechaRaid) { crearError.value = 'Selecciona la fecha y hora de la raid'; return; }
  const iso = newRaid.value.fechaRaid; // datetime-local format is YYYY-MM-DDThh:mm

  try {
    const nombre = newRaid.value.nombreRaid;
    await raidService.create({
      idClan: props.character.idClan,
      nombreRaid: nombre,
      // Si el navegador no envia los segundos, los agregamos para que el backend lo parseé bien como LocalDateTime
      fechaRaid: iso.length === 16 ? iso + ':00' : iso,
      itemLevelMinimo: newRaid.value.itemLevelMinimo,
      tanques: newRaid.value.tanques,
      healers: newRaid.value.healers,
      dps: newRaid.value.dps,
    });
    success.value = 'Raid creada';
    showCrear.value = false;
    await cargarTodo();
  } catch (e: any) { crearError.value = e.message; }
}

// ---------- Inscribirse (posicion fija: la del personaje) ----------
const showInscribirse = ref(false);
const modalError = ref('');
const raidInscripcion = ref<RaidDTO | null>(null);
const rolEnRaid = ref('');


async function abrirInscribirse(rd: RaidDTO) {
  raidInscripcion.value = rd;
  rolEnRaid.value = ''; modalError.value = '';
  bossInscripcion.value = null;
  showInscribirse.value = true;
  try {
    const b = await geoService.boss(rd.raid.idRaid);
    if (b && b.geometry) bossInscripcion.value = { x: b.geometry.coordinates[0], y: b.geometry.coordinates[1] };
  } catch { /* la raid puede no tener jefe */ }
}

async function inscribirse() {
  modalError.value = '';
  if (!rolEnRaid.value || !raidInscripcion.value) { modalError.value = 'Selecciona un rol'; return; }
  try {
    const idRaid = raidInscripcion.value.raid.idRaid;
    await raidService.inscribirse(idRaid, props.character.idPersonaje, rolEnRaid.value);
    if (miPosicion.value) {
      try { await geoService.setPosicionEnRaid(idRaid, props.character.idPersonaje, miPosicion.value.x, miPosicion.value.y); } catch { /* opcional */ }
    }
    success.value = 'Inscripcion enviada';
    showInscribirse.value = false;
    await cargarTodo();
  } catch (e: any) { modalError.value = e.message; }
}

async function desinscribirse(idRaid: number) {
  error.value = '';
  try {
    await raidService.desinscribirse(idRaid, props.character.idPersonaje);
    success.value = 'Saliste de la raid';
    await cargarTodo();
  } catch (e: any) { error.value = e.message; }
}

// Confirmar asistencia desde la propia lista de raids
async function confirmarDesdeLista(idRaid: number) {
  const ins = inscripcionDe(idRaid);
  if (!ins) return;
  error.value = '';
  try {
    await inscripcionService.confirmarAsistencia(ins.id_inscripcion, props.character.idPersonaje);
    success.value = 'Asistencia confirmada';
    await cargarTodo();
    emit('refrescar');
  } catch (e: any) { error.value = e.message; }
}

// ---------- Detalle del encuentro ----------
const showDetalle = ref(false);
const raidDetalle = ref<RaidDTO | null>(null);
async function verDetalle(rd: RaidDTO) {
  raidDetalle.value = rd;
  detalleError.value = '';
  invitados.value = [];
  showDetalle.value = true;
  try { invitados.value = await inscripcionService.getByRaid(rd.raid.idRaid); } catch { /* vacio */ }
}


// ---------- Finalizar ----------
const showFinalizar = ref(false);
const raidAFinalizar = ref<RaidDTO | null>(null);
const excluidos = ref<any[]>([]);
const finalizarError = ref('');

async function abrirFinalizar(rd: RaidDTO) {
  raidAFinalizar.value = rd;
  finalizarError.value = '';
  showFinalizar.value = true;
}

async function confirmarFinalizar() {
  if (!raidAFinalizar.value) return;
  finalizarError.value = '';
  try {
    await raidService.finalizar(raidAFinalizar.value.raid.idRaid);
    success.value = 'Raid finalizada. El botin se repartio a los asistentes en rango del jefe.';
    showFinalizar.value = false;
    await cargarTodo();
    emit('refrescar');
  } catch (e: any) { finalizarError.value = e.message; }
}

async function invitarRaiders(idRaid: number) {
  error.value = ''; success.value = '';
  try {
    await raidService.invitarRaiders(idRaid, props.character.idClan);
    success.value = 'Invitaciones enviadas: los raiders recibieron una notificacion.';
    await cargarTodo();
  } catch (e: any) { error.value = e.message; }
}

async function eliminarRaid(idRaid: number) {
  if (!confirm('Eliminar esta raid?')) return;
  error.value = '';
  try { await raidService.remove(idRaid); success.value = 'Raid eliminada'; await cargarTodo(); }
  catch (e: any) { error.value = e.message; }
}

function estadoClass(estado: string) {
  if (estado === 'COMPLETADA') return 'estado-done';
  if (estado === 'PROGRAMADA') return 'estado-active';
  return 'estado-other';
}
</script>

<template>
  <div class="raids-view">
    <div class="view-header">
      <div>
        <h1>Raids</h1>
        <p class="view-subtitle">Solo las raids de tu clan</p>
      </div>
      <div class="header-actions">
        <button v-if="esGM && tieneClan" class="btn-primary" @click="abrirCrear">Programar raid</button>
      </div>
    </div>

    <div v-if="error" class="alert alert-error" @click="error = ''">{{ error }}</div>
    <div v-if="success" class="alert alert-success" @click="success = ''">{{ success }}</div>

    <div v-if="!tieneClan" class="panel empty-state">
      <h3>Aun no perteneces a un clan</h3>
      <p>Unete o funda un clan desde la pestaña "Mi Clan" para ver y participar en raids.</p>
    </div>

    <template v-else>
      <!-- Mapa de las raids del clan + posicion del jugador -->
      <div class="filtro-raids">
        <button :class="{ active: filtroEstado === 'todas' }" @click="filtroEstado = 'todas'">Todas</button>
        <button :class="{ active: filtroEstado === 'pendientes' }" @click="filtroEstado = 'pendientes'">Programadas</button>
        <button :class="{ active: filtroEstado === 'completadas' }" @click="filtroEstado = 'completadas'">Completadas</button>
      </div>

      <div class="panel">
        <h3 class="section-title">Calendario de Raids</h3>
      </div>

      <div v-if="loading" class="loader">Cargando raids...</div>
      <div v-else class="raids-grid">
        <div v-for="rd in raidsFiltradas" :key="rd.raid.idRaid" class="raid-card">
          <div class="raid-header">
            <h3>{{ rd.raid.nombreRaid }}</h3>
            <span class="estado-badge" :class="estadoClass(rd.raid.estado)">{{ rd.raid.estado }}</span>
          </div>
          <div class="raid-meta">
            <div class="meta-item"><span class="meta-label">Fecha</span><span>{{ fechaCL(rd.raid.fechaRaid) }}</span></div>
            <div class="meta-item"><span class="meta-label">iLvl minimo</span><span>{{ rd.raid.itemLevelMinimo }}</span></div>
          </div>
          <div class="raid-slots">
            <div class="slot" :class="{ full: rd.cuposTanqueLibres <= 0 }"><span class="slot-label">Tanques</span><span>{{ rd.cuposTanqueLibres }} libres</span></div>
            <div class="slot" :class="{ full: rd.cuposHealerLibres <= 0 }"><span class="slot-label">Healers</span><span>{{ rd.cuposHealerLibres }} libres</span></div>
            <div class="slot" :class="{ full: rd.cuposDpsLibres <= 0 }"><span class="slot-label">DPS</span><span>{{ rd.cuposDpsLibres }} libres</span></div>
          </div>

          <!-- Invitacion pendiente: se confirma aqui mismo -->
          <div v-if="inscripcionDe(rd.raid.idRaid) && !inscripcionDe(rd.raid.idRaid).confirmado && rd.raid.estado === 'PROGRAMADA'" class="invitacion">
            <span>Estas invitado como {{ inscripcionDe(rd.raid.idRaid).rol_en_raid }}. Confirma tu asistencia.</span>
            <button class="btn-sm-primary" @click="confirmarDesdeLista(rd.raid.idRaid)">Confirmar asistencia</button>
          </div>
          <div v-else-if="inscripcionDe(rd.raid.idRaid) && inscripcionDe(rd.raid.idRaid).confirmado" class="confirmado">
            Asistencia confirmada como {{ inscripcionDe(rd.raid.idRaid).rol_en_raid }}
          </div>

          <div class="raid-actions">
            <button class="btn-sm-secondary" @click="verDetalle(rd)">Ver detalle</button>
            <button v-if="rd.raid.estado === 'PROGRAMADA' && !inscripcionDe(rd.raid.idRaid)" class="btn-sm-primary" @click="abrirInscribirse(rd)">Inscribirse</button>
            <button v-if="rd.raid.estado === 'PROGRAMADA' && inscripcionDe(rd.raid.idRaid)" class="btn-sm-danger" @click="desinscribirse(rd.raid.idRaid)">Salir</button>
            <button v-if="esGM && rd.raid.estado === 'PROGRAMADA'" class="btn-sm-secondary" @click="invitarRaiders(rd.raid.idRaid)">Invitar raiders</button>
            <button v-if="esGM && rd.raid.estado === 'PROGRAMADA'" class="btn-sm-primary" @click="abrirFinalizar(rd)">Finalizar</button>
            <button v-if="esGM && rd.raid.estado === 'PROGRAMADA'" class="btn-sm-danger" @click="eliminarRaid(rd.raid.idRaid)">Eliminar</button>
          </div>
        </div>

        <div v-if="raidsFiltradas.length === 0" class="empty-state panel">
          <h3>No hay raids para este filtro</h3>
          <p>Cuando tu Guild Master programe una raid, aparecera aqui.</p>
        </div>
      </div>
    </template>

    <!-- Modal: crear raid -->
    <div v-if="showCrear" class="modal-overlay" @click.self="showCrear = false">
      <div class="modal modal-lg">
        <h3>Programar nueva raid</h3>
        <div v-if="crearError" class="alert alert-error">{{ crearError }}</div>
        <div class="form-grid">
          <div><label>Nombre</label><input v-model="newRaid.nombreRaid" type="text" placeholder="Nombre de la raid"></div>
          <div><label>Fecha de la raid</label><input v-model="newRaid.fechaRaid" type="datetime-local" :min="newRaid.minFecha"></div>
          <div><label>iLvl minimo</label><input v-model.number="newRaid.itemLevelMinimo" type="number" min="0"></div>
          <div><label>Tanques</label><input v-model.number="newRaid.tanques" type="number" min="0"></div>
          <div><label>Healers</label><input v-model.number="newRaid.healers" type="number" min="0"></div>
          <div><label>DPS</label><input v-model.number="newRaid.dps" type="number" min="0"></div>
        </div>
        <label>Lugar de la raid</label>
        <p class="hint-mini">Haz clic en el mapa para marcar donde ocurrirá el encuentro. El radio rojo indica la zona donde los personajes deberán estar al finalizar la raid para recibir botín.</p>
        <WorldMap :markers="markersCrear" :pickable="true" height="440px" @pick="(p) => { newRaid.x = p.x; newRaid.y = p.y; }" />
        <div class="modal-actions">
          <button class="btn-cancel" @click="showCrear = false">Cancelar</button>
          <button class="btn-primary" @click="crearRaid">Crear raid</button>
        </div>
      </div>
    </div>

    <!-- Modal: inscribirse -->
    <div v-if="showInscribirse" class="modal-overlay" @click.self="showInscribirse = false">
      <div class="modal modal-lg">
        <h3>Inscribirse en {{ raidInscripcion?.raid.nombreRaid }}</h3>
        <div v-if="modalError" class="alert alert-error">{{ modalError }}</div>
        <label>Rol en la raid</label>
        <div class="role-options">
          <button v-for="r in rolesRaid" :key="r" :class="{ selected: rolEnRaid === r }" @click="rolEnRaid = r">{{ r }}</button>
        </div>
        <label>Tu posicion frente al jefe</label>
        <p class="hint-mini">
          Tu posicion en el mundo se fijo al crear el personaje y no cambia aqui.
          <span v-if="distanciaAlBoss !== null">
            Estas a <strong>{{ distanciaAlBoss }} metros</strong> del jefe:
            <strong :class="distanciaAlBoss <= 50 ? 'ok' : 'ko'">
              {{ distanciaAlBoss <= 50 ? 'dentro del radio de botin' : 'fuera del radio de botin' }}
            </strong>.
          </span>
        </p>
        <WorldMap :markers="markersInscripcion" height="440px" />
        <div class="modal-actions">
          <button class="btn-cancel" @click="showInscribirse = false">Cancelar</button>
          <button class="btn-primary" @click="inscribirse">Inscribirme</button>
        </div>
      </div>
    </div>

    <!-- Modal: detalle -->
    <div v-if="showDetalle" class="modal-overlay" @click.self="showDetalle = false">
      <div class="modal modal-lg">
        <h3>{{ raidDetalle?.raid.nombreRaid }}</h3>
        <div v-if="detalleError" class="alert alert-error">{{ detalleError }}</div>
        <div class="detail-grid">
          <div class="detail-item"><span class="detail-label">Estado</span><span class="estado-badge" :class="estadoClass(raidDetalle?.raid.estado || '')">{{ raidDetalle?.raid.estado }}</span></div>
          <div class="detail-item"><span class="detail-label">Fecha</span><span>{{ fechaCL(raidDetalle?.raid.fechaRaid) }}</span></div>
          <div class="detail-item"><span class="detail-label">iLvl minimo</span><span>{{ raidDetalle?.raid.itemLevelMinimo }}</span></div>
          <div class="detail-item"><span class="detail-label">Tanque lider</span><span>{{ tanqueLider ? tanqueLider.nombre_personaje + ' (iLvl ' + tanqueLider.item_level + ')' : 'Sin tanque' }}</span></div>
        </div>

        <h4 class="sub">Invitados</h4>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>Personaje</th><th>Clase</th><th>Rol</th><th>iLvl</th><th>Asistencia</th></tr></thead>
            <tbody>
              <tr v-for="ins in invitados" :key="ins.id_inscripcion"
                  :class="{ yo: ins.id_personaje === character.idPersonaje }">
                <td class="player-name">
                  {{ ins.nombre_personaje }}
                  <span v-if="ins.id_personaje === character.idPersonaje" class="tag-yo">tu personaje</span>
                </td>
                <td>{{ ins.clase }}</td>
                <td>{{ ins.rol_en_raid }}</td>
                <td>{{ ins.item_level }}</td>
                <td :class="ins.confirmado ? 'ok' : 'pend'">{{ ins.confirmado ? 'Confirmada' : 'Pendiente' }}</td>
              </tr>
              <tr v-if="invitados.length === 0"><td colspan="5" class="empty-cell">Sin invitados</td></tr>
            </tbody>
          </table>
        </div>

        <h4 class="sub">Mapa del encuentro</h4>
        <p class="hint-mini">
          El circulo rojo marca el radio de botin (50 metros) alrededor del jefe. Se muestran los asistentes
          confirmados: verde dentro del radio, rojo fuera. El tanque lider (el tanque de mayor iLvl) aparece en morado.
          <span v-if="raidDetalle?.raid.estado === 'COMPLETADA'">
            Esta raid ya se jugo: las posiciones son las que quedaron registradas durante el encuentro.
          </span>
          <span v-else>Las posiciones son las que cada jugador tiene ahora en el mundo.</span>
        </p>
        <WorldMap :markers="markersEncuentro" height="480px" />

        <h4 class="sub">Formacion de grupo: healers en la region del tanque lider</h4>
        <p class="hint-mini">
          <span v-if="tanqueLider">
            Tanque lider: <strong>{{ tanqueLider.nombre_personaje }}</strong> (iLvl {{ tanqueLider.item_level }}),
            en la region <strong>{{ tanqueLider.region || 'tierras salvajes' }}</strong>.
          </span>
          <span v-else>Esta raid no tiene un tanque inscrito con posicion registrada.</span>
        </p>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>Healer</th><th>Region</th><th>Estado</th></tr></thead>
            <tbody>
              <tr v-for="h in healersRegion" :key="h.id_personaje">
                <td class="player-name">{{ h.nombre_personaje }}</td>
                <td>{{ h.region }}</td>
                <td class="ok">Misma region que el tanque lider</td>
              </tr>
              <tr v-if="healersRegion.length === 0"><td colspan="3" class="empty-cell">Ningun healer confirmado comparte region con el tanque lider</td></tr>
            </tbody>
          </table>
        </div>

        <div class="modal-actions"><button class="btn-cancel" @click="showDetalle = false">Cerrar</button></div>
      </div>
    </div>

    <!-- Modal: finalizar -->
    <div v-if="showFinalizar" class="modal-overlay" @click.self="showFinalizar = false">
      <div class="modal modal-lg">
        <h3>Finalizar: {{ raidAFinalizar?.raid.nombreRaid }}</h3>
        <p class="hint-mini">Cada asistente dentro de 50 metros del jefe recibe un item al azar en su pool de canje. Los que quedan fuera no reciben botin.</p>
        <div v-if="finalizarError" class="alert alert-error">{{ finalizarError }}</div>
        <div class="prox-cols">
          <div>
            <h4 class="prox-ok">Reciben botin ({{ elegibles.length }})</h4>
            <ul class="prox-list">
              <li v-for="e in elegibles" :key="e.id_personaje">{{ e.nombre_personaje }} <span class="prox-dist">a {{ e.distancia }} m</span></li>
              <li v-if="elegibles.length === 0" class="prox-empty">Sin posiciones registradas: se repartira a todos los confirmados.</li>
            </ul>
          </div>
          <div>
            <h4 class="prox-no">Quedan fuera ({{ excluidos.length }})</h4>
            <ul class="prox-list">
              <li v-for="x in excluidos" :key="x.id_personaje">{{ x.nombre_personaje }} <span class="prox-dist">a {{ x.distancia }} m</span></li>
              <li v-if="excluidos.length === 0" class="prox-empty">Nadie fuera de rango.</li>
            </ul>
          </div>
        </div>
        <div class="modal-actions">
          <button class="btn-cancel" @click="showFinalizar = false">Cancelar</button>
          <button class="btn-primary" @click="confirmarFinalizar">Finalizar y repartir</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.raids-view { display: flex; flex-direction: column; gap: 18px; padding: 24px; }
.view-header { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 12px; }
.view-subtitle { color: var(--text-dim); font-size: 14px; }
.alert { padding: 10px 14px; border-radius: 8px; cursor: pointer; }
.alert-error { background: #fdecea; color: var(--red); }
.alert-success { background: #e9f7ec; color: var(--green); }
.loader { text-align: center; color: var(--text-dim); padding: 30px; }
.panel { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 18px; }
.section-title { margin-bottom: 8px; }
.hint-mini { font-size: 12px; color: var(--text-dim); margin-bottom: 10px; }

.filtro-raids { display: flex; gap: 6px; }
.filtro-raids button { background: transparent; border: 1px solid var(--border); color: var(--text-dim); padding: 6px 14px; border-radius: 20px; cursor: pointer; font-size: 13px; }
.filtro-raids button.active { background: var(--gold); color: #fff; border-color: var(--gold); }

.raids-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 16px; }
.raid-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 16px; }
.raid-header { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 10px; }
.raid-header h3 { font-size: 17px; }
.estado-badge { padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; }
.estado-active { background: #e9f7ec; color: var(--green); }
.estado-done { background: #eceff1; color: #5f6b7a; }
.estado-other { background: #fff8e1; color: #a9791f; }
.raid-meta { display: flex; gap: 20px; margin-bottom: 10px; }
.meta-item { display: flex; flex-direction: column; font-size: 14px; }
.meta-label { font-size: 10px; text-transform: uppercase; color: var(--text-dim); letter-spacing: 0.5px; }
.raid-slots { display: flex; gap: 8px; margin-bottom: 12px; }
.slot { flex: 1; background: var(--bg-main); border-radius: 8px; padding: 8px; text-align: center; font-size: 12px; }
.slot.full { opacity: 0.5; }
.slot-label { display: block; font-size: 10px; text-transform: uppercase; color: var(--text-dim); }
.invitacion { display: flex; align-items: center; justify-content: space-between; gap: 10px; background: rgba(169,121,31,0.1); border: 1px solid var(--gold); border-radius: 8px; padding: 10px; margin-bottom: 12px; font-size: 13px; }
.confirmado { background: #e9f7ec; color: var(--green); border-radius: 8px; padding: 8px 10px; margin-bottom: 12px; font-size: 13px; font-weight: 600; }
.raid-actions { display: flex; flex-wrap: wrap; gap: 6px; }

.btn-primary { background: var(--gold); color: #fff; border: none; padding: 10px 16px; border-radius: 8px; cursor: pointer; font-weight: 600; }
.btn-sm-primary { background: var(--gold); color: #fff; border: none; padding: 6px 12px; border-radius: 6px; cursor: pointer; font-size: 13px; }
.btn-sm-secondary { background: transparent; border: 1px solid var(--border); color: var(--text-dim); padding: 6px 12px; border-radius: 6px; cursor: pointer; font-size: 13px; }
.btn-sm-danger { background: transparent; border: 1px solid var(--red); color: var(--red); padding: 6px 12px; border-radius: 6px; cursor: pointer; font-size: 13px; }
.btn-cancel { padding: 10px 16px; background: transparent; border: 1px solid var(--border); color: var(--text-dim); border-radius: 8px; cursor: pointer; }

.empty-state { text-align: center; color: var(--text-dim); padding: 40px; }
.empty-state h3 { margin-bottom: 6px; }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 20px; }
.modal { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 26px; width: 480px; max-height: 92vh; overflow-y: auto; }
.modal-lg { width: 900px; }
.modal h3 { margin-bottom: 12px; }
.modal label { display: block; font-size: 11px; text-transform: uppercase; letter-spacing: 1px; color: var(--text-dim); margin: 14px 0 6px; }
.modal input { width: 100%; background: var(--bg-main); border: 1px solid var(--border); color: var(--text); padding: 9px; border-radius: 6px; font-size: 14px; outline: none; }
.form-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.role-options { display: flex; gap: 8px; }
.role-options button { background: var(--bg-main); border: 1px solid var(--border); color: var(--text-dim); padding: 8px 16px; border-radius: 6px; cursor: pointer; font-size: 13px; }
.role-options button.selected { border-color: var(--gold); color: var(--gold); background: rgba(169,121,31,0.1); }
.modal-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 20px; }
.sub { margin: 20px 0 8px; color: var(--text-dim); }

.detail-grid { display: flex; gap: 24px; flex-wrap: wrap; }
.detail-item { display: flex; flex-direction: column; gap: 4px; }
.detail-label { font-size: 10px; text-transform: uppercase; color: var(--text-dim); }
.table-wrap { max-height: 300px; overflow-y: auto; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); font-size: 14px; }
.data-table th { color: var(--text-dim); font-weight: 600; position: sticky; top: 0; background: var(--bg-card); }
.ok { color: var(--green); font-weight: 600; }
.ko { color: var(--red); font-weight: 600; }
.pend { color: var(--text-dim); }
.player-name { font-weight: 600; }
.empty-cell { text-align: center; color: var(--text-dim); padding: 16px; }
.data-table tr.yo { background: rgba(31,111,235,0.09); box-shadow: inset 3px 0 0 var(--blue); }
.tag-yo { background: var(--blue); color: #fff; font-size: 10px; padding: 1px 7px; border-radius: 10px; margin-left: 6px; font-weight: 700; }

.prox-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 12px 0; }
.prox-ok { color: var(--green); margin-bottom: 6px; }
.prox-no { color: var(--red); margin-bottom: 6px; }
.prox-list { list-style: none; padding: 0; margin: 0; max-height: 220px; overflow-y: auto; }
.prox-list li { padding: 6px 10px; border-bottom: 1px solid var(--border); font-size: 14px; }
.prox-dist { color: var(--text-dim); font-size: 12px; }
.prox-empty { color: var(--text-dim); font-style: italic; }
</style>
