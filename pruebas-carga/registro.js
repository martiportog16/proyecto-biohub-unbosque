// =====================================================================
// PR-13 (RNF-09, HU-11 criterio 3) · paquete 13, Sergio
//
// Mide el tiempo del REGISTRO de hallazgos. Se corre dos veces con
// scripts/prueba-pr13.sh: sin el batch y con el batch calculando informes
// sobre 1 millón de hallazgos. El p95 con batch debe subir ≤ 1,0 s.
//
// Variables: URL, DURACION (por defecto 2m), TASA (5 por segundo), ETIQUETA.
// =====================================================================
import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const URL = __ENV.URL || 'http://nginx';
const errorRegistro = new Rate('error_registro');

export const options = {
    scenarios: {
        registro: {
            executor: 'constant-arrival-rate', rate: Number(__ENV.TASA || 5), timeUnit: '1s',
            duration: __ENV.DURACION || '2m', preAllocatedVUs: 10, maxVUs: 60,
        },
    },
    thresholds: { 'error_registro': ['rate==0'] },
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
    const login = http.post(`${URL}/api/cientifico/auth/login`,
        JSON.stringify({ correo: 'investigador@biohub.co', clave: 'BioHub2026!' }),
        { headers: { 'Content-Type': 'application/json' } });
    const especies = http.get(`${URL}/api/publico/especies`).json();
    return { token: login.json('token'), turdus: especies.find((e) => e.nombreCientifico === 'Turdus fuscater').id };
}

// Fecha de AYER: la API rechaza fechas futuras en hora de Colombia, y "hoy" en UTC
// ya es "mañana" en Colombia desde las 7 p. m. Ayer siempre es válida.
function ayer() {
    return new Date(Date.now() - 24 * 3600 * 1000).toISOString().slice(0, 10);
}

export default function (datos) {
    const r = http.post(`${URL}/api/cientifico/hallazgos`, JSON.stringify({
        especieId: datos.turdus, fechaObservacion: ayer(), departamento: 'Boyacá',
        valores: { numero_individuos: 3 }, coordenada: { latitud: 5.5, longitud: -73.3 },
    }), { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${datos.token}` } });
    errorRegistro.add(r.status !== 201);
    check(r, { 'registro responde 201': (x) => x.status === 201 });
}
