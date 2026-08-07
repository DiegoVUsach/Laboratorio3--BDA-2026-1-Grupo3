// Formato de fechas chileno (dd/mm/aaaa) para toda la interfaz.
const RE_ISO_SIMPLE = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/;

function parse(valor: any): Date | null {
  if (!valor) return null;
  if (valor instanceof Date) return isNaN(valor.getTime()) ? null : valor;
  const s = String(valor);
  const m = s.match(RE_ISO_SIMPLE);
  if (m) return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]);
  const soloFecha = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (soloFecha) return new Date(+soloFecha[1], +soloFecha[2] - 1, +soloFecha[3]);
  const d = new Date(s);
  return isNaN(d.getTime()) ? null : d;
}

const dosDigitos = (n: number) => String(n).padStart(2, '0');

/** dd/mm/aaaa */
export function fechaCL(valor: any): string {
  const d = parse(valor);
  if (!d) return '-';
  return `${dosDigitos(d.getDate())}/${dosDigitos(d.getMonth() + 1)}/${d.getFullYear()}`;
}

/** dd/mm/aaaa HH:MM */
export function fechaHoraCL(valor: any): string {
  const d = parse(valor);
  if (!d) return '-';
  return `${fechaCL(d)} ${dosDigitos(d.getHours())}:${dosDigitos(d.getMinutes())}`;
}

/** Convierte una fecha escrita como dd/mm/aaaa a ISO (aaaa-mm-dd). Null si es invalida. */
export function clAIso(texto: string): string | null {
  const m = (texto || '').trim().match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (!m) return null;
  const dia = +m[1], mes = +m[2], anio = +m[3];
  if (mes < 1 || mes > 12 || dia < 1 || dia > 31) return null;
  const d = new Date(anio, mes - 1, dia);
  if (d.getDate() !== dia || d.getMonth() !== mes - 1) return null;
  return `${anio}-${dosDigitosPub(mes)}-${dosDigitosPub(dia)}`;
}
const dosDigitosPub = (n: number) => String(n).padStart(2, '0');
