<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import LoginView from './views/LoginView.vue';
import CharacterSelection from './views/CharacterSelection.vue';
import PersonajeView from './views/PersonajeView.vue';
import ClanesView from './views/ClanesView.vue';
import RaidsView from './views/RaidsView.vue';
import StatsView from './views/StatsView.vue';
import { personajeService, clanService, notificacionService, type Notificacion } from './services/api';
import { fechaHoraCL } from './utils/format';

const currentStep = ref<'login' | 'select' | 'main'>('login');
const currentView = ref<'personaje' | 'clanes' | 'raids' | 'stats'>('personaje');

// Solo los Raiders (y el Guild Master) participan en raids: los Member no ven la pestaña.
const puedeVerRaids = computed(() => {
  const rol = session.value.character?.rolClan;
  return rol === 'Guild Master' || rol === 'Raider';
});

const session = ref({ token: '', username: '', rol: '', character: null as any });

// ---- Notificaciones (globales: disponibles en todas las pestañas) ----
const showNotif = ref(false);
const notificaciones = ref<Notificacion[]>([]);
const notifCount = ref(0);
const notifError = ref('');

async function cargarNotifCount() {
  if (!session.value.character?.idPersonaje) return;
  try {
    const d: any = await notificacionService.noLeidas(session.value.character.idPersonaje);
    notifCount.value = d?.count ?? 0;
  } catch { notifCount.value = 0; }
}

async function abrirNotif() {
  showNotif.value = true; notifError.value = '';
  try { notificaciones.value = await notificacionService.mias(session.value.character.idPersonaje); }
  catch (e: any) { notifError.value = e.message; }
}

async function marcarLeida(id: number) {
  try {
    await notificacionService.marcarLeida(id);
    const n = notificaciones.value.find(x => x.id_notificacion === id);
    if (n) n.leida = true;
    await cargarNotifCount();
  } catch (e: any) { notifError.value = e.message; }
}

async function marcarTodas() {
  try {
    await notificacionService.marcarTodas(session.value.character.idPersonaje);
    notificaciones.value = notificaciones.value.map(n => ({ ...n, leida: true }));
    await cargarNotifCount();
  } catch (e: any) { notifError.value = e.message; }
}

// ---- Refresco del personaje (DKP / item level despues de canjear o equipar) ----
async function refrescarPersonaje() {
  if (!session.value.character?.idPersonaje) return;
  try {
    const p: any = await personajeService.getById(session.value.character.idPersonaje);
    let nombreClan: string | null = null;
    if (p.idClan) {
      try { nombreClan = (await clanService.getById(p.idClan)).nombreClan; } catch { nombreClan = null; }
    }
    session.value.character = { ...p, nombreClan };
  } catch { /* se mantiene el personaje actual */ }
  await cargarNotifCount();
}

const onLogin = (data: { token: string; user: string; rol: string }) => {
  session.value.token = data.token;
  session.value.username = data.user;
  session.value.rol = data.rol;
  currentStep.value = 'select';
  localStorage.setItem('auth_token', data.token);
};

const onCharacterActive = async (char: any) => {
  session.value.character = char;
  currentStep.value = 'main';
  currentView.value = 'personaje';
  await cargarNotifCount();
};

watch(puedeVerRaids, (puede) => {
  if (!puede && currentView.value === 'raids') currentView.value = 'personaje';
});

const onBackToSelect = () => { currentStep.value = 'select'; };

const onLogout = () => {
  localStorage.removeItem('auth_token');
  session.value = { token: '', username: '', rol: '', character: null };
  notifCount.value = 0; notificaciones.value = [];
  currentStep.value = 'login';
};
</script>

