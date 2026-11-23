// Paquete 18: equipos de campo desde la consola (HU-03).
const { test, expect } = require('@playwright/test');
const { USUARIOS, entrarComo, api } = require('./ayuda');

test('registrar un equipo, pasarlo a otra persona y ver el historial', async ({ page }) => {
  await entrarComo(page, USUARIOS.investigador);
  await page.getByRole('navigation').getByRole('link', { name: 'Equipos de campo' }).click();
  await page.getByRole('link', { name: '+ Registrar equipo' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Registrar equipo');
  const codigo = 'UI-' + Date.now().toString(36).toUpperCase();
  await page.getByLabel('Código', { exact: true }).fill(codigo.toLowerCase());
  await page.getByLabel('Tipo').selectOption('DRON');
  await page.getByLabel(/Descripción/).fill('Dron de prueba');
  // La persona que registra queda escogida como responsable
  await expect(page.getByLabel('Responsable')).toHaveValue(/\d+/);
  await page.getByRole('button', { name: 'Registrar equipo' }).click();

  await expect(page.getByRole('heading', { level: 1 })).toHaveText(codigo);
  await expect(page.locator('.cifra').first()).toContainText('Ana Investigadora');

  // Solo se le puede pasar a un investigador (HU-03, criterio 1)
  await expect(page.locator('#reasignar-responsable option', { hasText: 'Marta Directora' })).toHaveCount(0);
  // Sin motivo, el backend lo rechaza y lo dice junto al campo
  const carlos = await page.locator('#reasignar-responsable option', { hasText: 'Carlos Investigador' }).getAttribute('value');
  await page.getByLabel('Nuevo responsable').selectOption(carlos);
  await page.getByRole('button', { name: 'Pasar el equipo' }).click();
  await expect(page.locator('[data-error="motivo"]')).toContainText('al menos 5 caracteres');

  await page.getByLabel('Motivo').fill('Ana sale de vacaciones');
  await page.getByRole('button', { name: 'Pasar el equipo' }).click();
  await expect(page.locator('.cifra').first()).toContainText('Carlos Investigador');
  const filas = page.locator('section', { hasText: 'Historial de responsables' }).locator('tbody tr');
  await expect(filas).toHaveCount(2);
  await expect(filas.first()).toContainText('Ana sale de vacaciones');

  // Buscar por código, en minúsculas, lo encuentra
  await page.goto('/consola/#/equipos');
  await page.getByLabel('Código del equipo').fill(codigo.toLowerCase());
  await page.getByRole('button', { name: 'Buscar' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(codigo);
});

test('sin responsable no se puede registrar un equipo (HU-03, criterio 4)', async ({ page }) => {
  await entrarComo(page, USUARIOS.investigador);
  await page.goto('/consola/#/equipos/nuevo');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Registrar equipo');
  await page.getByLabel('Código', { exact: true }).fill('SIN-RESP-UI');
  await page.getByLabel('Tipo').selectOption('GPS');
  await page.getByLabel('Responsable').selectOption('');
  await page.getByRole('button', { name: 'Registrar equipo' }).click();
  await expect(page.locator('[data-error="responsableId"]')).toContainText('sin responsable');
});

test('los equipos son del investigador (CU-03 y CU-04): el auditor no los ve y la API le responde 403', async ({ page }) => {
  await entrarComo(page, USUARIOS.auditor);
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Equipos de campo' })).toHaveCount(0);
  await page.goto('/consola/#/equipos');
  await expect(page.getByRole('alert')).toContainText('no tiene acceso a esta pantalla');
  expect((await api(page, 'GET', '/equipos')).estado).toBe(403);
});
