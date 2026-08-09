<script setup lang="ts">
/**
 * Pestaña MUNDO: todo lo global del juego.
 *  - Ranking de jugadores con filtros (mi clan / mi faccion / global).
 *  - Refrescar ranking materializado.
 */
import { ref, onMounted, computed } from 'vue';
import { itemService, clanService, type RankingEntry } from '../services/api';

const props = defineProps<{ character: any; token: string }>();

const ranking = ref<RankingEntry[]>([]);
const rankingClanes = ref<any[]>([]);
const vista = ref<'clanes' | 'jugadores'>('clanes');
const loading = ref(true);
const error = ref('');
const success = ref('');
const filtro = ref<'global' | 'faccion' | 'clan'>('global');
const miClanNombre = ref<string | null>(null);

onMounted(async () => {
  await cargarRanking();
  if (props.character?.idClan) {
    try { miClanNombre.value = (await clanService.getById(props.character.idClan)).nombreClan; } catch { /* opcional */ }
  }
});

async function cargarRanking() {
  loading.value = true; error.value = '';
  try { rankingClanes.value = await itemService.getRanking(); }
  catch (e: any) { error.value = e.message; }
  try { ranking.value = await itemService.rankingJugadores(); }
  catch (e: any) { error.value = e.message; }
  finally { loading.value = false; }
}

const rankingFiltrado = computed(() => {
  if (filtro.value === 'faccion') return ranking.value.filter(r => r.faccion === props.character?.faccion);
  if (filtro.value === 'clan') return ranking.value.filter(r => r.nombre_clan === miClanNombre.value);
  return ranking.value;
});

async function refrescar() {
  error.value = ''; success.value = '';
  try {
    await itemService.refrescarRanking();
    await cargarRanking();
    success.value = 'Ranking materializado actualizado con exito';
  } catch (e: any) { error.value = e.message; }
}
</script>

