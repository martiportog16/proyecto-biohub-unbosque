// Paquete 20: mapa de coordenadas (HU-08) y registro de accesos para el auditor (RNF-06).
const { test, expect } = require('@playwright/test');
const { USUARIOS, entrarComo, api, vigilarErrores } = require('./ayuda');

const MONO = 1;   // Ateles hybridus: EN PELIGRO
const MIRLA = 3;  // Turdus fuscater: fuera de peligro

/** Registra un hallazgo de esa especie como investigador, para que la prueba tenga al menos un punto. */
async function hallazgoDe(page, especie) {
  await entrarComo(page, USUARIOS.investigador);
  const ficha = (await api(page, 'GET', '/especies/' + especie + '/ficha')).cuerpo;
  const valores = {};
  for (const a of ficha.atributos.filter((x) => x.activo && x.obligatorio)) {
    valores[a.nombre] = a.tipo === 'NUMERO' ? 1 : a.tipo === 'LISTA' ? a.opciones[0] : 'dato';
  }
  const ayer = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
  const r = await api(page, 'POST', '/hallazgos', { especieId: especie, fechaObservacion: ayer, departamento: 'Meta', valores,
    coordenada: { latitud: 3.987654, longitud: -73.765432, precisionMetros: 12 } });
  expect(r.estado).toBe(201);
  await page.getByRole('button', { name: 'Salir' }).click();
  return r.cuerpo.id;
}

test('el alto rango ve en el mapa una especie en peligro, sin pedir nada a sitios externos', async ({ page, baseURL }) => {
  await hallazgoDe(page, MONO);
  const errores = vigilarErrores(page);
  const externas = [];
  page.on('request', (r) => { if (!r.url().startsWith(baseURL)) externas.push(r.url()); });

  await entrarComo(page, USUARIOS.altoRango);
  await page.getByRole('navigation').getByRole('link', { name: 'Mapa de coordenadas' }).click();
  await page.getByLabel('Especie').selectOption(String(MONO));
  await page.getByRole('button', { name: 'Ver en el mapa' }).click();
  await expect(page.getByText('Especie en peligro: no comparta estas coordenadas.')).toBeVisible();
  // Los departamentos de fondo y al menos un punto
  await expect(page.locator('.leaflet-overlay-pane path').first()).toBeVisible();
  expect(await page.locator('.leaflet-overlay-pane path').count()).toBeGreaterThan(33);
  await page.getByText('Ver los puntos en una tabla').click();
  await expect(page.getByRole('cell', { name: '3.987654' }).first()).toBeVisible();
  expect(externas).toEqual([]);
  expect(errores).toEqual([]);
});

test('el investigador no ve la especie en peligro: la autorización lo niega y lo dice', async ({ page }) => {
  await entrarComo(page, USUARIOS.investigador);
  await page.goto('/consola/#/mapa');
  await page.getByLabel('Especie').selectOption(String(MONO));
  await page.getByRole('button', { name: 'Ver en el mapa' }).click();
  await expect(page.getByRole('alert')).toContainText('solo las ve el personal científico de alto rango');
  await expect(page.getByRole('alert')).toContainText('quedó en el registro de accesos');
  await expect(page.locator('.leaflet-container')).toHaveCount(0);
});

test('el investigador sí ve una especie fuera de peligro, y la ubicación exacta desde el hallazgo', async ({ page }) => {
  expect((await (await page.request.get('/api/publico/especies/' + MIRLA)).json()).enPeligro).toBe(false);
  const id = await hallazgoDe(page, MIRLA);
  await entrarComo(page, USUARIOS.investigador);
  await page.goto('/consola/#/mapa/' + MIRLA);
  await page.getByRole('button', { name: 'Ver en el mapa' }).click();
  await expect(page.getByText('Su consulta quedó registrada.')).toBeVisible();
  await expect(page.locator('.leaflet-container')).toBeVisible();

  await page.goto('/consola/#/hallazgos/' + id);
  await expect(page.locator('main')).not.toContainText('3.987654');   // no se muestra hasta pedirla
  await page.getByRole('button', { name: 'Ver la ubicación exacta' }).click();
  await expect(page.locator('main code', { hasText: '3.987654, -73.765432' })).toBeVisible();
});

test('el auditor ve la consulta negada en el registro de accesos, con el motivo', async ({ page }) => {
  // Un intento negado: investigador pidiendo la coordenada de una especie en peligro
  const id = await hallazgoDe(page, MONO);
  await entrarComo(page, USUARIOS.investigador);
  expect((await api(page, 'GET', '/hallazgos/' + id + '/coordenada')).estado).toBe(403);
  await page.getByRole('button', { name: 'Salir' }).click();

  await entrarComo(page, USUARIOS.auditor);
  await page.getByRole('navigation').getByRole('link', { name: 'Accesos a coordenadas' }).click();
  const primera = page.locator('tbody tr').first();
  await expect(primera).toContainText('Hallazgo #' + id);
  await expect(primera).toContainText('Negado');
  await expect(primera).toContainText('Especie en peligro: exige alto rango');
  await expect(primera).not.toContainText('3.98');   // el registro nunca muestra la coordenada

  await page.getByLabel('Solo los negados').check();
  await expect(page).toHaveURL(/negados=1/);
  await expect(page.getByLabel('Solo los negados')).toBeChecked();   // ya se volvió a dibujar
  await expect(page.locator('tbody tr').first()).toContainText('Negado');
  const estados = await page.locator('tbody tr td:nth-child(6)').allTextContents();
  expect(estados.length).toBeGreaterThan(0);
  for (const e of estados) expect(e).toBe('Negado');
});

test('solo el auditor ve el registro de accesos, y el analista no ve el mapa', async ({ page }) => {
  await entrarComo(page, USUARIOS.altoRango);
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Accesos a coordenadas' })).toHaveCount(0);
  expect((await api(page, 'GET', '/coordenadas/accesos')).estado).toBe(403);
  await page.getByRole('button', { name: 'Salir' }).click();
  await entrarComo(page, USUARIOS.analista);
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Mapa de coordenadas' })).toHaveCount(0);
});