<template>
  <div id="app-wrapper">
    <LoginView v-if="currentStep === 'login'" @auth-success="onLogin" />

    <CharacterSelection
      v-else-if="currentStep === 'select'"
      :token="session.token"
      :username="session.username"
      @char-selected="onCharacterActive"
      @logout="onLogout"
    />

    <div v-else-if="currentStep === 'main'" class="dashboard-layout">
      <aside class="sidebar">
        <div class="sidebar-header">
          <div class="logo">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 2 7 12 12 22 7 12 2"></polygon><polyline points="2 17 12 22 22 17"></polyline><polyline points="2 12 12 17 22 12"></polyline></svg>
          </div>
          <h2>MMORPG</h2>
        </div>

        <nav class="sidebar-nav">
          <button :class="{ active: currentView === 'personaje' }" @click="currentView = 'personaje'">Mi Personaje</button>
          <button :class="{ active: currentView === 'clanes' }" @click="currentView = 'clanes'">Mi Clan</button>
          <button v-if="puedeVerRaids" :class="{ active: currentView === 'raids' }" @click="currentView = 'raids'">Raids</button>
          <button :class="{ active: currentView === 'stats' }" @click="currentView = 'stats'">Mundo</button>
        </nav>

        <div class="sidebar-footer">
          <button class="btn-notif" @click="abrirNotif">
            Notificaciones<span v-if="notifCount > 0" class="badge">{{ notifCount }}</span>
          </button>
          <div class="user-info">
            <span class="ui-name">{{ session.character?.nombrePersonaje || session.username }}</span>
            <span class="ui-clan">{{ session.character?.nombreClan || 'Sin clan' }}</span>
          </div>
          <button class="btn-logout" @click="onBackToSelect" style="margin-bottom: 8px;">Cambiar personaje</button>
          <button class="btn-logout" @click="onLogout">Cerrar sesion</button>
        </div>
      </aside>

      <main class="main-content">
        <PersonajeView v-if="currentView === 'personaje'" :character="session.character" :token="session.token" @refrescar="refrescarPersonaje" />
        <ClanesView v-else-if="currentView === 'clanes'" :character="session.character" :token="session.token" @refrescar="refrescarPersonaje" />
        <RaidsView v-else-if="currentView === 'raids'" :character="session.character" :token="session.token" @refrescar="refrescarPersonaje" />
        <StatsView v-else-if="currentView === 'stats'" :character="session.character" :token="session.token" />
      </main>

      <!-- Notificaciones: disponibles desde cualquier pestaña -->
      <div v-if="showNotif" class="modal-overlay" @click.self="showNotif = false">
        <div class="modal-notif">
          <div class="notif-head">
            <h3>Notificaciones</h3>
            <button class="btn-mini" @click="marcarTodas">Marcar todas como leidas</button>
          </div>
          <p v-if="notifError" class="notif-err">{{ notifError }}</p>
          <div class="notif-list">
            <div v-for="n in notificaciones" :key="n.id_notificacion" class="notif-item" :class="{ unread: !n.leida }">
              <div class="notif-content">
                <span class="notif-type">{{ n.tipo }}</span>
                <p>{{ n.mensaje }}</p>
                <span class="notif-date">{{ fechaHoraCL(n.fecha) }}</span>
              </div>
              <button v-if="!n.leida" class="btn-mini" @click="marcarLeida(n.id_notificacion)">Marcar leida</button>
            </div>
            <div v-if="notificaciones.length === 0" class="notif-empty">Sin notificaciones</div>
          </div>
          <div class="notif-actions"><button class="btn-secondary" @click="showNotif = false">Cerrar</button></div>
        </div>
      </div>
    </div>
  </div>
</template> <style>
@import url('https://fonts.googleapis.com/css2?family=Cinzel:wght@400;700&family=Nunito:wght@400;600;700&display=swap');

:root {
  --gold: #a9791f;
  --gold-dim: #8a5f16;
  --bg-dark: #f4f1ea;
  --bg-main: #efe9dd;
  --bg-card: #ffffff;
  --border: #ddd6c8;
  --text: #2b2b28;
  --text-dim: #6f6c64;
  --purple: #6a3fd0;
  --red: #c62828;
  --green: #2f8f3e;
  --blue: #1f6feb;
  --legendary: #cc7a00;
  --epic: #8a2be2;
  --rare: #0059b3;
  --common: #6b6b6b;
  
  --primary: var(--gold);
  --primary-hover: var(--gold-dim);
}

* { box-sizing: border-box; margin: 0; padding: 0; }

body { 
  background: var(--bg-dark); 
  color: var(--text); 
  font-family: 'Nunito', sans-serif;
  height: 100vh;
  overflow: hidden;
}

h1, h2, h3 { font-family: 'Cinzel', serif; color: var(--gold); font-weight: 700; }

