// Paquete 18: registrar hallazgos con el formulario que sale de la ficha (HU-01, RNF-01).
const { test, expect } = require('@playwright/test');
const { USUARIOS, entrarComo, api, unico, vigilarErrores } = require('./ayuda');

/** Crea, como administrador, una especie con su ficha para que la prueba no dependa de otras. */
async function especieDePrueba(page, atributos) {
  await entrarComo(page, USUARIOS.adminCientifico);
  const r = await api(page, 'POST', '/especies', { nombreCientifico: unico('Especie ui'), nombreComun: 'Prueba', enPeligro: false, atributos });
  expect(r.estado).toBe(201);
  await page.getByRole('button', { name: 'Salir' }).click();
  return r.cuerpo.especieId;
}

test('el investigador registra un hallazgo y los errores aparecen junto a cada campo', async ({ page }) => {
  const errores = vigilarErrores(page);
  const especie = await especieDePrueba(page, [
    { nombre: 'numero_individuos', tipo: 'NUMERO', obligatorio: true },
    { nombre: 'sexo', tipo: 'LISTA', obligatorio: false, opciones: ['macho', 'hembra'] }
  ]);
  await entrarComo(page, USUARIOS.investigador);
  await page.getByRole('navigation').getByRole('link', { name: 'Registrar hallazgo' }).click();
  await page.getByLabel('Especie').selectOption(String(especie));
  await expect(page.getByLabel('Numero individuos')).toBeVisible();
  await expect(page.getByLabel(/Sexo/)).toBeVisible();

  // Sin datos: el backend responde 422 y cada motivo aparece debajo de su campo
  await page.getByRole('button', { name: 'Registrar hallazgo' }).click();
  await expect(page.locator('[data-error="numero_individuos"]')).toHaveText('es obligatorio');
  await expect(page.locator('[data-error="departamento"]')).toBeVisible();
  await expect(page.locator('[data-error="coordenada"]')).toBeVisible();

  await page.getByLabel('Departamento').selectOption('Meta');
  await page.getByLabel('Numero individuos').fill('3');
  await page.getByLabel(/Sexo/).selectOption('hembra');
  await page.getByLabel('Latitud').fill('4.1420');
  await page.getByLabel('Longitud').fill('-73.6266');
  await page.getByRole('button', { name: 'Registrar hallazgo' }).click();
  await expect(page.getByText(/Hallazgo #\d+ registrado con la versión 1 de la ficha/)).toBeVisible();

  await page.getByRole('link', { name: 'Ver el hallazgo' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Hallazgo #');
  await expect(page.getByRole('cell', { name: 'hembra' })).toBeVisible();
  // La página del hallazgo nunca muestra la coordenada exacta
  await expect(page.locator('main')).not.toContainText('4.142');
  await expect(page.locator('main')).not.toContainText('-73.62');
  expect(errores).toEqual([]);
});

test('un atributo nuevo de la ficha aparece en el formulario sin cambiar código (RNF-01)', async ({ page }) => {
  const especie = await especieDePrueba(page, [{ nombre: 'conteo', tipo: 'NUMERO', obligatorio: true }]);
  await entrarComo(page, USUARIOS.adminCientifico);
  expect((await api(page, 'POST', '/especies/' + especie + '/ficha/atributos',
    { nombre: 'temperatura_agua', tipo: 'NUMERO', obligatorio: false, unidad: '°C' })).estado).toBe(201);
  await page.getByRole('button', { name: 'Salir' }).click();

  await entrarComo(page, USUARIOS.investigador);
  await page.goto('/consola/#/registrar');
  await page.getByLabel('Especie').selectOption(String(especie));
  await expect(page.getByLabel(/Temperatura agua \(°C\)/)).toBeVisible();
  await expect(page.getByText('Ficha versión 2')).toBeVisible();
});

test('una ruta GPS mal escrita se avisa antes de enviar', async ({ page }) => {
  const especie = await especieDePrueba(page, [{ nombre: 'recorrido', tipo: 'RUTA_GPS', obligatorio: true }]);
  await entrarComo(page, USUARIOS.investigador);
  await page.goto('/consola/#/registrar');
  await page.getByLabel('Especie').selectOption(String(especie));
  await page.getByLabel('Recorrido').fill('4.6, -74.08\nesto no es un punto');
  let envios = 0;
  page.on('request', (r) => { if (r.method() === 'POST' && r.url().endsWith('/hallazgos')) envios++; });
  await page.getByRole('button', { name: 'Registrar hallazgo' }).click();
  await expect(page.locator('[data-error="recorrido"]')).toContainText('latitud y longitud');
  expect(envios).toBe(0);
});

test('solo el investigador ve "Registrar hallazgo"', async ({ page }) => {
  await entrarComo(page, USUARIOS.director);
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Registrar hallazgo' })).toHaveCount(0);
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Hallazgos' })).toBeVisible();
});

test('la lista de hallazgos se filtra por especie y el filtro queda en la dirección', async ({ page }) => {
  await entrarComo(page, USUARIOS.investigador);
  await page.getByRole('navigation').getByRole('link', { name: 'Hallazgos' }).click();
  await page.getByLabel('Especie').selectOption({ index: 1 });
  await expect(page).toHaveURL(/#\/hallazgos\?especie=\d+/);
  const elegida = await page.getByLabel('Especie').locator('option:checked').textContent();
  const filas = page.locator('tbody tr');
  const n = await filas.count();
  for (let i = 0; i < Math.min(n, 10); i++) await expect(filas.nth(i).locator('td').nth(1)).toHaveText(elegida);
});
