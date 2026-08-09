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
  try { ranking.value = await itemService.getRanking(); }
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
.empty-cell { text-align: center; color: var(--text-dim); padding: 20px; }
.loader { text-align: center; color: var(--text-dim); padding: 20px; }
</style>
