// Paquete 18: equipos de campo desde la consola (HU-03).
const { test, expect } = require('@playwright/test');
const { USUARIOS, entrarComo } = require('./ayuda');

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

  // Sin motivo, el backend lo rechaza y lo dice junto al campo
  const marta = await page.locator('#reasignar-responsable option', { hasText: 'Marta Directora' }).getAttribute('value');
  await page.getByLabel('Nuevo responsable').selectOption(marta);
  await page.getByRole('button', { name: 'Pasar el equipo' }).click();
  await expect(page.locator('[data-error="motivo"]')).toContainText('al menos 5 caracteres');

  await page.getByLabel('Motivo').fill('Ana sale de vacaciones');
  await page.getByRole('button', { name: 'Pasar el equipo' }).click();
  await expect(page.locator('.cifra').first()).toContainText('Marta Directora');
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

test('el auditor consulta los equipos pero no los registra; el analista ni los ve', async ({ page }) => {
  await entrarComo(page, USUARIOS.auditor);
  await page.getByRole('navigation').getByRole('link', { name: 'Equipos de campo' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Equipos de campo');
  await expect(page.getByRole('link', { name: '+ Registrar equipo' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Salir' }).click();

  await entrarComo(page, USUARIOS.analista);
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Equipos de campo' })).toHaveCount(0);
});
