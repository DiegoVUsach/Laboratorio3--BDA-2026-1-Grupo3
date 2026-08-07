<script setup lang="ts">
import { ref } from 'vue';
import { authService } from '../services/api';

const emit = defineEmits(['auth-success']);
const mode = ref<'login' | 'register'>('login');
const username = ref('');
const password = ref('');
const errorMsg = ref('');
const successMsg = ref('');
const loading = ref(false);

const handleLogin = async () => {
  if (!username.value || !password.value) { errorMsg.value = 'Completa todos los campos'; return; }
  loading.value = true; errorMsg.value = '';
  try {
    await authService.login(username.value, password.value);
    const me = await authService.getMe();
    emit('auth-success', { token: localStorage.getItem('auth_token'), user: username.value, rol: me.rol });
  } catch (err: any) {
    errorMsg.value = err.message || 'Credenciales incorrectas';
  } finally { loading.value = false; }
};

const handleRegister = async () => {
  if (!username.value || !password.value) { errorMsg.value = 'Completa todos los campos'; return; }
  loading.value = true; errorMsg.value = ''; successMsg.value = '';
  try {
    await authService.register({ nombreUsuario: username.value, password: password.value });
    successMsg.value = 'Cuenta creada. Ahora inicia sesión.';
    mode.value = 'login';
    password.value = '';
  } catch (err: any) {
    errorMsg.value = err.message || 'Error al registrar';
  } finally { loading.value = false; }
};

const submit = () => mode.value === 'login' ? handleLogin() : handleRegister();
</script> <template> <div class="login-page"> <div class="login-card"> <div class="emblem"><svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="#a9791f" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2 L4 6 v6 c0 5 3.5 8.5 8 10 4.5-1.5 8-5 8-10 V6 Z"/></svg></div> <h1>CLAN SYSTEM</h1> <p class="subtitle">Gestor de Clanes y Raids</p> <div class="tab-row"> <button :class="{ active: mode === 'login' }" @click="mode = 'login'; errorMsg = ''; successMsg = ''">Iniciar Sesión</button> <button :class="{ active: mode === 'register' }" @click="mode = 'register'; errorMsg = ''; successMsg = ''">Registrarse</button> </div> <div class="form"> <label>Usuario</label> <input v-model="username" type="text" placeholder="Tu nombre de usuario" @keyup.enter="submit"> <label>Contraseña</label> <input v-model="password" type="password" placeholder="••••••••" @keyup.enter="submit"> <p v-if="errorMsg" class="msg error">{{ errorMsg }}</p> <p v-if="successMsg" class="msg success">{{ successMsg }}</p> <button class="btn-gold" :disabled="loading" @click="submit"> {{ loading ? 'PROCESANDO...' : (mode === 'login' ? 'ENTRAR' : 'CREAR CUENTA') }} </button> </div> </div> </div>
</template> <style scoped>
.login-page { height: 100vh; display: flex; align-items: center; justify-content: center; background: radial-gradient(ellipse at center, #efe9dd 0%, #f4f1ea 70%); }
.login-card { background: var(--bg-card); padding: 40px; border-radius: 12px; border: 1px solid var(--border); width: 380px; text-align: center; }
.emblem { font-size: 48px; margin-bottom: 8px; }
h1 { margin-bottom: 4px; font-size: 26px; letter-spacing: 2px; }
.subtitle { color: var(--text-dim); font-size: 13px; margin-bottom: 24px; }
.tab-row { display: flex; gap: 0; margin-bottom: 24px; border: 1px solid var(--border); border-radius: 6px; overflow: hidden; }
.tab-row button { flex: 1; padding: 10px; background: transparent; border: none; color: var(--text-dim); cursor: pointer; font-size: 13px; font-weight: 600; transition: all 0.2s; }
.tab-row button.active { background: var(--gold-dim); color: #000; }
.form { display: flex; flex-direction: column; text-align: left; }
label { font-size: 11px; color: var(--text-dim); margin-bottom: 4px; text-transform: uppercase; letter-spacing: 1px; }
input { background: var(--bg-dark); border: 1px solid var(--border); color: var(--text); padding: 12px; border-radius: 6px; margin-bottom: 16px; font-size: 14px; outline: none; transition: border 0.2s; }
input:focus { border-color: var(--gold); }
.btn-gold { background: var(--gold); color: #000; border: none; padding: 13px; border-radius: 6px; font-weight: 700; cursor: pointer; font-size: 14px; letter-spacing: 1px; transition: opacity 0.2s; margin-top: 4px; }
.btn-gold:hover { opacity: 0.9; }
.btn-gold:disabled { opacity: 0.4; cursor: not-allowed; }
.msg { font-size: 13px; text-align: center; margin-bottom: 12px; padding: 8px; border-radius: 6px; }
.msg.error { color: var(--red); background: #fdecea; }
.msg.success { color: var(--green); background: rgba(63,185,80,0.1); }
</style>
