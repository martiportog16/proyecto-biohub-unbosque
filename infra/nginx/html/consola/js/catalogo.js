/*
 * Datos que usan varias pantallas: especies, departamentos y los tipos de atributo.
 * - Los departamentos no cambian: se piden una sola vez.
 * - Las especies se piden de nuevo en cada cambio de pantalla, porque otro administrador
 *   pudo crear una mientras tanto. Dentro de la misma pantalla se reutilizan.
 */
(function () {
  'use strict';
  const { api } = window.BioHub;

  let especies = null;
  let departamentos = null;

  /** Los 6 tipos de dato de una ficha (P-32), con su explicación para el formulario. */
  const TIPOS = {
    TEXTO: { nombre: 'Texto', ayuda: 'Palabras o un código, por ejemplo el de una muestra de ADN.' },
    NUMERO: { nombre: 'Número', ayuda: 'Puede tener unidad, por ejemplo °C o cm.' },
    FECHA: { nombre: 'Fecha', ayuda: 'Un día del calendario.' },
    LISTA: { nombre: 'Lista de opciones', ayuda: 'Se escoge una de las opciones definidas.' },
    RUTA_GPS: { nombre: 'Ruta GPS', ayuda: 'Varios puntos (latitud, longitud). No sale de BioHub hacia afuera.' },
    ARCHIVO: { nombre: 'Archivo', ayuda: 'Nombre o enlace de una foto o grabación. El archivo no se guarda en BioHub.' }
  };

  const catalogo = {
    TIPOS,

    async especies() {
      if (!especies) especies = await api.get('/especies');
      return especies;
    },
    olvidarEspecies() {
      especies = null;
    },
    /** "Ateles hybridus (Mono araña café)" o "Especie 7" si no se encuentra. */
    async nombreEspecie(id) {
      const e = (await catalogo.especies()).find((x) => x.id === Number(id));
      return e ? e.nombreCientifico + (e.nombreComun ? ' (' + e.nombreComun + ')' : '') : 'Especie ' + id;
    },
    async departamentos() {
      if (!departamentos) departamentos = await api.get('/hallazgos/departamentos');
      return departamentos;
    }
  };

  window.addEventListener('hashchange', () => catalogo.olvidarEspecies());

  window.BioHub.catalogo = catalogo;
})();
