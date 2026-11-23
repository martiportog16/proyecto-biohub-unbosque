// Paquete 20: el administrador de la plataforma cambia límites en caliente (HU-10, criterio 4).
const { test, expect } = require('@playwright/test');
const { USUARIOS, entrarComo, api } = require('./ayuda');

test('cambiar el límite, validarlo y desactivar con confirmación; al final todo queda como estaba', async ({ page }) => {
  await entrarComo(page, USUARIOS.adminPlataforma);
  await page.getByRole('navigation').getByRole('link', { name: 'Consumidores externos' }).click();
  const consumidores = (await api(page, 'GET', '/consumidores')).cuerpo;
  const c = consumidores[consumidores.length - 1];
  const fila = page.locator('tbody tr', { hasText: c.nombre });
  const campo = fila.getByLabel('Límite por minuto de ' + c.nombre);

  try {
    // Un valor inválido no se envía
    let envios = 0;
    page.on('request', (r) => { if (r.method() === 'PATCH') envios++; });
    await campo.fill('0');
    await fila.getByRole('button', { name: 'Guardar' }).click();
    await expect(fila.getByText(/entero entre 1 y/)).toBeVisible();
    expect(envios).toBe(0);

    await campo.fill('37');
    await fila.getByRole('button', { name: 'Guardar' }).click();
    await expect(page.getByText(c.nombre + ' ahora puede hacer 37 peticiones por minuto.')).toBeVisible();
    await page.reload();
    await expect(page.locator('tbody tr', { hasText: c.nombre }).getByLabel('Límite por minuto de ' + c.nombre)).toHaveValue('37');

    // Desactivar pide un segundo clic
    const desactivar = page.locator('tbody tr', { hasText: c.nombre }).getByRole('button', { name: 'Desactivar' });
    await desactivar.click();
    await expect(page.locator('tbody tr', { hasText: c.nombre }).getByRole('button', { name: '¿Seguro? Clic otra vez' })).toBeVisible();
    await page.locator('tbody tr', { hasText: c.nombre }).getByRole('button', { name: '¿Seguro? Clic otra vez' }).click();
    await expect(page.locator('tbody tr', { hasText: c.nombre })).toContainText('Desactivado');
    await page.locator('tbody tr', { hasText: c.nombre }).getByRole('button', { name: 'Activar' }).click();
    await expect(page.locator('tbody tr', { hasText: c.nombre })).toContainText('Activo');
  } finally {
    await api(page, 'PATCH', '/consumidores/' + c.id, { limitePorMinuto: c.limitePorMinuto, activo: c.activo });
  }
});

test('solo el administrador de la plataforma ve los consumidores', async ({ page }) => {
  await entrarComo(page, USUARIOS.adminCientifico);
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Consumidores externos' })).toHaveCount(0);
  expect((await api(page, 'GET', '/consumidores')).estado).toBe(403);
});

test('dar de alta una universidad muestra su clave una sola vez, y la clave funciona', async ({ page }) => {
  await entrarComo(page, USUARIOS.adminPlataforma);
  await page.goto('/consola/#/consumidores');
  const nombre = 'Universidad de prueba ui ' + Date.now();
  await page.getByLabel('Nombre de la universidad u ONG').fill(nombre);
  await page.getByRole('button', { name: 'Dar de alta' }).click();
  await expect(page.getByText(nombre + ' quedó dado de alta.', { exact: false })).toBeVisible();
  const clave = await page.getByLabel('API key (encabezado X-API-Key)').inputValue();
  expect(clave).toMatch(/^bh_/);
  await expect(page.locator('tbody tr', { hasText: nombre })).toContainText('Activo');

  const r = await page.request.get('/api/v1/estado', { headers: { 'X-API-Key': clave } });
  expect(r.status()).toBe(200);

  await page.getByRole('button', { name: 'Ya la guardé, ocultarla' }).click();
  await expect(page.getByLabel('API key (encabezado X-API-Key)')).toHaveCount(0);
  await page.reload();
  await expect(page.locator('main')).not.toContainText(clave);
});