::-webkit-scrollbar { width: 6px; }
::-webkit-scrollbar-track { background: var(--bg-dark); }
::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }

#app-wrapper { height: 100vh; }

.dashboard-layout {
  display: flex;
  height: 100vh;
  background: var(--bg-dark);
}

.sidebar {
  width: 260px;
  background: var(--bg-card);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--border);
}

.sidebar-header .logo {
  background: var(--gold);
  width: 32px; height: 32px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center; color: white;
}
.sidebar-header h2 { font-size: 20px; }

.sidebar-nav {
  padding: 24px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-grow: 1;
}

.sidebar-nav button {
  background: transparent;
  color: var(--text-dim);
  border: none;
  padding: 12px 16px;
  border-radius: 8px;
  text-align: left;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  font-family: 'Nunito', sans-serif;
}

.sidebar-nav button:hover:not(.active) { background: var(--bg-main); color: var(--text); }
.sidebar-nav button.active { background: var(--gold); color: white; box-shadow: 0 4px 12px rgba(169, 121, 31, 0.3); }

.sidebar-footer {
  padding: 24px 16px;
  border-top: 1px solid var(--border);
}

.user-info { margin-bottom: 12px; font-size: 14px; color: var(--text-dim); text-align: center; font-weight: 700; }

.btn-logout {
  width: 100%;
  background: rgba(198, 40, 40, 0.1);
  color: var(--red);
  border: 1px solid rgba(198, 40, 40, 0.2);
  padding: 10px;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  font-family: 'Nunito', sans-serif;
}
.btn-logout:hover { background: rgba(198, 40, 40, 0.2); }

.main-content {
  flex-grow: 1;
  overflow-y: auto;
  padding: 0;
  position: relative;
  background: var(--bg-main);
}

/* Utils shared */
.btn-primary { background: var(--gold); color: white; border: none; padding: 10px 20px; border-radius: 8px; font-weight: 700; cursor: pointer; transition: background 0.2s; font-family: 'Nunito', sans-serif;}
.btn-primary:hover { background: var(--gold-dim); }
.btn-secondary { background: var(--bg-card); color: var(--text); border: 1px solid var(--border); padding: 10px 20px; border-radius: 8px; font-weight: 700; cursor: pointer; transition: background 0.2s; font-family: 'Nunito', sans-serif;}
.btn-secondary:hover { background: var(--bg-main); } .user-info { display: flex; flex-direction: column; gap: 2px; margin-bottom: 10px; }
.ui-name { font-weight: 700; color: var(--text); }
.ui-clan { font-size: 12px; color: var(--gold); }

.btn-notif { width: 100%; background: var(--bg-main); border: 1px solid var(--border); color: var(--text); padding: 10px; border-radius: 8px; font-weight: 700; cursor: pointer; margin-bottom: 12px; font-family: 'Nunito', sans-serif; }
.btn-notif:hover { border-color: var(--gold); }
.badge { background: var(--red); color: #fff; border-radius: 10px; padding: 1px 7px; font-size: 11px; margin-left: 6px; }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 2000; padding: 20px; }
.modal-notif { background: var(--bg-card); border: 1px solid var(--border); border-radius: 12px; padding: 24px; width: 640px; max-height: 88vh; overflow-y: auto; }
.notif-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 10px; }
.notif-err { color: var(--red); font-size: 13px; margin-bottom: 8px; }
.notif-list { display: flex; flex-direction: column; gap: 8px; }
.notif-item { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; background: var(--bg-main); border: 1px solid var(--border); border-radius: 10px; padding: 12px; }
.notif-item.unread { border-color: var(--gold); background: rgba(169,121,31,0.07); }
.notif-content { flex: 1; }
.notif-type { font-size: 11px; color: var(--gold); font-weight: 700; letter-spacing: 0.5px; }
.notif-content p { margin: 4px 0; font-size: 14px; }
.notif-date { font-size: 12px; color: var(--text-dim); }
.notif-empty { color: var(--text-dim); text-align: center; padding: 24px; }
.notif-actions { display: flex; justify-content: flex-end; margin-top: 16px; }
.btn-mini { background: transparent; border: 1px solid var(--border); color: var(--text-dim); padding: 5px 12px; border-radius: 6px; cursor: pointer; font-size: 12px; font-family: 'Nunito', sans-serif; }
</style>
