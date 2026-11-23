// Paquete 19: reclasificación (HU-06) e historial con comparación por fecha (HU-07).
const { test, expect } = require('@playwright/test');
const { USUARIOS, entrarComo, api, vigilarErrores } = require('./ayuda');

/** Registra un hallazgo nuevo de la especie 2 (Tremarctos ornatus) como investigador y devuelve su id. */
async function hallazgoNuevo(page) {
  await entrarComo(page, USUARIOS.investigador);
  const ficha = (await api(page, 'GET', '/especies/2/ficha')).cuerpo;
  const valores = {};
  for (const a of ficha.atributos.filter((x) => x.activo && x.obligatorio)) {
    valores[a.nombre] = a.tipo === 'NUMERO' ? 1 : a.tipo === 'LISTA' ? a.opciones[0] : a.tipo === 'FECHA' ? '2026-01-01'
      : a.tipo === 'RUTA_GPS' ? [{ lat: 4, lon: -74 }, { lat: 4.1, lon: -74.1 }] : a.tipo === 'ARCHIVO' ? 'foto.jpg' : 'dato';
  }
  const ayer = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
  const r = await api(page, 'POST', '/hallazgos', { especieId: 2, fechaObservacion: ayer, departamento: 'Boyacá', valores,
    coordenada: { latitud: 5.5, longitud: -73.3 } });
  expect(r.estado).toBe(201);
  await page.getByRole('button', { name: 'Salir' }).click();
  return r.cuerpo.id;
}

test('el director reclasifica desde el detalle del hallazgo y el cambio queda en la línea de tiempo', async ({ page }) => {
  const errores = vigilarErrores(page);
  const id = await hallazgoNuevo(page);
  await entrarComo(page, USUARIOS.director);
  await page.goto('/consola/#/hallazgos/' + id);
  await page.getByRole('link', { name: 'Historial y reclasificar' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Historial del hallazgo #' + id);
  await expect(page.locator('.cifra').first()).toContainText('Tremarctos ornatus');

  // Motivo muy corto: el backend lo rechaza y lo dice junto al campo
  await page.getByLabel('Nueva especie').selectOption('1');
  await page.getByLabel(/Motivo/).fill('ADN');
  await expect(page.getByText('Faltan 7 caracteres (mínimo 10).')).toBeVisible();
  await page.getByRole('button', { name: 'Reclasificar' }).click();
  await expect(page.locator('[data-error="motivo"]')).toContainText('mínimo 10');

  await page.getByLabel(/Motivo/).fill('Análisis de ADN de la muestra M-0412');
  await page.getByRole('button', { name: 'Reclasificar' }).click();
  await expect(page.getByText('Reclasificado: Tremarctos ornatus → Ateles hybridus.')).toBeVisible();
  await expect(page.locator('.cifra').first()).toContainText('Ateles hybridus');
  const filas = page.locator('section', { hasText: 'Línea de tiempo' }).locator('tbody tr');
  await expect(filas).toHaveCount(2);
  await expect(filas.nth(1)).toContainText('Marta Directora');
  await expect(filas.nth(1)).toContainText('Análisis de ADN de la muestra M-0412');
  expect(errores).toEqual([]);
});

test('comparar con una fecha: antes del registro "no existía", hoy coincide con el estado actual', async ({ page }) => {
  const id = await hallazgoNuevo(page);
  await entrarComo(page, USUARIOS.auditor);
  await page.goto('/consola/#/historial/' + id);
  const ayer = new Date(Date.now() - 86400000 * 2).toISOString().slice(0, 10);
  await page.getByLabel('Fecha').fill(ayer);
  await page.getByRole('button', { name: 'Comparar con hoy' }).click();
  await expect(page.getByText(/todavía no existía/)).toBeVisible();

  const hoy = await page.evaluate(() => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date()));
  await page.getByLabel('Fecha').fill(hoy);
  await page.getByRole('button', { name: 'Comparar con hoy' }).click();
  await expect(page.getByText('No ha cambiado desde esa fecha.')).toBeVisible();
});

test('el auditor solo lee: no hay formulario para reclasificar y la API le responde 403', async ({ page }) => {
  const id = await hallazgoNuevo(page);
  await entrarComo(page, USUARIOS.auditor);
  await page.getByRole('navigation').getByRole('link', { name: 'Historial y reclasificación' }).click();
  await page.getByLabel('Número del hallazgo').fill(String(id));
  await page.getByRole('button', { name: 'Abrir' }).click();
  await expect(page.getByText(/Acceso de solo lectura/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Reclasificar' })).toHaveCount(0);
  const r = await api(page, 'POST', '/hallazgos/' + id + '/reclasificaciones', { especieNuevaId: 1, motivo: 'Intento del auditor sin permiso' });
  expect(r.estado).toBe(403);
});

test('el investigador no ve el historial ni en el menú ni en el detalle del hallazgo', async ({ page }) => {
  const id = await hallazgoNuevo(page);
  await entrarComo(page, USUARIOS.investigador);
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Historial y reclasificación' })).toHaveCount(0);
  await page.goto('/consola/#/hallazgos/' + id);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hallazgo #' + id);
  await expect(page.getByRole('link', { name: /historial/i })).toHaveCount(0);
});

test('un hallazgo que no existe lo dice claramente', async ({ page }) => {
  await entrarComo(page, USUARIOS.director);
  await page.goto('/consola/#/historial/999999999');
  await expect(page.getByText('No existe el hallazgo #999999999.')).toBeVisible();
});
