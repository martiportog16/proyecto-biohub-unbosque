// Pruebas en navegador contra BioHub ya levantado (docker compose up -d).
// Se corren con scripts/pruebas-ui.sh, que usa la imagen de Docker de Playwright:
// no hay que instalar Node ni navegadores.
const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './pruebas',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,      // comparten la base de datos de la demo
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { outputFolder: 'resultados/informe', open: 'never' }]],
  outputDir: 'resultados/evidencias',
  use: {
    baseURL: process.env.BIOHUB_URL || 'http://localhost:8080',
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  },
  projects: [
    { name: 'escritorio', use: { ...devices['Desktop Chrome'] }, testIgnore: /celular\.spec\.js/ },
    { name: 'celular', use: { ...devices['Pixel 5'] }, testMatch: /celular\.spec\.js/ }
  ]
});
