// Paquete 28: usuarios y roles (P-28, S-01).
const { test, expect } = require('@playwright/test');
const { USUARIOS, entrarComo, api, vigilarErrores } = require('./ayuda');

const correoNuevo = () => 'ui-' + Date.now().toString(36) + '@biohub.co';

async function entrarCon(page, correo, clave, rol) {
  await page.goto('/consola/');
  await page.getByLabel('Correo institucional').fill(correo);
  await page.getByLabel('Clave').fill(clave);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.locator('#rol-usuario')).toHaveText(rol);
}

test('la plataforma crea un usuario; con su clave temporal entra y la cambia por una suya', async ({ page }) => {
  const errores = vigilarErrores(page);
  await entrarComo(page, USUARIOS.adminPlataforma);
  await page.getByRole('navigation').getByRole('link', { name: 'Usuarios y roles' }).click();
  await page.getByRole('link', { name: '+ Nuevo usuario' }).click();
  // El alto rango no se ofrece: lo da un director (S-01)
  await expect(page.locator('#usuario-rol option', { hasText: 'Personal de alto rango' })).toHaveCount(0);

  // Sin motivo, el backend lo rechaza y lo dice junto al campo
  const correo = correoNuevo();
  await page.getByLabel('Nombre completo').fill('Carolina Rojas');
  await page.getByLabel('Correo de la persona').fill(correo);
  await page.getByLabel('Rol', { exact: true }).selectOption('INVESTIGADOR');
  await page.getByRole('button', { name: 'Crear usuario' }).click();
  await expect(page.locator('[data-error="motivo"]')).toContainText('es obligatorio');

  await page.getByLabel('Motivo').fill('Ingresa al grupo de ecología del Orinoco');
  await page.getByRole('button', { name: 'Crear usuario' }).click();
  await expect(page.getByText('no se volverá a mostrar')).toBeVisible();
  const temporal = await page.getByLabel('Clave temporal').inputValue();
  expect(temporal).toHaveLength(14);
  await page.getByRole('button', { name: 'Salir' }).click();

  // La persona nueva entra con la temporal y la cambia
  await entrarCon(page, correo, temporal, 'Investigador');
  await page.getByRole('link', { name: 'Cambiar clave' }).click();
  await page.getByLabel('Clave actual').fill(temporal);
  await page.getByLabel('Clave nueva', { exact: true }).fill('mi-clave-propia-2026');
  await page.getByLabel('Repita la clave nueva').fill('mi-clave-propia-2026');
  await page.getByRole('button', { name: 'Cambiar la clave' }).click();
  await expect(page.getByText('su clave cambió')).toBeVisible();
  await page.getByRole('button', { name: 'Salir' }).click();
  await entrarCon(page, correo, 'mi-clave-propia-2026', 'Investigador');
  expect(errores).toEqual([]);
});

test('el director da el alto rango (S-01); la plataforma no puede quitárselo', async ({ page }) => {
  await entrarComo(page, USUARIOS.adminPlataforma);
  const nombre = 'Julián Pardo ' + Date.now().toString(36);
  const creado = (await api(page, 'POST', '/usuarios', {
    correo: correoNuevo(), nombre, rol: 'INVESTIGADOR', motivo: 'Ingresa al proyecto del Chocó'
  })).cuerpo;
  const id = creado.usuario.id;
  await page.getByRole('button', { name: 'Salir' }).click();

  await entrarComo(page, USUARIOS.director);
  await page.getByRole('navigation').getByRole('link', { name: 'Usuarios y roles' }).click();
  await page.getByRole('link', { name: nombre }).click();
  await expect(page.getByRole('heading', { name: 'Dar el alto rango' })).toBeVisible();
  await page.getByLabel('Motivo').fill('Lidera la expedición al Chocó');
  await page.getByRole('button', { name: 'Dar el alto rango' }).click();
  await expect(page.getByRole('heading', { name: 'Quitar el alto rango' })).toBeVisible();
  const bitacora = page.locator('section', { hasText: 'Bitácora' }).locator('tbody tr');
  await expect(bitacora.first()).toContainText('Marta Directora');
  await expect(bitacora.first()).toContainText('Lidera la expedición al Chocó');
  await page.getByRole('button', { name: 'Salir' }).click();

  await entrarComo(page, USUARIOS.adminPlataforma);
  await page.goto('/consola/#/usuarios/' + id);
  await expect(page.getByText('solo un director científico puede quitarle ese rol')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Cambiar el rol' })).toHaveCount(0);
  expect((await api(page, 'PUT', '/usuarios/' + id + '/rol', { rol: 'INVESTIGADOR', motivo: 'Intento de la plataforma' })).estado).toBe(403);
});

test('los demás roles no ven la gestión de usuarios y la API les responde 403', async ({ page }) => {
  await entrarComo(page, USUARIOS.investigador);
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Usuarios y roles' })).toHaveCount(0);
  await page.goto('/consola/#/usuarios');
  await expect(page.getByRole('alert')).toContainText('no tiene acceso a esta pantalla');
  expect((await api(page, 'GET', '/usuarios')).estado).toBe(403);
});
