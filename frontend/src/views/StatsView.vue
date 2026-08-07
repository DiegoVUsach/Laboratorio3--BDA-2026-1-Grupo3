<script setup lang="ts">
/**
 * Pestaña MUNDO: todo lo global del juego.
 *  - Mapa de poder de los clanes sobre el mundo virtual. La intensidad usa
 *    el item level TOTAL del clan (poder militar), no los DKP, que son la
 *    moneda para canjear items.
 *  - Tu clan aparece destacado y tambien se marca tu posicion.
 *  - Ranking de jugadores con filtros (mi clan / mi faccion / global).
 *  - Clanes mas cercanos a tu posicion (consulta de proximidad PostGIS).
 */
import { ref, onMounted, computed } from 'vue';
import { itemService, clanService, geoService, type RankingEntry } from '../services/api';
import WorldMap from '../components/WorldMap.vue';
import type { WorldMarker } from '../types/world';

const props = defineProps<{ character: any; token: string }>();

const ranking = ref<RankingEntry[]>([]);
const loading = ref(true);
const error = ref('');
const success = ref('');
const filtro = ref<'global' | 'faccion' | 'clan'>('global');
const miClanNombre = ref<string | null>(null);

const clanesGeo = ref<any[]>([]);
const cercanos = ref<any[]>([]);
const miPosicion = ref<{ x: number; y: number; region?: string } | null>(null);

onMounted(async () => {
  await Promise.all([cargarRanking(), cargarMapa(), cargarCercanos(), cargarMiPosicion()]);
  if (props.character?.idClan) {
    try { miClanNombre.value = (await clanService.getById(props.character.idClan)).nombreClan; } catch { /* opcional */ }
  }
});

async function cargarRanking() {
  loading.value = true; error.value = '';
  try { ranking.value = await itemService.getRanking(); }
  catch (e: any) { error.value = e.message; }
  finally { loading.value = false; }
}

async function cargarMapa() {
  try { const fc = await geoService.heatmapClanes(); clanesGeo.value = fc?.features || []; }
  catch { clanesGeo.value = []; }
}

async function cargarCercanos() {
  if (!props.character?.idPersonaje) return;
  try {
    const fc = await geoService.clanesCercanosDe(props.character.idPersonaje, 5);
    cercanos.value = (fc?.features || []).map((f: any) => f.properties);
  } catch { cercanos.value = []; }
}

async function cargarMiPosicion() {
  if (!props.character?.idPersonaje) return;
  try {
    const up = await geoService.ubicacionPersonaje(props.character.idPersonaje);
    miPosicion.value = up && up.geometry
      ? { x: up.geometry.coordinates[0], y: up.geometry.coordinates[1], region: up.properties?.region }
      : null;
  } catch { miPosicion.value = null; }
}

const pesos = computed(() => clanesGeo.value.map(f => f.properties?.peso || 0));
const maxPeso = computed(() => Math.max(1, ...pesos.value));
const minPeso = computed(() => Math.min(...(pesos.value.length ? pesos.value : [0])));

/**
 * Intensidad 0..1 del clan dentro del rango observado. Se usa el minimo y el
 * maximo del momento (y no un valor absoluto) para que las diferencias se
 * noten aunque todos los clanes tengan poder parecido. El piso de 0.15 evita
 * que el clan mas debil quede invisible.
 */
function intensidad(peso: number): number {
  const rango = maxPeso.value - minPeso.value;
  if (rango <= 0) return 1;
  return 0.15 + 0.85 * ((peso - minPeso.value) / rango);
}

// Radio acotado: nunca menor a 18 m ni mayor a 60 m, sin importar la escala
// de los datos (asi el circulo no crece sin limite si un clan se dispara).
const RADIO_MIN = 18, RADIO_MAX = 60;
const radioDe = (r: number) => RADIO_MIN + (RADIO_MAX - RADIO_MIN) * r;

function colorPoder(ratio: number): string {
  if (ratio >= 0.85) return '#c62828';
  if (ratio >= 0.65) return '#ef6c00';
  if (ratio >= 0.45) return '#f9a825';
  if (ratio >= 0.25) return '#2f8f3e';
  return '#1f6feb';
}

// Mapa de calor: intensidad = item level total del clan, normalizado
const heatPoints = computed(() =>
  clanesGeo.value.map(f => ({
    x: f.geometry.coordinates[0],
    y: f.geometry.coordinates[1],
    intensidad: intensidad(f.properties?.peso || 0),
  })));

