<script setup lang="ts">
/**
 * Pestaña MI CLAN: gestion del clan, miembros, roles, transferencia de
 * liderazgo y el mapa historico de "Sedes de Poder" (auditoria territorial).
 */
import { ref, onMounted, computed } from 'vue';
import { clanService, personajeService, type Clan, type Personaje } from '../services/api';
import { fechaHoraCL } from '../utils/format';

const props = defineProps<{ character: any; token: string }>();
const emit = defineEmits<{ (e: 'refrescar'): void }>();

const clanes = ref<Clan[]>([]);
const miembros = ref<Personaje[]>([]);
const miClan = ref<Clan | null>(null);
const loading = ref(true);
const error = ref('');
const success = ref('');

const esGM = computed(() => props.character?.rolClan === 'Guild Master');
const tieneClan = computed(() => props.character?.idClan !== null && props.character?.idClan !== undefined);
const clanesDisponibles = computed(() =>
  clanes.value.filter(c => !c.faccion || c.faccion === props.character?.faccion));

onMounted(cargarDatos);

async function cargarDatos() {
  loading.value = true;
  try {
    clanes.value = await clanService.getAll();
    if (tieneClan.value) {
      miClan.value = clanes.value.find(c => c.idClan === props.character.idClan) || null;
      miembros.value = await clanService.getMiembros(props.character.idClan);
    } else { miClan.value = null; miembros.value = []; }
  } catch (e: any) { error.value = e.message; }
  finally { loading.value = false; }
}

// ---------- Fundar ----------
const showCrear = ref(false);
const crearError = ref('');
const nombreClan = ref('');

function abrirCrear() {
  crearError.value = ''; nombreClan.value = '';
  showCrear.value = true;
}

async function fundarClan() {
  crearError.value = '';
  if (!nombreClan.value.trim()) { crearError.value = 'Escribe el nombre del clan'; return; }
  try {
    await clanService.fundar(props.character.idPersonaje, nombreClan.value.trim());
    success.value = 'Clan fundado.';
    showCrear.value = false;
    await cargarDatos();
    emit('refrescar');
  } catch (e: any) { crearError.value = e.message; }
}

// ---------- Unirse / salir / disolver ----------
const showUnirse = ref(false);
const unirseError = ref('');
const clanSeleccionado = ref<number | null>(null);

function abrirUnirse() { unirseError.value = ''; clanSeleccionado.value = null; showUnirse.value = true; }

async function unirseAClan() {
  unirseError.value = '';
  if (!clanSeleccionado.value) { unirseError.value = 'Selecciona un clan de tu faccion'; return; }
  try {
    await clanService.unirse(props.character.idPersonaje, clanSeleccionado.value);
    success.value = 'Te uniste al clan.';
    showUnirse.value = false;
    await cargarDatos();
    emit('refrescar');
  } catch (e: any) { unirseError.value = e.message; }
}

async function salirDelClan() {
  if (!confirm('Seguro que quieres salir del clan?')) return;
  error.value = '';
  try {
    await clanService.salir(props.character.idPersonaje);
    success.value = 'Saliste del clan.';
    await cargarDatos();
    emit('refrescar');
  } catch (e: any) { error.value = e.message; }
}

async function disolverClan() {
  if (!confirm('Disolver el clan? Se libera a todos sus miembros.')) return;
  error.value = '';
  try {
    await clanService.disolver(props.character.idClan);
    success.value = 'Clan disuelto.';
    await cargarDatos();
    emit('refrescar');
  } catch (e: any) { error.value = e.message; }
}

// ---------- Transferir liderazgo ----------
const showTransferir = ref(false);
const transferError = ref('');
const nuevoLiderId = ref<number | null>(null);

function abrirTransferir() {
  transferError.value = ''; nuevoLiderId.value = null;
  showTransferir.value = true;
}

async function transferirLiderazgo() {
  transferError.value = '';
  if (!nuevoLiderId.value) { transferError.value = 'Elige al nuevo lider'; return; }
  try {
    await clanService.transferLeadership(props.character.idClan, props.character.idPersonaje, nuevoLiderId.value);
    success.value = 'Liderazgo transferido.';
    showTransferir.value = false;
    await cargarDatos();
    emit('refrescar');
  } catch (e: any) { transferError.value = e.message; }
}



// ---------- Asignar rol ----------
const showAsignarRol = ref(false);
const rolError = ref('');
const miembroSeleccionado = ref<number | null>(null);
const nuevoRol = ref('');
const roles = ['Raider', 'Member'];

function abrirAsignarRol() { rolError.value = ''; miembroSeleccionado.value = null; nuevoRol.value = ''; showAsignarRol.value = true; }

