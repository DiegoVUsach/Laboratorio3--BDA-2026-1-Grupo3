<script setup lang="ts">
/**
 * Pestaña MI PERSONAJE: ficha del personaje, equipamiento (equipar y
 * desequipar), bolsa, botin por canjear e historial.
 * Al equipar cambia el item level; al canjear se descuentan los DKP.
 * En ambos casos se pide al contenedor que refresque el personaje.
 */
import { ref, onMounted, computed } from 'vue';
import { inventarioService, itemService, type InventarioDTO, type InventarioItemDTO, type HistorialBotin } from '../services/api';
import { fechaCL } from '../utils/format';

const props = defineProps<{ character: any; token: string }>();
const emit = defineEmits<{ (e: 'refrescar'): void }>();

const inventario = ref<InventarioDTO | null>(null);
const bolsa = ref<InventarioItemDTO[]>([]);
const pool = ref<any[]>([]);
const historial = ref<HistorialBotin[]>([]);
const loading = ref(true);
const error = ref('');
const success = ref('');

onMounted(cargar);

async function cargar() {
  loading.value = true; error.value = '';
  const id = props.character.idPersonaje;
  // Cada consulta se resuelve por separado: un personaje recien creado todavia
  // no tiene botin ni historial, y eso no debe impedir ver el resto.
  try { inventario.value = await inventarioService.getByPersonaje(id); }
  catch { inventario.value = null; }
  try { bolsa.value = await inventarioService.getItemsByPersonaje(id); }
  catch { bolsa.value = []; }
  try { pool.value = await itemService.miPool(id); }
  catch { pool.value = []; }
  try { historial.value = await itemService.getHistorial(id); }
  catch { historial.value = []; }
  loading.value = false;
}

const slotDe = (tipo: string) =>
  tipo === 'ARMA' ? 'armaEquipado' : tipo === 'ARMADURA' ? 'armaduraEquipado' : 'accesorioEquipado';

const equipados = computed(() => ({
  ARMADURA: inventario.value?.armaduraEquipado ?? null,
  ARMA: inventario.value?.armaEquipado ?? null,
  ACCESORIO: inventario.value?.accesorioEquipado ?? null,
}));

function estaEquipado(it: InventarioItemDTO): boolean {
  return (equipados.value as any)[it.tipo] === it.idItem;
}

async function guardarEquipo(cambios: Partial<Record<string, number | null>>) {
  if (!inventario.value) {
    error.value = 'Todavia no se pudo cargar tu inventario. Recarga la pagina e intentalo de nuevo.';
    return;
  }
  error.value = ''; success.value = '';
  const cuerpo = {
    idInventario: (inventario.value as any).idInventario,
    idPersonaje: props.character.idPersonaje,
    armaduraEquipado: inventario.value.armaduraEquipado ?? null,
    armaEquipado: inventario.value.armaEquipado ?? null,
    accesorioEquipado: inventario.value.accesorioEquipado ?? null,
    ...cambios,
  } as any;
  try {
    await inventarioService.equipar(cuerpo);
    await cargar();
    emit('refrescar');
    success.value = 'Equipamiento actualizado. Tu item level se recalculo.';
  } catch (e: any) { error.value = e.message; }
}

const equipar = (it: InventarioItemDTO) => guardarEquipo({ [slotDe(it.tipo)]: it.idItem });
const desequipar = (tipo: string) => guardarEquipo({ [slotDe(tipo)]: null });

async function canjear(idPool: number) {
  error.value = ''; success.value = '';
  try {
    await itemService.canjear(idPool, props.character.idPersonaje);
    await cargar();
    emit('refrescar');
    success.value = 'Item canjeado: se descontaron los DKP y quedo en tu bolsa.';
  } catch (e: any) { error.value = e.message; }
}

const pendientes = computed(() => pool.value.filter(p => !p.canjeado));
const canjeados = computed(() => pool.value.filter(p => p.canjeado));

