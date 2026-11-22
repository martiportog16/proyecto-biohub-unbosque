// Paquete 17: pantalla del analista (HU-11, criterio 4: "el analista puede consultar los índices").
const { test, expect } = require('@playwright/test');
const { USUARIOS, entrarComo, api, vigilarErrores } = require('./ayuda');

test.describe('Índices de presión', () => {
  test.beforeEach(async ({ page }) => {
    await entrarComo(page, USUARIOS.analista);
    await page.getByRole('navigation').getByRole('link', { name: 'Índices de presión' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Índices de presión');
  });

  test('pedir un informe lo deja en la lista de solicitudes', async ({ page }) => {
    const errores = vigilarErrores(page);
    // Un mes de hace ~3 años: seguro no tiene informe ni hallazgos
    const hace3 = new Date(); hace3.setFullYear(hace3.getFullYear() - 3);
    const mes = hace3.toISOString().slice(0, 7);
    await page.getByLabel('Mes').fill(mes);
    await page.getByRole('button', { name: 'Pedir informe' }).click();
    await expect(page.getByText(/Solicitud enviada|ya existe/)).toBeVisible();
    const fila = page.locator('section', { hasText: 'Mis solicitudes' }).locator('tbody tr').first();
    await expect(fila).toBeVisible();
    expect(errores).toEqual([]);
  });

  test('sin mes no envía nada', async ({ page }) => {
    await page.getByRole('button', { name: 'Pedir informe' }).click();
    // El navegador marca el campo obligatorio, o la consola muestra el aviso
    const invalido = await page.getByLabel('Mes').evaluate((el) => !el.checkValidity());
    expect(invalido).toBe(true);
  });

  test('el detalle de un informe muestra los índices por departamento y la explicación', async ({ page }) => {
    const informes = (await api(page, 'GET', '/informes')).cuerpo;
    test.skip(!informes.length, 'No hay informes todavía (correr scripts/sembrar-millon.sh para tener datos)');
    const periodo = informes[0].periodo;

    await page.getByRole('link', { name: 'Ver' }).first().click();
    await expect(page).toHaveURL(new RegExp('#/informes/' + periodo));
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Índices de');
    await expect(page.getByRole('heading', { name: 'Por departamento' })).toBeVisible();
    await expect(page.getByText('Índice = (B − E) / B')).toBeVisible();
    const filas = page.locator('section', { hasText: 'Por departamento' }).locator('tbody tr');
    expect(await filas.count()).toBeGreaterThan(0);

    // El filtro deja solo los de presión alta (≥ 0,5) o dice que no hay
    await page.getByLabel('Solo presión alta').check();
    const valores = await page.locator('section', { hasText: 'Por departamento' }).locator('tbody td.numero:nth-child(2)').allTextContents();
    for (const v of valores) expect(parseFloat(v.replace(',', '.'))).toBeGreaterThanOrEqual(0.5);
  });

  test('un mes sin informe lo dice y ofrece volver', async ({ page }) => {
    await page.goto('/consola/#/informes/2001-01');
    await expect(page.getByText('No hay informe de enero de 2001')).toBeVisible();
    await page.getByRole('link', { name: '← Todos los informes' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Índices de presión');
  });
});