async function asignarRol() {
  rolError.value = '';
  if (!miembroSeleccionado.value || !nuevoRol.value) { rolError.value = 'Elige miembro y rol'; return; }
  try {
    await personajeService.asignarRol({
      idEjecutor: props.character.idPersonaje,
      idObjetivo: miembroSeleccionado.value,
      nuevoRol: nuevoRol.value,
    });
    success.value = 'Rol actualizado';
    showAsignarRol.value = false;
    await cargarDatos();
  } catch (e: any) { rolError.value = e.message; }
}
</script>

<template>
  <div class="clan-view">
    <div class="view-header">
      <div>
        <h1>Mi Clan</h1>
        <p class="view-subtitle">Tu hermandad, sus miembros y su historia territorial</p>
      </div>
    </div>

    <div v-if="error" class="alert alert-error" @click="error = ''">{{ error }}</div>
    <div v-if="success" class="alert alert-success" @click="success = ''">{{ success }}</div>

    <div v-if="loading" class="loader">Cargando...</div>

    <div v-else-if="!tieneClan" class="panel empty-state">
      <h3>Aun no perteneces a ningun clan</h3>
      <p>Funda tu propio clan o unete a uno de tu faccion.</p>
      <div class="empty-actions">
        <button class="btn-primary" @click="abrirCrear">Fundar clan</button>
        <button class="btn-outline" :disabled="clanesDisponibles.length === 0" @click="abrirUnirse">Unirse a un clan</button>
      </div>
      <p v-if="clanesDisponibles.length === 0" class="hint-mini">No hay clanes de tu faccion disponibles ahora mismo.</p>
    </div>

    <template v-else>
      <div class="panel clan-head">
        <div class="clan-name-row">
          <h2>{{ miClan?.nombreClan || 'Mi clan' }}</h2>
          <span class="role-badge" :class="esGM ? 'gm' : 'member'">{{ character.rolClan }}</span>
        </div>
        <div class="clan-actions">
          <button v-if="esGM" class="btn-outline" @click="abrirTransferir">Transferir liderazgo</button>
          <button v-if="esGM" class="btn-outline" @click="abrirAsignarRol">Asignar rol</button>
          <button v-if="!esGM" class="btn-danger" @click="salirDelClan">Salir del clan</button>
          <button v-if="esGM" class="btn-danger" @click="disolverClan">Disolver clan</button>
        </div>
      </div>

      <div class="panel">
        <h3 class="section-title">Miembros ({{ miembros.length }})</h3>
        <div class="members-grid">
          <div v-for="m in miembros" :key="m.idPersonaje" class="member-card" :class="{ me: m.idPersonaje === character.idPersonaje }">
            <div class="member-name">{{ m.nombrePersonaje }}</div>
            <div class="member-info">
              <span>{{ m.clase }}</span>
              <span>Nivel {{ m.nivel }}</span>
              <span>iLvl {{ m.itemLevel }}</span>
              <span class="member-role">{{ m.rolClan }}</span>
            </div>
          </div>
          <div v-if="miembros.length === 0" class="empty-small">Sin miembros registrados</div>
        </div>
      </div>
    </template>

    <!-- Modal: fundar -->
    <div v-if="showCrear" class="modal-overlay" @click.self="showCrear = false">
      <div class="modal modal-lg">
        <h3>Fundar nuevo clan</h3>
        <div v-if="crearError" class="alert alert-error">{{ crearError }}</div>
        <label>Nombre del clan</label>
        <input v-model="nombreClan" type="text" placeholder="Nombre del clan" maxlength="50">

        <div class="modal-actions">
          <button class="btn-cancel" @click="showCrear = false">Cancelar</button>
          <button class="btn-primary" @click="fundarClan">Fundar</button>
        </div>
      </div>
    </div>

    <!-- Modal: unirse -->
    <div v-if="showUnirse" class="modal-overlay" @click.self="showUnirse = false">
      <div class="modal">
        <h3>Unirse a un clan</h3>
        <div v-if="unirseError" class="alert alert-error">{{ unirseError }}</div>
        <p class="hint-mini">Solo se muestran clanes de tu faccion: {{ character.faccion }}</p>
        <label>Clan</label>
        <select v-model="clanSeleccionado">
          <option :value="null" disabled>Elige un clan</option>
          <option v-for="c in clanesDisponibles" :key="c.idClan" :value="c.idClan">{{ c.nombreClan }}</option>
        </select>
        <div class="modal-actions">
          <button class="btn-cancel" @click="showUnirse = false">Cancelar</button>
          <button class="btn-primary" @click="unirseAClan">Unirse</button>
        </div>
      </div>
    </div>

    <!-- Modal: transferir -->
    <div v-if="showTransferir" class="modal-overlay" @click.self="showTransferir = false">
      <div class="modal modal-lg">
        <h3>Transferir liderazgo</h3>
        <div v-if="transferError" class="alert alert-error">{{ transferError }}</div>
        <label>Nuevo lider</label>
        <select v-model="nuevoLiderId">
          <option :value="null" disabled>Elige un miembro</option>
          <option v-for="m in miembros.filter(x => x.idPersonaje !== character.idPersonaje)" :key="m.idPersonaje" :value="m.idPersonaje">
            {{ m.nombrePersonaje }} ({{ m.clase }}, iLvl {{ m.itemLevel }})
          </option>
        </select>

        <div class="modal-actions">
          <button class="btn-cancel" @click="showTransferir = false">Cancelar</button>
          <button class="btn-primary" @click="transferirLiderazgo">Transferir</button>
        </div>
      </div>
    </div>


    <!-- Modal: asignar rol -->
    <div v-if="showAsignarRol" class="modal-overlay" @click.self="showAsignarRol = false">
      <div class="modal">
        <h3>Asignar rol</h3>
        <div v-if="rolError" class="alert alert-error">{{ rolError }}</div>
        <label>Miembro</label>
        <select v-model="miembroSeleccionado">
          <option :value="null" disabled>Elige</option>
          <option v-for="m in miembros.filter(x => x.idPersonaje !== character.idPersonaje)" :key="m.idPersonaje" :value="m.idPersonaje">{{ m.nombrePersonaje }}</option>
        </select>
        <label>Nuevo rol</label>
        <select v-model="nuevoRol">
          <option value="" disabled>Elige</option>
          <option v-for="r in roles" :key="r" :value="r">{{ r }}</option>
        </select>
        <div class="modal-actions">
          <button class="btn-cancel" @click="showAsignarRol = false">Cancelar</button>
          <button class="btn-primary" @click="asignarRol">Asignar</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.clan-view { display: flex; flex-direction: column; gap: 18px; padding: 24px; }