const slots = [
  { tipo: 'ARMADURA', label: 'Armadura', nombre: () => inventario.value?.nombreArmadura, nivel: () => inventario.value?.nivelArmadura },
  { tipo: 'ARMA', label: 'Arma', nombre: () => inventario.value?.nombreArma, nivel: () => inventario.value?.nivelArma },
  { tipo: 'ACCESORIO', label: 'Accesorio', nombre: () => inventario.value?.nombreAccesorio, nivel: () => (inventario.value as any)?.nivelAccesorio },
];
</script>

<template>
  <div class="pj-view">
    <div class="view-header">
      <div>
        <h1>Mi Personaje</h1>
        <p class="view-subtitle">Ficha, equipamiento y botin de {{ character.nombrePersonaje }}</p>
      </div>
    </div>

    <div v-if="error" class="alert alert-error" @click="error = ''">{{ error }}</div>
    <div v-if="success" class="alert alert-success" @click="success = ''">{{ success }}</div>

    <!-- Ficha -->
    <div class="panel ficha">
      <div class="ficha-main">
        <h2>{{ character.nombrePersonaje }}</h2>
        <p class="ficha-sub">{{ character.clase }} · {{ character.faccion }}</p>
        <p class="ficha-clan">{{ character.nombreClan || 'Sin clan' }} · {{ character.rolClan }}</p>
      </div>
      <div class="ficha-stats">
        <div class="stat"><span class="stat-label">Nivel</span><span class="stat-val">{{ character.nivel }}</span></div>
        <div class="stat"><span class="stat-label">Item level</span><span class="stat-val">{{ character.itemLevel }}</span></div>
        <div class="stat"><span class="stat-label">DKP</span><span class="stat-val">{{ character.puntosDkpActuales }}</span></div>
      </div>
    </div>

    <div v-if="loading" class="loader">Cargando...</div>

    <template v-else>
      <!-- Equipamiento -->
      <div class="panel">
        <h3 class="section-title">Equipamiento</h3>
        <p class="hint-mini">El item level del personaje es la suma de los tres slots equipados.</p>
        <div class="equip-grid">
          <div v-for="s in slots" :key="s.tipo" class="equip-slot">
            <span class="equip-label">{{ s.label }}</span>
            <span class="equip-name">{{ s.nombre() || 'Vacio' }}</span>
            <span class="equip-lvl">{{ s.nivel() ? 'Nivel ' + s.nivel() : '' }}</span>
            <button v-if="s.nombre()" class="btn-sm-secondary" @click="desequipar(s.tipo)">Desequipar</button>
          </div>
        </div>
      </div>

      <!-- Bolsa -->
      <div class="panel">
        <h3 class="section-title">Bolsa ({{ bolsa.length }})</h3>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>Item</th><th>Rareza</th><th>Tipo</th><th>Nivel</th><th>Estado</th><th>Accion</th></tr></thead>
            <tbody>
              <tr v-for="(it, i) in bolsa" :key="i" :class="{ 'top-row': estaEquipado(it) }">
                <td class="player-name">{{ it.nombreItem }}</td>
                <td>{{ it.rareza }}</td>
                <td>{{ it.tipo }}</td>
                <td>{{ it.nivel }}</td>
                <td>{{ estaEquipado(it) ? 'Equipado' : 'En la bolsa' }}</td>
                <td>
                  <button v-if="!estaEquipado(it)" class="btn-sm-primary" @click="equipar(it)">Equipar</button>
                  <button v-else class="btn-sm-secondary" @click="desequipar(it.tipo)">Desequipar</button>
                </td>
              </tr>
              <tr v-if="bolsa.length === 0"><td colspan="6" class="empty-cell">Tu bolsa esta vacia. Participa en una raid para conseguir tu primer item.</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Botin por canjear -->
      <div class="panel">
        <h3 class="section-title">Mi botin por canjear ({{ pendientes.length }})</h3>
        <p class="hint-mini">Cada raid en la que participaste dentro del radio del jefe te dio un item. Canjealo pagando DKP.</p>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>Item</th><th>Rareza</th><th>Tipo</th><th>Nivel</th><th>Costo DKP</th><th>Accion</th></tr></thead>
            <tbody>
              <tr v-for="p in pendientes" :key="p.id_pool">
                <td class="player-name">{{ p.nombre_item }}</td>
                <td>{{ p.rareza }}</td>
                <td>{{ p.tipo }}</td>
                <td>{{ p.nivel }}</td>
                <td class="dkp-val">{{ p.costo_dkp }}</td>
                <td>
                  <button class="btn-sm-primary" :disabled="p.costo_dkp > character.puntosDkpActuales" @click="canjear(p.id_pool)">
                    {{ p.costo_dkp > character.puntosDkpActuales ? 'DKP insuficientes' : 'Canjear' }}
                  </button>
                </td>
              </tr>
              <tr v-if="pendientes.length === 0"><td colspan="6" class="empty-cell">No tienes botin pendiente. El botin se reparte al finalizar una raid en la que participes.</td></tr>
            </tbody>
          </table>
        </div>
        <p v-if="canjeados.length > 0" class="hint-mini">Ya canjeaste {{ canjeados.length }} item(s).</p>
      </div>

      <!-- Historial -->
      <div class="panel">
        <h3 class="section-title">Historial de botin</h3>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>Item</th><th>Fecha</th></tr></thead>
            <tbody>
              <tr v-for="(h, i) in historial" :key="i">
                <td>{{ h.nombreItem }}</td>
                <td>{{ fechaCL(h.fechaEntrega) }}</td>
              </tr>
              <tr v-if="historial.length === 0"><td colspan="2" class="empty-cell">Sin historial</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.pj-view { display: flex; flex-direction: column; gap: 18px; padding: 24px; }