const markers = computed<WorldMarker[]>(() => {
  const m: WorldMarker[] = clanesGeo.value.map(f => {
    const [x, y] = f.geometry.coordinates;
    const p = f.properties;
    const ratio = intensidad(p.peso || 0);
    const esMio = props.character?.idClan === p.id_clan;
    return {
      x, y,
      label: esMio ? `${p.nombre_clan} — TU CLAN` : p.nombre_clan,
      kind: esMio ? ('sede' as const) : ('clan' as const),
      color: esMio ? '#6a3fd0' : colorPoder(ratio),
      highlight: esMio,
      permanent: true,
      radius: radioDe(ratio),
      radiusColor: colorPoder(ratio),
      radiusOpacity: 0.18 + 0.4 * ratio,
      radiusSolido: true,
      popup: `<b>${p.nombre_clan}</b>${esMio ? ' <em>(tu clan)</em>' : ''}<br>Region: ${p.region || 'Tierras salvajes'}<br>Poder (iLvl total): ${p.ilvl_total}<br>iLvl promedio: ${p.ilvl_promedio}<br>Miembros: ${p.miembros}<br>DKP acumulados: ${p.dkp_total}`,
    };
  });
  if (miPosicion.value) {
    m.push({
      x: miPosicion.value.x, y: miPosicion.value.y,
      label: `${props.character.nombrePersonaje} (tu)`,
      kind: 'yo', color: '#1f6feb', highlight: true, permanent: true,
      popup: `<b>${props.character.nombrePersonaje}</b><br>Tu posicion en el mundo<br>Region: ${miPosicion.value.region || 'Tierras salvajes'}`,
    });
  }
  return m;
});

const rankingFiltrado = computed(() => {
  if (filtro.value === 'faccion') return ranking.value.filter(r => r.faccion === props.character?.faccion);
  if (filtro.value === 'clan') return ranking.value.filter(r => r.nombre_clan === miClanNombre.value);
  return ranking.value;
});

const clanesOrdenados = computed(() =>
  [...clanesGeo.value].map(f => f.properties).sort((a, b) => (b.ilvl_total || 0) - (a.ilvl_total || 0)));

async function refrescar() {
  error.value = ''; success.value = '';
  try {
    await itemService.refrescarRanking();
    await geoService.refrescarHeatmap();
    await Promise.all([cargarRanking(), cargarMapa(), cargarCercanos()]);
    success.value = 'Vistas materializadas actualizadas';
  } catch (e: any) { error.value = e.message; }
}
</script>

<template>
  <div class="mundo-view">
    <div class="view-header">
      <div>
        <h1>Mundo</h1>
        <p class="view-subtitle">Aethermoor: poder territorial de los clanes y ranking global</p>
      </div>
      <div class="header-actions">
        <button class="btn-primary" @click="refrescar">Refrescar vistas materializadas</button>
      </div>
    </div>

    <div v-if="error" class="alert alert-error" @click="error = ''">{{ error }}</div>
    <div v-if="success" class="alert alert-success" @click="success = ''">{{ success }}</div>

    <div class="panel">
      <h3 class="section-title">Mapa de Poder</h3>
      <p class="hint-mini">
        Mapa de calor del poder militar: el peso de cada clan es la suma del item level de todos sus miembros
        (los DKP son solo la moneda para canjear items). Un clan debil se dibuja con un circulo pequeño y un
        color suave; uno fuerte, con un circulo mayor y un color intenso. El radio esta acotado entre 18 y 60
        metros para que ningun clan crezca sin limite. Tu clan lleva un anillo morado y tu personaje va en azul.
      </p>
      <div class="legend">
        <span class="legend-item"><i class="dot" style="background:#c62828"></i> Poder alto</span>
        <span class="legend-item"><i class="dot" style="background:#f9a825"></i> Medio</span>
        <span class="legend-item"><i class="dot" style="background:#1f6feb"></i> Poder bajo</span>
        <span class="legend-item"><i class="dot" style="background:#6a3fd0"></i> Tu clan</span>
        <span class="legend-item">Circulo mas grande y mas intenso = clan mas fuerte</span>
      </div>
      <WorldMap :markers="markers" :heat-points="heatPoints" height="540px" />
    </div>

    <div class="grid-2">
      <div class="panel">
        <div class="panel-head">
          <h3>Ranking de Jugadores</h3>
          <div class="filtros">
            <button :class="{ active: filtro === 'clan' }" :disabled="!miClanNombre" @click="filtro = 'clan'">Mi clan</button>
            <button :class="{ active: filtro === 'faccion' }" @click="filtro = 'faccion'">Mi faccion</button>
            <button :class="{ active: filtro === 'global' }" @click="filtro = 'global'">Global</button>
          </div>
        </div>
        <div v-if="loading" class="loader">Cargando ranking...</div>
        <div v-else class="table-wrap">
          <table class="data-table">
            <thead><tr><th>#</th><th>Personaje</th><th>Clan</th><th>Raids asistidas</th><th>DKP</th></tr></thead>
            <tbody>
              <tr v-for="(r, i) in rankingFiltrado" :key="r.id_personaje"
                  :class="{ 'top-row': i < 3, mine: r.nombre_clan === miClanNombre }">
                <td>{{ i + 1 }}</td>
                <td class="player-name">{{ r.nombre_personaje }}</td>
                <td>{{ r.nombre_clan }}</td>
                <td>{{ r.raids_asistidas }}</td>
                <td class="dkp-val">{{ r.contribucion_dkp }}</td>
              </tr>
              <tr v-if="rankingFiltrado.length === 0"><td colspan="5" class="empty-cell">Sin datos de ranking</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="col-stack">
        <div class="panel">
          <div class="panel-head"><h3>Ranking de Clanes</h3></div>
          <p class="hint-mini">Ordenados por item level total (el mismo peso que usa el mapa de calor).</p>
          <div class="table-wrap">
            <table class="data-table">
              <thead><tr><th>#</th><th>Clan</th><th>iLvl total</th><th>Miembros</th></tr></thead>
              <tbody>
                <tr v-for="(c, i) in clanesOrdenados" :key="c.id_clan" :class="{ mine: c.id_clan === character?.idClan }">
                  <td>{{ i + 1 }}</td>
                  <td class="player-name">{{ c.nombre_clan }}</td>
                  <td class="dkp-val">{{ c.ilvl_total }}</td>
                  <td>{{ c.miembros }}</td>
                </tr>
                <tr v-if="clanesOrdenados.length === 0"><td colspan="4" class="empty-cell">Sin clanes</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head"><h3>Clanes mas cercanos a ti</h3></div>
          <p class="hint-mini">
            Consulta de proximidad desde tu posicion en el mundo
            <span v-if="miPosicion">({{ miPosicion.x }}, {{ miPosicion.y }} — {{ miPosicion.region || 'tierras salvajes' }})</span>.
          </p>
          <div class="table-wrap">
            <table class="data-table">
              <thead><tr><th>Clan</th><th>Region</th><th>Distancia</th></tr></thead>
              <tbody>
                <tr v-for="c in cercanos" :key="c.id_clan" :class="{ mine: c.id_clan === character?.idClan }">
                  <td class="player-name">{{ c.nombre_clan }}</td>
                  <td>{{ c.region || 'Tierras salvajes' }}</td>
                  <td class="dkp-val">{{ c.distancia }} m</td>
                </tr>
                <tr v-if="cercanos.length === 0"><td colspan="3" class="empty-cell">Tu personaje aun no tiene posicion en el mundo</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mundo-view { display: flex; flex-direction: column; gap: 18px; padding: 24px; }