.view-header { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 12px; }
.view-subtitle { color: var(--text-dim); font-size: 14px; }
.alert { padding: 10px 14px; border-radius: 8px; cursor: pointer; }
.alert-error { background: #fdecea; color: var(--red); }
.alert-success { background: #e9f7ec; color: var(--green); }
.loader { text-align: center; color: var(--text-dim); padding: 30px; }
.panel { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 18px; }
.empty-state { text-align: center; color: var(--text-dim); }
.empty-state h3 { margin-bottom: 6px; }
.empty-actions { display: flex; gap: 10px; justify-content: center; margin-top: 16px; flex-wrap: wrap; }
.hint-mini { font-size: 12px; color: var(--text-dim); margin: 6px 0 10px; }
.clan-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.clan-name-row { display: flex; align-items: center; gap: 12px; }
.role-badge { padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: 700; }
.role-badge.gm { background: rgba(106,63,208,0.12); color: var(--purple); }
.role-badge.member { background: var(--bg-main); color: var(--text-dim); }
.clan-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.section-title { margin-bottom: 12px; }
.members-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.member-card { background: var(--bg-main); border: 1px solid var(--border); border-radius: 10px; padding: 12px; }
.member-card.me { border-color: var(--gold); }
.member-name { font-weight: 700; margin-bottom: 4px; }
.member-info { display: flex; gap: 10px; flex-wrap: wrap; font-size: 12px; color: var(--text-dim); }
.member-role { color: var(--gold); font-weight: 600; }
.empty-small { color: var(--text-dim); font-size: 14px; padding: 10px; }
.btn-primary { background: var(--gold); color: #fff; border: none; padding: 10px 16px; border-radius: 8px; cursor: pointer; font-weight: 600; }
.btn-outline { background: transparent; border: 1px solid var(--border); color: var(--text); padding: 8px 14px; border-radius: 8px; cursor: pointer; font-size: 13px; }
.btn-outline:disabled { opacity: 0.45; cursor: not-allowed; }
.btn-danger { background: transparent; border: 1px solid var(--red); color: var(--red); padding: 8px 14px; border-radius: 8px; cursor: pointer; font-size: 13px; }
.btn-cancel { padding: 10px 16px; background: transparent; border: 1px solid var(--border); color: var(--text-dim); border-radius: 8px; cursor: pointer; }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 20px; }
.modal { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 26px; width: 460px; max-height: 92vh; overflow-y: auto; }
.modal-lg { width: 860px; }
.modal h3 { margin-bottom: 12px; }
.modal label { display: block; font-size: 11px; text-transform: uppercase; letter-spacing: 1px; color: var(--text-dim); margin: 14px 0 6px; }
.modal input, .modal select { width: 100%; background: var(--bg-main); border: 1px solid var(--border); color: var(--text); padding: 9px; border-radius: 6px; font-size: 14px; outline: none; }
.modal-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 20px; }
.table-wrap { max-height: 260px; overflow-y: auto; margin-top: 12px; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); font-size: 14px; }
.data-table th { color: var(--text-dim); font-weight: 600; position: sticky; top: 0; background: var(--bg-card); }
.top-row { background: rgba(169,121,31,0.08); }
.player-name { font-weight: 600; }
.empty-cell { text-align: center; color: var(--text-dim); padding: 16px; }
</style>
