// Paquete 18: especies, fichas y grupos taxonómicos desde la consola (HU-02).
const { test, expect } = require('@playwright/test');
const { USUARIOS, entrarComo, api, unico } = require('./ayuda');

test('el administrador crea una especie, le agrega y le retira atributos; cada cambio es una versión', async ({ page }) => {
  await entrarComo(page, USUARIOS.adminCientifico);
  await page.getByRole('navigation').getByRole('link', { name: 'Especies y fichas' }).click();
  await page.getByRole('link', { name: 'Nueva especie' }).click();
  const nombre = unico('Panthera ui');
  // Sin nombres: el backend responde 422 con los dos motivos, junto a cada campo
  await page.getByRole('button', { name: 'Crear especie' }).click();
  await expect(page.locator('[data-error="nombreCientifico"]')).toHaveText('es obligatorio');
  await expect(page.locator('[data-error="nombreComun"]')).toHaveText('es obligatorio');
  await page.getByLabel('Nombre científico').fill(nombre);
  await page.getByLabel('Nombre común').fill('Jaguar de prueba');
  await page.locator('#atr-0-nombre').fill('longitud_cm');
  await page.locator('#atr-0-tipo').selectOption('NUMERO');
  await page.locator('#atr-0-obligatorio').check();
  await page.getByRole('button', { name: '+ Otro atributo' }).click();
  await page.locator('#atr-1-nombre').fill('pelaje');
  await page.locator('#atr-1-tipo').selectOption('LISTA');
  await page.locator('#atr-1-opciones').fill('manchado, negro');
  await page.getByRole('button', { name: 'Crear especie' }).click();

  await expect(page.getByRole('heading', { level: 1 })).toHaveText(nombre);
  await expect(page.getByRole('heading', { name: 'Ficha vigente · versión 1' })).toBeVisible();
  await expect(page.getByText('Opciones: manchado, negro')).toBeVisible();

  await page.locator('#nuevo-atr-nombre').fill('peso_kg');
  await page.locator('#nuevo-atr-tipo').selectOption('NUMERO');
  await page.getByRole('button', { name: 'Agregar atributo' }).click();
  await expect(page.getByRole('heading', { name: 'Ficha vigente · versión 2' })).toBeVisible();

  // Retirar pide confirmar con un segundo clic
  const retirar = page.getByRole('button', { name: 'Retirar pelaje' });
  await retirar.click();
  await expect(retirar).toHaveText('¿Seguro? Clic otra vez');
  await retirar.click();
  await expect(page.getByRole('heading', { name: 'Ficha vigente · versión 3' })).toBeVisible();
  await expect(page.getByRole('row', { name: /pelaje/ })).toContainText('Retirado');
});

test('un atributo con nombre repetido muestra el motivo junto al campo', async ({ page }) => {
  await entrarComo(page, USUARIOS.adminCientifico);
  await page.goto('/consola/#/especies/1');
  const existente = await page.locator('tbody code').first().textContent();
  await page.locator('#nuevo-atr-nombre').fill(existente);
  await page.getByRole('button', { name: 'Agregar atributo' }).click();
  await expect(page.locator('form [data-error="nombre"]')).toContainText('ya existe');
});

test('las fichas y los grupos son del administrador científico (CU-02): otro rol que llega por la dirección no entra', async ({ page }) => {
  await entrarComo(page, USUARIOS.investigador);
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Especies y fichas' })).toHaveCount(0);
  for (const ruta of ['#/especies', '#/grupos']) {
    await page.goto('/consola/' + ruta);
    await expect(page.getByRole('alert')).toContainText('no tiene acceso a esta pantalla');
  }
  // El investigador sí ve la ficha donde la necesita: en el formulario de registro (CU-01)
  await page.getByRole('navigation').getByRole('link', { name: 'Registrar hallazgo' }).click();
  await expect(page.getByLabel('Especie')).toBeVisible();
});

test('un atributo agregado al grupo llega a la especie que hereda (HU-02, criterio 4)', async ({ page }) => {
  await entrarComo(page, USUARIOS.adminCientifico);
  const especie = (await api(page, 'POST', '/especies', { nombreCientifico: unico('Ave ui'), nombreComun: 'Ave', enPeligro: false,
    atributos: [{ nombre: 'conteo', tipo: 'NUMERO', obligatorio: true }] })).cuerpo.especieId;
  const otra = (await api(page, 'POST', '/especies', { nombreCientifico: unico('Reptil ui'), nombreComun: 'Reptil', enPeligro: false,
    atributos: [{ nombre: 'conteo', tipo: 'NUMERO', obligatorio: true }] })).cuerpo.especieId;

  await page.goto('/consola/#/grupos');
  const grupo = unico('Aves ui');
  await page.getByLabel('Nombre', { exact: true }).fill(grupo);
  await page.getByRole('button', { name: 'Crear grupo' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(grupo);
  await expect(page.getByText('El grupo todavía no tiene ficha')).toBeVisible();

  await page.getByLabel('Especie', { exact: true }).selectOption(String(especie));
  await page.getByRole('button', { name: 'Agregar al grupo' }).click();
  await expect(page.getByRole('row', { name: /Ave ui/ })).toContainText('Hereda la del grupo');

  await page.locator('#nuevo-atr-nombre').fill('envergadura_cm');
  await page.locator('#nuevo-atr-tipo').selectOption('NUMERO');
  await page.getByRole('button', { name: 'Agregar atributo' }).click();
  await expect(page.getByText('Se copió a 1 especie.')).toBeVisible();

  await page.goto('/consola/#/especies/' + especie);
  await expect(page.getByText(/hereda su ficha/)).toBeVisible();
  await expect(page.getByRole('cell', { name: 'envergadura_cm', exact: true })).toBeVisible();
  await page.goto('/consola/#/especies/' + otra);
  await expect(page.getByRole('heading', { name: /Ficha vigente/ })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'envergadura_cm', exact: true })).toHaveCount(0);
});
