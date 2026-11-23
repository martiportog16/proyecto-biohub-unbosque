/*
 * Mapa con las coordenadas exactas de una especie (HU-08, RF-10, RF-11, RNF-06).
 *
 * En el menú solo aparece para el personal de alto rango: es el actor de CU-08.
 * Si otra persona del personal científico llega por un enlace (por ejemplo, uno que le
 * compartieron), la consola NO decide: le pregunta a la autorización del backend, que
 * es la única que decide (un único componente, RF-11):
 *  - especie fuera de peligro: el personal científico puede verla (decisión P-24);
 *  - especie en peligro: solo el personal de alto rango.
 * Cada consulta, permitida o negada, queda en el registro de accesos. Por eso el mapa
 * no se carga solo al escoger la especie: se pide con un botón, a propósito.
 *
 * El fondo del mapa son los límites de los departamentos, guardados en nuestro servidor.
 * NO se usan mapas de terceros (OpenStreetMap, Google): cada imagen que el navegador
 * pidiera le diría a ese tercero qué zona está mirando el alto rango, es decir, dónde
 * vive una especie en peligro. Además, así funciona sin internet.
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui, sesion, catalogo } = window.BioHub;

  const CIENTIFICOS = ['INVESTIGADOR', 'ADMINISTRADOR_CIENTIFICO', 'DIRECTOR_CIENTIFICO', 'AUDITOR_CIENTIFICO', 'ALTO_RANGO'];
  const LIMITES = { sur: -4.3, oeste: -82, norte: 13.6, este: -66.8 };
  // Colombia continental (sin San Andrés, que queda muy al oeste y achica todo el mapa)
  const CONTINENTAL = [[-4.3, -79.1], [12.5, -66.8]];

  // Colores de los departamentos según el tema del sistema: en oscuro, relleno oscuro y borde claro
  function estiloDepartamentos() {
    const oscuro = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    return oscuro
      ? { color: '#6f8a7d', weight: 1, fillColor: '#2a3a32', fillOpacity: 1 }
      : { color: '#7f978a', weight: 1, fillColor: '#d9e7dd', fillOpacity: 1 };
  }
  let departamentos = null;

  BioHub.registrar({
    id: 'mapa',
    titulo: 'Mapa de coordenadas',
    grupo: 'Coordenadas protegidas',
    menu: ['ALTO_RANGO'],    // CU-08: consultar coordenadas exactas
    roles: CIENTIFICOS,      // por un enlace puede llegar cualquiera del personal científico; decide el backend
    resumen: 'Dónde se vio cada hallazgo, con la ubicación exacta, incluidas las especies en peligro.',
    mostrar
  });

  // En el detalle de cada hallazgo: el alto rango puede pedir su ubicación exacta (también queda registrado)
  if (BioHub.hallazgos) {
    BioHub.hallazgos.agregarAccion((hallazgo) => sesion.tiene('ALTO_RANGO') ? ubicacionDeHallazgo(hallazgo) : null);
  }

  /* ---------- Pantalla del mapa ---------- */

  async function mostrar(contenedor, [especieInicial]) {
    ui.titulo(contenedor, 'Mapa de coordenadas',
      sesion.tiene('ALTO_RANGO') ? 'Usted puede ver las coordenadas de todas las especies, incluidas las que están en peligro.'
        : 'Esta pantalla es del personal de alto rango. Usted llegó por un enlace: la autorización decide si su rol puede ver la especie, y registra la consulta.');
    const especies = await catalogo.especies();
    const select = h('select', { id: 'especie-mapa' }, h('option', { value: '' }, 'Escoja la especie…'),
      especies.map((e) => h('option', { value: e.id }, e.nombreCientifico + (e.enPeligro ? ' · en peligro' : ''))));
    if (especieInicial) select.value = especieInicial;
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Ver en el mapa');
    const zona = h('div', { 'aria-live': 'polite' });
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('label', { for: 'especie-mapa' }, 'Especie'),
      h('div', { class: 'acciones', style: 'margin-top:0' }, h('div', { style: 'flex:1 1 280px' }, select), boton),
      h('p', { class: 'ayuda-campo' }, 'Cada consulta queda registrada con su nombre, la hora y si fue permitida (la revisa el auditor).'));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!select.value) {
        vaciar(zona, ui.mensaje('error', 'Escoja una especie.'));
        return;
      }
      history.replaceState(null, '', '#/mapa/' + select.value);
      vaciar(zona, ui.cargando('Pidiendo las coordenadas a la autorización…'));
      try {
        const mapa = await ui.conBotonOcupado(boton, () => api.get('/especies/' + select.value + '/coordenadas'), 'Consultando…');
        await pintarMapa(zona, mapa);
      } catch (err) {
        vaciar(zona, err.estado === 403
          ? ui.mensaje('error', err.message, ['Esta consulta negada también quedó en el registro de accesos.'])
          : ui.error(err));
      }
    });
    contenedor.append(form, zona);
  }

  async function pintarMapa(zona, mapa) {
    vaciar(zona);
    zona.append(
      mapa.enPeligro ? ui.mensaje('aviso', 'Especie en peligro: no comparta estas coordenadas. Su consulta quedó registrada.')
        : ui.mensaje('info', 'Su consulta quedó registrada.'),
      h('p', null, h('strong', null, ui.numero(mapa.total)), ' hallazgos de ', h('em', null, mapa.nombreCientifico),
        mapa.total > mapa.puntos.length ? ' · se muestran los ' + mapa.puntos.length + ' más recientes' : ''));
    if (!mapa.puntos.length) {
      zona.append(ui.vacio('Esta especie no tiene hallazgos registrados.'));
      return;
    }
    const lienzo = h('div', { class: 'mapa', id: 'mapa', role: 'region', 'aria-label': 'Mapa de Colombia con los hallazgos' });
    zona.append(lienzo, tablaPuntos(mapa.puntos));
    await dibujar(lienzo, mapa.puntos);
  }

  /** Dibuja el fondo (departamentos) y un círculo por punto. Devuelve el mapa de Leaflet. */
  async function dibujar(lienzo, puntos) {
    const mapa = L.map(lienzo, {
      zoomSnap: 0.5, minZoom: 4.5, maxZoom: 14, attributionControl: true,
      maxBounds: L.latLngBounds([LIMITES.sur - 3, LIMITES.oeste - 3], [LIMITES.norte + 3, LIMITES.este + 3])
    });
    mapa.attributionControl.setPrefix(false).addAttribution('Límites: Natural Earth · Mapa: Leaflet');
    try {
      if (!departamentos) departamentos = await (await fetch('datos/colombia-departamentos.geojson')).json();
      L.geoJSON(departamentos, {
        style: estiloDepartamentos(),
        // El nombre va como elemento, no como texto HTML (Leaflet usa innerHTML con los textos)
        onEachFeature: (f, capa) => capa.bindTooltip(h('span', null, f.properties.nombre), { sticky: true, direction: 'top' })
      }).addTo(mapa);
    } catch (e) {
      lienzo.after(ui.mensaje('aviso', 'No se pudo cargar el fondo con los departamentos. Los puntos se muestran igual.'));
    }
    const capa = L.featureGroup(puntos.map((p) => L.circleMarker([p.latitud, p.longitud], {
      radius: 7, color: '#ffffff', weight: 2, fillColor: '#a3372a', fillOpacity: 0.9
    }).bindPopup(() => popup(p)))).addTo(mapa);
    // Siempre se ve el país entero con los puntos encima; con la rueda o el + se acerca.
    // (Antes se acercaba al punto y con uno solo quedaba dentro de un departamento, sin referencia.)
    const vista = L.latLngBounds(CONTINENTAL);
    if (puntos.length) vista.extend(capa.getBounds());
    mapa.fitBounds(vista, { padding: [12, 12] });
    return mapa;
  }

  function popup(p) {
    return h('div', null,
      h('strong', null, 'Hallazgo #' + p.hallazgoId), h('br'),
      ui.fecha(p.fechaObservacion) + ' · ' + p.departamento, h('br'),
      h('code', null, p.latitud.toFixed(6) + ', ' + p.longitud.toFixed(6)),
      p.precisionMetros ? ' (± ' + p.precisionMetros + ' m)' : '', h('br'),
      h('a', { href: '#/hallazgos/' + p.hallazgoId }, 'Ver el hallazgo'));
  }

  /** Los mismos puntos en una tabla: se pueden leer con lector de pantalla y copiar. */
  function tablaPuntos(puntos) {
    return h('details', { class: 'tarjeta' }, h('summary', null, 'Ver los puntos en una tabla'),
      h('div', { class: 'tabla-envoltura' }, h('table', null,
        h('thead', null, h('tr', null, h('th', null, 'Hallazgo'), h('th', null, 'Fecha'), h('th', null, 'Departamento'),
          h('th', { class: 'numero' }, 'Latitud'), h('th', { class: 'numero' }, 'Longitud'), h('th', { class: 'numero' }, 'Precisión'))),
        h('tbody', null, puntos.map((p) => h('tr', null,
          h('td', null, h('a', { href: '#/hallazgos/' + p.hallazgoId }, '#' + p.hallazgoId)),
          h('td', null, ui.fecha(p.fechaObservacion)), h('td', null, p.departamento),
          h('td', { class: 'numero' }, p.latitud.toFixed(6)), h('td', { class: 'numero' }, p.longitud.toFixed(6)),
          h('td', { class: 'numero' }, p.precisionMetros ? p.precisionMetros + ' m' : '—')))))));
  }

  /* ---------- Ubicación de un solo hallazgo ---------- */

  function ubicacionDeHallazgo(hallazgo) {
    const zona = h('div');
    const boton = h('button', { class: 'secundario', type: 'button' }, 'Ver la ubicación exacta');
    boton.addEventListener('click', async () => {
      vaciar(zona, ui.cargando('Pidiendo la coordenada a la autorización…'));
      try {
        const p = await ui.conBotonOcupado(boton, () => api.get('/hallazgos/' + hallazgo.id + '/coordenada'), 'Consultando…');
        boton.hidden = true;
        const lienzo = h('div', { class: 'mapa mapa-pequeno', role: 'region', 'aria-label': 'Ubicación del hallazgo' });
        const extra = Object.entries(p.datosDeUbicacion || {});
        vaciar(zona, ui.mensaje('info', 'Su consulta quedó registrada.'),
          h('p', null, h('code', null, p.latitud.toFixed(6) + ', ' + p.longitud.toFixed(6)),
            p.precisionMetros ? ' (± ' + p.precisionMetros + ' m)' : ''),
          extra.length ? h('table', null, h('tbody', null, extra.map(([nombre, valor]) => h('tr', null,
            h('th', { style: 'width:35%' }, BioHub.formularios.nombreBonito(nombre)),
            h('td', null, Array.isArray(valor) ? valor.length + ' puntos de ruta (en rojo en el mapa)' : String(valor)))))) : null,
          lienzo);
        const mapa = await dibujar(lienzo, [p]);
        // Las rutas GPS (lista de {lat, lon}) se dibujan como una línea
        for (const [, valor] of extra) {
          if (Array.isArray(valor) && valor.every((x) => x && typeof x.lat === 'number')) {
            const linea = L.polyline(valor.map((x) => [x.lat, x.lon]), { color: '#a3372a', weight: 3 }).addTo(mapa);
            mapa.fitBounds(linea.getBounds().extend([p.latitud, p.longitud]).pad(0.5), { maxZoom: 13 });
          }
        }
      } catch (err) {
        vaciar(zona, err.estado === 403
          ? ui.mensaje('error', err.message, ['Esta consulta negada también quedó en el registro de accesos.'])
          : ui.error(err));
      }
    });
    return h('section', { class: 'tarjeta' }, h('h2', null, 'Ubicación exacta'),
      h('p', { class: 'suave pequeno' }, 'Al pedirla, la autorización revisa su rol y deja registro de la consulta, se la muestre o no.'),
      boton, zona);
  }
})();