.view-header { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 12px; }
.view-subtitle { color: var(--text-dim); font-size: 14px; }
.btn-primary { background: var(--gold); color: #fff; border: none; padding: 10px 16px; border-radius: 8px; cursor: pointer; font-weight: 600; }
.alert { padding: 10px 14px; border-radius: 8px; cursor: pointer; }
.alert-error { background: #fdecea; color: var(--red); }
.alert-success { background: #e9f7ec; color: var(--green); }
.panel { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 18px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; flex-wrap: wrap; gap: 8px; }
.section-title { margin-bottom: 8px; }
.hint-mini { font-size: 12px; color: var(--text-dim); margin-bottom: 10px; }
.grid-2 { display: grid; grid-template-columns: 1.25fr 1fr; gap: 18px; align-items: start; }
.col-stack { display: flex; flex-direction: column; gap: 18px; }
.legend { display: flex; gap: 16px; font-size: 12px; color: var(--text-dim); margin-bottom: 10px; flex-wrap: wrap; align-items: center; }
.legend-item { display: flex; align-items: center; gap: 6px; }
.dot { width: 12px; height: 12px; border-radius: 50%; display: inline-block; }
.filtros { display: flex; gap: 6px; }
.filtros button { background: transparent; border: 1px solid var(--border); color: var(--text-dim); padding: 6px 12px; border-radius: 20px; cursor: pointer; font-size: 13px; }
.filtros button.active { background: var(--gold); color: #fff; border-color: var(--gold); }
.filtros button:disabled { opacity: 0.4; cursor: not-allowed; }
.table-wrap { max-height: 420px; overflow-y: auto; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { text-align: left; padding: 9px 12px; border-bottom: 1px solid var(--border); font-size: 14px; }
.data-table th { color: var(--text-dim); font-weight: 600; position: sticky; top: 0; background: var(--bg-card); }
.top-row { background: rgba(169,121,31,0.06); }
.mine { box-shadow: inset 3px 0 0 var(--gold); }
.player-name { font-weight: 600; }
.dkp-val { color: var(--gold); font-weight: 700; }
.empty-cell { text-align: center; color: var(--text-dim); padding: 20px; }
.loader { text-align: center; color: var(--text-dim); padding: 20px; }
@media (max-width: 1200px) { .grid-2 { grid-template-columns: 1fr; } }
</style>