<template>
  <div class="mundo-view">
    <div class="view-header">
      <div>
        <h1>Mundo</h1>
        <p class="view-subtitle">Ranking global y de clanes de Aethermoor</p>
      </div>
      <div class="header-actions">
        <button class="btn-primary" @click="refrescar">Refrescar Ranking</button>
      </div>
    </div>

    <div v-if="error" class="alert alert-error" @click="error = ''">{{ error }}</div>
    <div v-if="success" class="alert alert-success" @click="success = ''">{{ success }}</div>

    <!-- Podio de clanes (Tarea 4) -->
    <div v-if="rankingClanes.length > 0" class="podio">
      <div v-for="(c, i) in rankingClanes.slice(0, 3)" :key="c.id_clan"
           class="podio-card" :class="['pos-' + (i + 1), { mine: c.nombre_clan === miClanNombre }]">
        <span class="podio-pos">{{ i + 1 }}</span>
        <h3>{{ c.nombre_clan }}</h3>
        <p class="podio-puntaje">{{ (c.puntaje || 0).toLocaleString('es-CL') }} pts</p>
        <p class="podio-detalle">
          {{ (c.dano_total || 0).toLocaleString('es-CL') }} de dano ·
          {{ c.asistencia_total }} asistencias · {{ c.tiempo_promedio }} min prom.
        </p>
      </div>
    </div>

    <div class="tabs">
      <button :class="{ active: vista === 'clanes' }" @click="vista = 'clanes'">Clanes</button>
      <button :class="{ active: vista === 'jugadores' }" @click="vista = 'jugadores'">Jugadores</button>
    </div>

    <!-- Ranking de CLANES: materializada clanes_rankeados -->
    <div v-if="vista === 'clanes'" class="panel">
      <div class="panel-head"><h3>Clanes mejor rankeados</h3></div>
      <p class="panel-note">
        Ranking basado en el desempeno de cada clan. El puntaje combina el dano por minuto
        con la asistencia acumulada en sus raids.
      </p>
      <div v-if="loading" class="loader">Cargando ranking...</div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr><th>#</th><th>Clan</th><th>Raids</th><th>Asistencia</th>
                <th>Dano total</th><th>Tiempo prom.</th><th>Dano/min</th><th>Puntaje</th></tr>
          </thead>
          <tbody>
            <tr v-for="(c, i) in rankingClanes" :key="c.id_clan"
                :class="{ 'top-row': i < 3, mine: c.nombre_clan === miClanNombre }">
              <td>{{ i + 1 }}</td>
              <td class="player-name">{{ c.nombre_clan }}</td>
              <td>{{ c.raids_completadas }}</td>
              <td>{{ c.asistencia_total }}</td>
              <td>{{ (c.dano_total || 0).toLocaleString('es-CL') }}</td>
              <td>{{ c.tiempo_promedio }} min</td>
              <td>{{ (c.dano_por_minuto || 0).toLocaleString('es-CL') }}</td>
              <td class="dkp-val">{{ (c.puntaje || 0).toLocaleString('es-CL') }}</td>
            </tr>
            <tr v-if="rankingClanes.length === 0">
              <td colspan="8" class="empty-cell">
                Sin datos. Finaliza una raid registrando duracion y dano para alimentar el ranking.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else class="panel">
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
              <td>{{ r.nombre_clan || 'Sin clan' }}</td>
              <td>{{ r.raids_asistidas }}</td>
              <td class="dkp-val">{{ r.contribucion_dkp }}</td>
            </tr>
            <tr v-if="rankingFiltrado.length === 0"><td colspan="5" class="empty-cell">Sin datos de ranking</td></tr>
          </tbody>
        </table>
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
.filtros { display: flex; gap: 6px; }
.filtros button { background: transparent; border: 1px solid var(--border); color: var(--text-dim); padding: 6px 12px; border-radius: 20px; cursor: pointer; font-size: 13px; }
.filtros button.active { background: var(--gold); color: #fff; border-color: var(--gold); }
.filtros button:disabled { opacity: 0.4; cursor: not-allowed; }
.table-wrap { max-height: 500px; overflow-y: auto; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { text-align: left; padding: 9px 12px; border-bottom: 1px solid var(--border); font-size: 14px; }
.data-table th { color: var(--text-dim); font-weight: 600; position: sticky; top: 0; background: var(--bg-card); }
.top-row { background: rgba(169,121,31,0.06); }
.mine { box-shadow: inset 3px 0 0 var(--gold); }
.player-name { font-weight: 600; }
.dkp-val { color: var(--gold); font-weight: 700; }
.podio { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 4px; }
.podio-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 16px; position: relative; }
.podio-card.pos-1 { border-color: var(--gold); box-shadow: 0 0 0 1px var(--gold) inset; }
.podio-card.mine { background: rgba(169,121,31,0.06); }
.podio-pos { position: absolute; top: 12px; right: 14px; font-size: 26px; font-weight: 800; color: var(--border); }
.podio-card h3 { margin: 0 0 6px; font-size: 16px; }
.podio-puntaje { color: var(--gold); font-weight: 700; font-size: 20px; margin: 0 0 4px; }
.podio-detalle { font-size: 12px; color: var(--text-dim); margin: 0; }
.tabs { display: flex; gap: 8px; }
.tabs button { background: transparent; border: 1px solid var(--border); color: var(--text-dim); padding: 7px 16px; border-radius: 20px; cursor: pointer; font-size: 13px; }
.tabs button.active { background: var(--gold); color: #fff; border-color: var(--gold); }
.panel-note { font-size: 12px; color: var(--text-dim); margin: 6px 0 12px; }
.panel-note code { background: rgba(0,0,0,0.05); padding: 1px 5px; border-radius: 4px; }
@media (max-width: 900px) { .podio { grid-template-columns: 1fr; } }
.empty-cell { text-align: center; color: var(--text-dim); padding: 20px; }
.loader { text-align: center; color: var(--text-dim); padding: 20px; }
</style>
