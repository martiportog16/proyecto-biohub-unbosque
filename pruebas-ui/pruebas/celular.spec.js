// La consola en un celular: el menú se abre con un botón y nada se sale de la pantalla.
const { test, expect } = require('@playwright/test');
const { USUARIOS, entrarComo, api } = require('./ayuda');

test('en un celular el menú se abre con el botón y la página no se desborda', async ({ page }) => {
  await entrarComo(page, USUARIOS.analista);
  const menu = page.getByRole('navigation', { name: 'Opciones de su rol' });
  await expect(menu).toBeHidden();
  await page.getByRole('button', { name: 'Menú' }).click();
  await expect(menu).toBeVisible();
  await menu.getByRole('link', { name: 'Índices de presión' }).click();
  await expect(menu).toBeHidden();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Índices de presión');
  await sinDesborde(page);
  await expect(page.locator('#rol-usuario')).toBeInViewport();
});

test('en un celular el detalle de un informe no se sale de la pantalla (la tabla se desplaza sola)', async ({ page }) => {
  await entrarComo(page, USUARIOS.analista);
  const informes = (await api(page, 'GET', '/informes')).cuerpo;
  test.skip(!informes.length, 'No hay informes todavía');
  await page.goto('/consola/#/informes/' + informes[0].periodo);
  await expect(page.getByRole('heading', { name: 'Por departamento' })).toBeVisible();
  await sinDesborde(page);
});

async function sinDesborde(page) {
  const desborde = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(desborde).toBeLessThanOrEqual(0);
}
