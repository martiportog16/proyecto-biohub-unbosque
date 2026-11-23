/*
 * Reclasificación e historial de un hallazgo (HU-06, HU-07, RF-06, RF-07, RF-08, RNF-05).
 * - El director científico reclasifica, con motivo obligatorio.
 * - El director y el auditor ven el historial completo y comparan el estado en una fecha
 *   con el actual. El auditor solo lee: su pantalla no tiene ningún botón que cambie algo.
 * El historial nunca se edita ni se borra: lo impide la base de datos (paquete 05).
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui, sesion, catalogo, formularios } = window.BioHub;
  const { error, limpiarErrores, marcarErrores } = formularios;

  const LECTORES = ['DIRECTOR_CIENTIFICO', 'AUDITOR_CIENTIFICO'];
  const MINIMO_MOTIVO = 10;
  const esDirector = () => sesion.tiene('DIRECTOR_CIENTIFICO');

  BioHub.registrar({
    id: 'historial',
    titulo: 'Historial y reclasificación',
    grupo: 'Auditoría',
    roles: LECTORES,
    resumen: 'Cada cambio de especie de un hallazgo: quién, cuándo, por qué. Comparar con una fecha anterior.',
    mostrar(contenedor, [id]) {
      return id ? detalle(contenedor, id) : buscar(contenedor);
    }
  });

  // En el detalle de cada hallazgo (pantalla Hallazgos), un acceso directo al historial
  if (BioHub.hallazgos) {
    BioHub.hallazgos.agregarAccion((hallazgo) => sesion.tiene(...LECTORES)
      ? h('section', { class: 'tarjeta' }, h('h2', null, 'Historial'),
        h('p', { class: 'suave pequeno' }, esDirector()
          ? 'Vea los cambios de especie de este hallazgo o reclasifíquelo si hay nueva evidencia.'
          : 'Vea los cambios de especie de este hallazgo y compare con una fecha anterior.'),
        h('a', { class: 'boton primario', href: '#/historial/' + hallazgo.id }, esDirector() ? 'Historial y reclasificar' : 'Ver historial'))
      : null);
  }

  /* ---------- Buscar un hallazgo ---------- */

  function buscar(contenedor) {
    ui.titulo(contenedor, 'Historial y reclasificación',
      esDirector() ? 'Escriba el número del hallazgo para ver su historial o reclasificarlo.'
        : 'Escriba el número del hallazgo para revisar su historial. Usted tiene acceso de solo lectura.');
    const numero = h('input', { id: 'numero-hallazgo', type: 'number', min: 1, inputmode: 'numeric', required: true, placeholder: '1250' });
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('label', { for: 'numero-hallazgo' }, 'Número del hallazgo'),
      h('div', { class: 'acciones', style: 'margin-top:0' }, h('div', { style: 'flex:0 1 220px' }, numero),
        h('button', { type: 'submit', class: 'primario' }, 'Abrir')),
      h('p', { class: 'ayuda-campo' }, 'También puede llegar desde ', h('a', { href: '#/hallazgos' }, 'la lista de hallazgos'), '.'));
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      if (numero.value) location.hash = '#/historial/' + Number(numero.value);
    });
    contenedor.append(form);
  }

  /* ---------- Historial de un hallazgo ---------- */

  async function detalle(contenedor, id) {
    contenedor.append(h('p', { class: 'migas' }, h('a', { href: '#/historial' }, '← Buscar otro hallazgo'), ' · ',
      h('a', { href: '#/hallazgos/' + id }, 'Ver el hallazgo')));
    const zona = h('div', null, ui.cargando('Cargando el historial…'));
    contenedor.append(zona);

    const pintar = async () => {
      let historial;
      try {
        historial = await api.get('/hallazgos/' + encodeURIComponent(id) + '/historial');
      } catch (e) {
        vaciar(zona, e.estado === 404 ? ui.mensaje('info', 'No existe el hallazgo #' + id + '.') : ui.error(e));
        return;
      }
      vaciar(zona);
      ui.titulo(zona, 'Historial del hallazgo #' + historial.hallazgoId, null);
      const n = historial.cambios.length;
      zona.append(
        h('div', { class: 'cifras' },
          cifra('Especie actual', h('em', null, historial.especieActual.nombreCientifico)),
          cifra('Especie con que se registró', h('em', null, historial.especieInicial.nombreCientifico)),
          cifra('Reclasificaciones', String(n))),
        h('section', { class: 'tarjeta' },
          h('h2', null, 'Línea de tiempo'),
          h('p', { class: 'suave pequeno' }, 'En orden cronológico. Nadie puede editar ni borrar estas entradas, ni siquiera el director: la base de datos lo impide.'),
          lineaDeTiempo(historial)),
        compararConFecha(id, historial),
        esDirector() ? await formularioReclasificar(id, historial, pintar)
          : ui.mensaje('info', 'Acceso de solo lectura: el auditor revisa el historial, pero no puede cambiarlo (HU-07, criterio 4).'));
    };
    await pintar();
  }

  function cifra(rotulo, valor) {
    return h('div', { class: 'cifra' }, h('div', { class: 'rotulo' }, rotulo), h('div', { class: 'valor', style: 'font-size:1.1rem' }, valor));
  }

  function lineaDeTiempo(historial) {
    const filas = [h('tr', null,
      h('td', null, ui.fechaHora(historial.registradoEn)),
      h('td', null, h('span', { class: 'chip info' }, 'Registro')),
      h('td', null, '—'),
      h('td', null, h('em', null, historial.especieInicial.nombreCientifico)),
      h('td', null, '—'),
      h('td', { class: 'suave' }, 'Registro inicial en campo'))];
    for (const c of historial.cambios) {
      filas.push(h('tr', null,
        h('td', null, ui.fechaHora(c.realizadaEn)),
        h('td', null, h('span', { class: 'chip aviso' }, 'Reclasificación')),
        h('td', null, h('em', null, c.especieAnterior.nombreCientifico)),
        h('td', null, h('em', null, c.especieNueva.nombreCientifico)),
        h('td', null, c.autor.nombre),
        h('td', null, c.motivo)));
    }
    return h('div', { class: 'tabla-envoltura' }, h('table', null,
      h('thead', null, h('tr', null, h('th', null, 'Fecha'), h('th', null, 'Qué'), h('th', null, 'Antes'),
        h('th', null, 'Después'), h('th', null, 'Autor'), h('th', null, 'Motivo'))),
      h('tbody', null, filas)));
  }

  /* ---------- Comparar con una fecha (HU-07, criterio 3) ---------- */

  function compararConFecha(id, historial) {
    const fecha = h('input', { type: 'date', id: 'fecha-comparar', required: true, max: ui.hoy() });
    // Por defecto, el día antes del primer cambio (o el día del registro): lo más útil para comparar
    const referencia = historial.cambios.length ? new Date(new Date(historial.cambios[0].realizadaEn).getTime() - 86400000) : new Date(historial.registradoEn);
    fecha.value = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(referencia);
    const resultado = h('div', { 'aria-live': 'polite' });
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Comparar con hoy');
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('h2', null, 'Comparar con una fecha anterior'),
      h('p', { class: 'suave pequeno' }, 'Muestra la especie que tenía el hallazgo al final de ese día (hora de Colombia) y la de hoy.'),
      h('label', { for: 'fecha-comparar' }, 'Fecha'),
      h('div', { class: 'acciones', style: 'margin-top:0' }, h('div', { style: 'flex:0 1 220px' }, fecha), boton),
      resultado);
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      vaciar(resultado);
      if (!fecha.value) {
        resultado.append(ui.mensaje('error', 'Escoja una fecha.'));
        return;
      }
      try {
        const c = await ui.conBotonOcupado(boton, () => api.get('/hallazgos/' + id + '/historial/comparacion?fecha=' + fecha.value), 'Comparando…');
        vaciar(resultado, comparacion(c));
      } catch (err) {
        vaciar(resultado, ui.error(err));
      }
    });
    return form;
  }

  function comparacion(c) {
    if (!c.existiaEnEsaFecha) {
      return ui.mensaje('info', 'El ' + ui.fecha(c.fecha) + ' el hallazgo todavía no existía. Escoja una fecha posterior a su registro.');
    }
    return [
      h('div', { class: 'cifras', style: 'margin-top:16px' },
        cifra('El ' + ui.fecha(c.fecha), h('em', null, c.especieEnEsaFecha.nombreCientifico)),
        cifra('Hoy', h('em', null, c.especieActual.nombreCientifico))),
      c.cambioDesdeEntonces
        ? [ui.mensaje('aviso', 'Cambió ' + c.cambiosDesdeEntonces.length + (c.cambiosDesdeEntonces.length === 1 ? ' vez' : ' veces') + ' desde esa fecha:'),
          h('ul', null, c.cambiosDesdeEntonces.map((x) => h('li', null,
            ui.fechaHora(x.realizadaEn) + ': ', h('em', null, x.especieAnterior.nombreCientifico), ' → ',
            h('em', null, x.especieNueva.nombreCientifico), ' (' + x.autor.nombre + ': "' + x.motivo + '")')))]
        : ui.mensaje('exito', 'No ha cambiado desde esa fecha.')
    ];
  }

  /* ---------- Reclasificar (solo director, HU-06) ---------- */

  async function formularioReclasificar(id, historial, alTerminar) {
    const especies = (await catalogo.especies()).filter((e) => e.id !== historial.especieActual.id);
    const especie = h('select', { id: 'especie-nueva', required: true }, h('option', { value: '' }, 'Escoja la especie correcta…'),
      especies.map((e) => h('option', { value: e.id }, e.nombreCientifico + (e.nombreComun ? ' · ' + e.nombreComun : ''))));
    const motivo = h('textarea', { id: 'motivo', required: true, placeholder: 'Análisis de ADN mitocondrial de la muestra M-0412 indica…' });
    const contador = h('div', { class: 'ayuda-campo' });
    const contar = () => {
      const n = motivo.value.trim().length;
      contador.textContent = n < MINIMO_MOTIVO ? 'Faltan ' + (MINIMO_MOTIVO - n) + ' caracteres (mínimo ' + MINIMO_MOTIVO + ').' : n + ' caracteres.';
    };
    motivo.addEventListener('input', contar);
    contar();
    const resultado = h('div');
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Reclasificar');
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('h2', null, 'Reclasificar'),
      h('p', { class: 'suave pequeno' }, 'Actual: ', h('em', null, historial.especieActual.nombreCientifico),
        '. El cambio queda en el historial con su nombre, la fecha y el motivo, y no se puede deshacer: si hay un error, se corrige con otra reclasificación.'),
      h('label', { for: 'especie-nueva' }, 'Nueva especie'), especie, error('especieNuevaId'),
      h('label', { for: 'motivo' }, 'Motivo (la nueva evidencia)'), motivo, contador, error('motivo'),
      resultado, h('div', { class: 'acciones' }, boton));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      limpiarErrores(form);
      vaciar(resultado);
      try {
        const r = await ui.conBotonOcupado(boton, () => api.post('/hallazgos/' + id + '/reclasificaciones', {
          especieNuevaId: especie.value ? Number(especie.value) : null, motivo: motivo.value
        }), 'Guardando…');
        ui.aviso('Reclasificado: ' + r.especieAnterior.nombreCientifico + ' → ' + r.especieNueva.nombreCientifico + '.');
        await alTerminar();
      } catch (err) {
        marcarErrores(form, err.detalles || []);
        vaciar(resultado, err.estado === 409
          ? ui.mensaje('error', 'Otra persona reclasificó este hallazgo al mismo tiempo. Se recargó el historial: revíselo antes de intentar de nuevo.')
          : ui.error(err));
        if (err.estado === 409) setTimeout(alTerminar, 2500);
      }
    });
    return form;
  }
})();