.view-header { display: flex; justify-content: space-between; align-items: flex-end; }
.view-subtitle { color: var(--text-dim); font-size: 14px; }
.alert { padding: 10px 14px; border-radius: 8px; cursor: pointer; }
.alert-error { background: #fdecea; color: var(--red); }
.alert-success { background: #e9f7ec; color: var(--green); }
.loader { text-align: center; color: var(--text-dim); padding: 30px; }
.panel { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 18px; }
.section-title { margin-bottom: 8px; }
.hint-mini { font-size: 12px; color: var(--text-dim); margin-bottom: 10px; }

.ficha { display: flex; justify-content: space-between; align-items: center; gap: 20px; flex-wrap: wrap; }
.ficha-sub { color: var(--text-dim); font-size: 14px; }
.ficha-clan { color: var(--gold); font-size: 14px; font-weight: 600; margin-top: 2px; }
.ficha-stats { display: flex; gap: 26px; }
.stat { text-align: center; }
.stat-label { display: block; font-size: 10px; text-transform: uppercase; color: var(--text-dim); letter-spacing: 0.5px; }
.stat-val { font-size: 24px; font-weight: 800; color: var(--gold); }

.equip-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.equip-slot { background: var(--bg-main); border: 1px solid var(--border); border-radius: 10px; padding: 14px; display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
.equip-label { font-size: 10px; text-transform: uppercase; color: var(--text-dim); letter-spacing: 0.5px; }
.equip-name { font-weight: 700; font-size: 15px; }
.equip-lvl { font-size: 12px; color: var(--text-dim); }

.table-wrap { max-height: 340px; overflow-y: auto; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); font-size: 14px; }
.data-table th { color: var(--text-dim); font-weight: 600; position: sticky; top: 0; background: var(--bg-card); }
.top-row { background: rgba(169,121,31,0.08); }
.player-name { font-weight: 600; }
.dkp-val { color: var(--gold); font-weight: 700; }
.empty-cell { text-align: center; color: var(--text-dim); padding: 16px; }

.btn-sm-primary { background: var(--gold); color: #fff; border: none; padding: 5px 12px; border-radius: 6px; cursor: pointer; font-size: 13px; }
.btn-sm-primary:disabled { opacity: 0.45; cursor: not-allowed; }
.btn-sm-secondary { background: transparent; border: 1px solid var(--border); color: var(--text-dim); padding: 5px 12px; border-radius: 6px; cursor: pointer; font-size: 13px; }
</style>
