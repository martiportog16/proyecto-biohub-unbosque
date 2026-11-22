// =====================================================================
// PR-02 y PR-03 (RNF-02, RNF-03, RNF-04, HU-04, HU-05) · paquete 13, Sergio
//
// Simula el PICO: 60 veces el tráfico habitual del portal (P-12: 5 → 300 peticiones/s)
// durante 5 minutos, y AL MISMO TIEMPO investigadores registrando hallazgos.
//
//   Escenario "portal":   300 peticiones/s a fichas públicas y búsquedas.
//   Escenario "registro": 5 hallazgos/s por la API científica.
//
// Criterios (se revisan solos al final; si alguno falla, k6 termina con error):
//   PR-02  ficha pública p95 ≤ 1,0 s (P-13) y NINGÚN minuto con más del 1 % de "caídas"
//          (P-16: respuesta 5xx o que tarda más de 5 s).
//   PR-03  registro p95 ≤ 1,0 s y 0 errores durante el pico.
//
// Se corre con scripts/carga.sh (ver docs/guia-pruebas-carga.md).
// Variables: URL, DURACION (por defecto 5m), TASA_PORTAL (300), TASA_REGISTRO (5).
// =====================================================================
import http from 'k6/http';
import { check } from 'k6';
import { Rate, Counter } from 'k6/metrics';

const URL = __ENV.URL || 'http://nginx';
const DURACION = __ENV.DURACION || '5m';
const TASA_PORTAL = Number(__ENV.TASA_PORTAL || 300);
const TASA_REGISTRO = Number(__ENV.TASA_REGISTRO || 5);
const MINUTOS = parseInt(DURACION) + 1;

// P-16: una "caída" es una respuesta 5xx o que tarda más de 5 s. Se cuenta por minuto.
const caidaPortal = new Rate('caida_portal');
const rechazos429 = new Counter('rechazos_429_portal');
const errorRegistro = new Rate('error_registro');

const umbralesPorMinuto = {};
for (let m = 0; m < MINUTOS; m++) {
    umbralesPorMinuto[`caida_portal{minuto:${m}}`] = ['rate<0.01'];
}

export const options = {
    scenarios: {
        portal: {
            executor: 'constant-arrival-rate', rate: TASA_PORTAL, timeUnit: '1s', duration: DURACION,
            preAllocatedVUs: 100, maxVUs: 600, exec: 'portal',
        },
        registro: {
            executor: 'constant-arrival-rate', rate: TASA_REGISTRO, timeUnit: '1s', duration: DURACION,
            preAllocatedVUs: 10, maxVUs: 60, exec: 'registro',
        },
    },
    thresholds: Object.assign({
        'http_req_duration{grupo:portal}': ['p(95)<1000'],      // PR-02
        'http_req_duration{grupo:registro}': ['p(95)<1000'],    // PR-03
        'error_registro': ['rate==0'],                          // PR-03: 0 errores
    }, umbralesPorMinuto),
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
    const login = http.post(`${URL}/api/cientifico/auth/login`,
        JSON.stringify({ correo: 'investigador@biohub.co', clave: 'BioHub2026!' }),
        { headers: { 'Content-Type': 'application/json' } });
    check(login, { 'login del investigador': (r) => r.status === 200 });
    const especies = http.get(`${URL}/api/publico/especies`).json();
    const turdus = especies.find((e) => e.nombreCientifico === 'Turdus fuscater');
    return { token: login.json('token'), especies: especies.map((e) => e.id), turdus: turdus.id, inicio: Date.now() };
}

function minuto(datos) {
    return String(Math.floor((Date.now() - datos.inicio) / 60000));
}

export function portal(datos) {
    // 80 % abre una ficha (sobre todo las más visitadas), 20 % busca por nombre
    let r;
    if (Math.random() < 0.8) {
        const id = datos.especies[Math.floor(Math.pow(Math.random(), 2) * datos.especies.length)];
        r = http.get(`${URL}/api/publico/especies/${id}`, { tags: { grupo: 'portal', name: 'ficha' } });
    } else {
        const texto = ['oso', 'mono', 'mirla', 'especie', 'turdus'][Math.floor(Math.random() * 5)];
        r = http.get(`${URL}/api/publico/especies?buscar=${texto}`, { tags: { grupo: 'portal', name: 'busqueda' } });
    }
    const caida = r.status >= 500 || r.status === 0 || r.timings.duration > 5000;
    caidaPortal.add(caida, { minuto: minuto(datos) });
    if (r.status === 429) {
        rechazos429.add(1);
    }
    check(r, { 'portal responde 200': (x) => x.status === 200 });
}

// Fecha de AYER: la API rechaza fechas futuras en hora de Colombia, y "hoy" en UTC
// ya es "mañana" en Colombia desde las 7 p. m. Ayer siempre es válida.
function ayer() {
    return new Date(Date.now() - 24 * 3600 * 1000).toISOString().slice(0, 10);
}

export function registro(datos) {
    const hoy = ayer();
    const r = http.post(`${URL}/api/cientifico/hallazgos`, JSON.stringify({
        especieId: datos.turdus, fechaObservacion: hoy, departamento: 'Cundinamarca',
        valores: { numero_individuos: 1 + Math.floor(Math.random() * 10) },
        coordenada: { latitud: 4.6 + Math.random() / 10, longitud: -74.1 + Math.random() / 10 },
    }), { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${datos.token}` },
          tags: { grupo: 'registro', name: 'registrar' } });
    errorRegistro.add(r.status !== 201);
    check(r, { 'registro responde 201': (x) => x.status === 201 });
}
