// Tipos del mundo virtual "Aethermoor" (Lab 2)
export interface WorldMarker {
  x: number;
  y: number;
  label: string;
  popup?: string;
  color?: string;
  kind?: 'clan' | 'raid' | 'boss' | 'jugador' | 'sede' | 'pick' | 'lider' | 'yo';
  highlight?: boolean;
  permanent?: boolean;   // etiqueta siempre visible
  radius?: number;        // circulo en metros del mundo (ej. 50 del jefe)
  radiusColor?: string;
  radiusOpacity?: number; // opacidad del relleno (mapa de calor)
  radiusSolido?: boolean; // borde continuo en vez de punteado
}
