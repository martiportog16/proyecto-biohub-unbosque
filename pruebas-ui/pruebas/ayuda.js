// Utilidades comunes a todas las pruebas de la consola.
const { expect } = require('@playwright/test');

const CLAVE = 'BioHub2026!';

const USUARIOS = {
  investigador: { correo: 'investigador@biohub.co', nombre: 'Ana Investigadora', rol: 'Investigador' },
  adminCientifico: { correo: 'admin.cientifico@biohub.co', nombre: 'Luis Administrador', rol: 'Administrador científico' },
  director: { correo: 'director@biohub.co', nombre: 'Marta Directora', rol: 'Director científico' },
  auditor: { correo: 'auditor@biohub.co', nombre: 'Jorge Auditor', rol: 'Auditor científico' },
  altoRango: { correo: 'altorango@biohub.co', nombre: 'Elena Alto Rango', rol: 'Personal de alto rango' },
  analista: { correo: 'analista@biohub.co', nombre: 'Pedro Analista', rol: 'Analista' },
  adminPlataforma: { correo: 'admin.plataforma@biohub.co', nombre: 'Sofía Plataforma', rol: 'Administrador de la plataforma' }
};

/** Entra a la consola como ese usuario y espera la pantalla de inicio. */
async function entrarComo(page, usuario) {
  await page.goto('/consola/');
  await page.getByLabel('Correo institucional').fill(usuario.correo);
  await page.getByLabel('Clave').fill(CLAVE);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.locator('#rol-usuario')).toHaveText(usuario.rol);
}

/** Falla la prueba si la página viola la política de seguridad (CSP) o lanza un error de JavaScript. */
function vigilarErrores(page) {
  const errores = [];
  page.on('pageerror', (e) => errores.push('JavaScript: ' + e.message));
  page.on('console', (m) => {
    if (m.type() === 'error' && /Content Security Policy|Refused to/.test(m.text())) errores.push('CSP: ' + m.text());
  });
  return errores;
}

/** Token de la sesión actual, para preparar datos por la API en la misma prueba. */
async function token(page) {
  return page.evaluate(() => JSON.parse(sessionStorage.getItem('biohub.sesion')).token);
}

/** Llama a la API científica con el token del navegador (para preparar datos). */
async function api(page, metodo, ruta, cuerpo) {
  const t = await token(page);
  const r = await page.request.fetch('/api/cientifico' + ruta, {
    method: metodo,
    headers: { Authorization: 'Bearer ' + t, 'Content-Type': 'application/json' },
    data: cuerpo === undefined ? undefined : JSON.stringify(cuerpo)
  });
  const texto = await r.text();
  return { estado: r.status(), cuerpo: texto ? JSON.parse(texto) : null };
}

function unico(prefijo) {
  return prefijo + ' ' + Date.now().toString(36) + Math.floor(Math.random() * 1000);
}

module.exports = { CLAVE, USUARIOS, entrarComo, vigilarErrores, token, api, unico };
