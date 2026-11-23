// Paquete 17: inicio de sesión, menú según el rol y manejo de errores de la consola.
const { test, expect } = require('@playwright/test');
const { USUARIOS, entrarComo, vigilarErrores, CLAVE } = require('./ayuda');

test.describe('Inicio de sesión', () => {
  test('con una clave equivocada muestra el error y no entra', async ({ page }) => {
    await page.goto('/consola/');
    await page.getByLabel('Correo institucional').fill(USUARIOS.investigador.correo);
    await page.getByLabel('Clave').fill('equivocada');
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.getByRole('alert')).toContainText('Correo o clave incorrectos');
    await expect(page.locator('#pantalla-consola')).toBeHidden();
  });

  test('sin datos pide escribirlos, sin llamar al servidor', async ({ page }) => {
    let llamadas = 0;
    page.on('request', (r) => { if (r.url().includes('/auth/login')) llamadas++; });
    await page.goto('/consola/');
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.getByRole('alert')).toContainText('Escriba su correo y su clave');
    expect(llamadas).toBe(0);
  });

  test('los usuarios de demostración llenan el formulario', async ({ page }) => {
    await page.goto('/consola/');
    await page.getByText('Usuarios de demostración').click();
    await page.getByRole('button', { name: /Analista · analista@biohub.co/ }).click();
    await expect(page.getByLabel('Correo institucional')).toHaveValue('analista@biohub.co');
    await expect(page.getByLabel('Clave')).toHaveValue(CLAVE);
  });

  test('entra, saluda y muestra el rol; al salir vuelve al inicio de sesión', async ({ page }) => {
    const errores = vigilarErrores(page);
    await entrarComo(page, USUARIOS.investigador);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Hola, Ana');
    await expect(page.locator('#nombre-usuario')).toHaveText('Ana Investigadora');

    await page.getByRole('button', { name: 'Salir' }).click();
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
    await page.goBack();
    await expect(page.locator('#pantalla-consola')).toBeHidden();
    expect(errores).toEqual([]);
  });

  test('al recargar la página sigue la sesión', async ({ page }) => {
    await entrarComo(page, USUARIOS.analista);
    await page.reload();
    await expect(page.locator('#rol-usuario')).toHaveText('Analista');
  });

  test('si el token vence, la siguiente acción vuelve al inicio de sesión con el aviso', async ({ page }) => {
    await entrarComo(page, USUARIOS.analista);
    await page.evaluate(() => {
      const s = JSON.parse(sessionStorage.getItem('biohub.sesion'));
      s.token = s.token.slice(0, -5) + 'xxxxx';   // firma inválida: el backend responde 401
      sessionStorage.setItem('biohub.sesion', JSON.stringify(s));
    });
    await page.getByRole('link', { name: 'Índices de presión' }).first().click();
    await expect(page.getByText('Su sesión venció. Entre de nuevo.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
  });
});

test.describe('Menú según el rol', () => {
  // Cada actor ve las pantallas de SUS casos de uso (diagrama de casos de uso del Entregable 2),
  // más la lista de hallazgos para quien necesita encontrar uno (CU-01, CU-06, CU-07 y CU-08).
  const ESPERADO = {
    investigador: ['Registrar hallazgo', 'Hallazgos', 'Equipos de campo'],
    adminCientifico: ['Especies y fichas', 'Grupos taxonómicos'],
    director: ['Hallazgos', 'Historial y reclasificación'],
    auditor: ['Hallazgos', 'Historial y reclasificación', 'Accesos a coordenadas'],
    altoRango: ['Hallazgos', 'Mapa de coordenadas'],
    analista: ['Índices de presión'],
    adminPlataforma: ['Consumidores externos']
  };

  for (const [quien, opciones] of Object.entries(ESPERADO)) {
    test(`el menú de ${quien} tiene exactamente sus pantallas`, async ({ page }) => {
      await entrarComo(page, USUARIOS[quien]);
      const menu = page.getByRole('navigation', { name: 'Opciones de su rol' });
      await expect(menu.getByRole('link')).toHaveText(['Inicio', ...opciones]);
    });
  }

  test('el investigador no ve los informes en el menú, y si entra por la dirección se le niega', async ({ page }) => {
    await entrarComo(page, USUARIOS.investigador);
    await expect(page.getByRole('navigation').getByRole('link', { name: 'Índices de presión' })).toHaveCount(0);
    await page.goto('/consola/#/informes');
    await expect(page.getByRole('alert')).toContainText('no tiene acceso a esta pantalla');
  });

  test('aunque se salte el menú, el backend responde 403 (el permiso real está en la API)', async ({ page }) => {
    await entrarComo(page, USUARIOS.investigador);
    const estado = await page.evaluate(async () => {
      const s = JSON.parse(sessionStorage.getItem('biohub.sesion'));
      const r = await fetch('/api/cientifico/informes', { headers: { Authorization: 'Bearer ' + s.token } });
      return r.status;
    });
    expect(estado).toBe(403);
  });

  test('una dirección que no existe muestra "Página no encontrada"', async ({ page }) => {
    await entrarComo(page, USUARIOS.analista);
    await page.goto('/consola/#/no-existe');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Página no encontrada');
  });
});

test.describe('Seguridad de la página', () => {
  test('Nginx envía la política de seguridad (CSP) y la consola no la viola', async ({ page }) => {
    const errores = vigilarErrores(page);
    const respuesta = await page.goto('/consola/');
    const csp = respuesta.headers()['content-security-policy'];
    expect(csp).toContain("default-src 'self'");
    expect(csp).toContain("frame-ancestors 'none'");
    await entrarComo(page, USUARIOS.analista);
    await page.getByRole('link', { name: 'Índices de presión' }).first().click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Índices de presión');
    expect(errores).toEqual([]);
  });

  test('/consola sin barra redirige a /consola/ sin perder el puerto', async ({ page, baseURL }) => {
    await page.goto('/consola');
    expect(page.url()).toBe(baseURL + '/consola/');
  });

  test('el portal público enlaza a la consola', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Personal del Instituto' }).click();
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
  });
});
