/*
 * Hallazgos registrados: lista con filtros y detalle (HU-01 criterio 4: "queda disponible
 * para consulta autorizada"). Nunca muestra coordenadas: la API de hallazgos no las tiene.
 * Las coordenadas se piden aparte, a la autorización (paquete 20), y cada consulta queda registrada.
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui, catalogo, formularios } = window.BioHub;

  const CIENTIFICOS = ['INVESTIGADOR', 'ADMINISTRADOR_CIENTIFICO', 'DIRECTOR_CIENTIFICO', 'AUDITOR_CIENTIFICO', 'ALTO_RANGO'];
  const POR_PAGINA = 50;

  /** Otras pantallas (por ejemplo reclasificar, paquete 19) agregan acciones al detalle. */
  const accionesDelDetalle = [];
  BioHub.hallazgos = { agregarAccion: (fn) => accionesDelDetalle.push(fn) };

  BioHub.registrar({
    id: 'hallazgos',
    titulo: 'Hallazgos',
    grupo: 'Registro científico',
    roles: CIENTIFICOS,
    resumen: 'Lo registrado en campo, con filtros por especie y departamento.',
    mostrar(contenedor, [id]) {
      return id ? detalle(contenedor, id) : lista(contenedor);
    }
  });

  /* ---------- Lista ---------- */

  async function lista(contenedor) {
    ui.titulo(contenedor, 'Hallazgos', 'Los más recientes primero. Las coordenadas exactas no se muestran aquí.');
    const consulta = new URLSearchParams(location.hash.split('?')[1] || '');
    const [especies, departamentos] = await Promise.all([catalogo.especies(), catalogo.departamentos()]);

    const filtroEspecie = h('select', { id: 'filtro-especie' }, h('option', { value: '' }, 'Todas'),
      especies.map((e) => h('option', { value: e.id }, e.nombreCientifico)));
    const filtroDepartamento = h('select', { id: 'filtro-departamento' }, h('option', { value: '' }, 'Todos'),
      departamentos.map((d) => h('option', { value: d }, d)));
    filtroEspecie.value = consulta.get('especie') || '';
    filtroDepartamento.value = consulta.get('departamento') || '';
    const pagina = Math.max(0, Number(consulta.get('pagina') || 0));

    const ir = (nuevaPagina) => {
      const p = new URLSearchParams();
      if (filtroEspecie.value) p.set('especie', filtroEspecie.value);
      if (filtroDepartamento.value) p.set('departamento', filtroDepartamento.value);
      if (nuevaPagina) p.set('pagina', nuevaPagina);
      location.hash = '#/hallazgos' + (p.toString() ? '?' + p : '');
    };
    filtroEspecie.addEventListener('change', () => ir(0));
    filtroDepartamento.addEventListener('change', () => ir(0));

    const zona = h('div', null, ui.cargando('Buscando hallazgos…'));
    contenedor.append(
      h('div', { class: 'tarjeta fila-campos' },
        h('div', null, h('label', { for: 'filtro-especie' }, 'Especie'), filtroEspecie),
        h('div', null, h('label', { for: 'filtro-departamento' }, 'Departamento'), filtroDepartamento)),
      zona);

    const p = new URLSearchParams({ pagina });
    if (filtroEspecie.value) p.set('especieId', filtroEspecie.value);
    if (filtroDepartamento.value) p.set('departamento', filtroDepartamento.value);
    try {
      const hallazgos = await api.get('/hallazgos?' + p);
      const nombres = new Map(especies.map((e) => [e.id, e.nombreCientifico]));
      vaciar(zona, hallazgos.length ? [
        h('div', { class: 'tarjeta tabla-envoltura' }, h('table', null,
          h('thead', null, h('tr', null, h('th', { class: 'numero' }, '#'), h('th', null, 'Especie'), h('th', null, 'Observado'),
            h('th', null, 'Departamento'), h('th', { class: 'numero' }, 'Ficha'), h('th', null, 'Registrado'))),
          h('tbody', null, hallazgos.map((x) => h('tr', { class: 'clic', onClick: () => { location.hash = '#/hallazgos/' + x.id; } },
            h('td', { class: 'numero' }, h('a', { href: '#/hallazgos/' + x.id }, x.id)),
            h('td', null, h('em', null, nombres.get(x.especieId) || 'Especie ' + x.especieId)),
            h('td', null, ui.fecha(x.fechaObservacion)),
            h('td', null, x.departamento),
            h('td', { class: 'numero' }, 'v' + x.versionFicha),
            h('td', null, ui.fechaHora(x.registradoEn))))))),
        h('div', { class: 'acciones' },
          h('button', { class: 'secundario', disabled: pagina === 0, onClick: () => ir(pagina - 1) }, '← Anteriores'),
          h('span', { class: 'suave pequeno' }, 'Página ' + (pagina + 1)),
          h('button', { class: 'secundario', disabled: hallazgos.length < POR_PAGINA, onClick: () => ir(pagina + 1) }, 'Siguientes →'))
      ] : ui.vacio(pagina ? 'No hay más hallazgos.' : 'No hay hallazgos con estos filtros.'));
    } catch (e) {
      vaciar(zona, ui.error(e));
    }
  }

  /* ---------- Detalle ---------- */

  async function detalle(contenedor, id) {
    contenedor.append(h('p', { class: 'migas' }, h('a', { href: '#/hallazgos' }, '← Todos los hallazgos')));
    let hallazgo;
    try {
      hallazgo = await api.get('/hallazgos/' + encodeURIComponent(id));
    } catch (e) {
      contenedor.append(e.estado === 404 ? ui.mensaje('info', 'No existe el hallazgo #' + id + '.') : ui.error(e));
      return;
    }
    const nombre = await catalogo.nombreEspecie(hallazgo.especieId);
    ui.titulo(contenedor, 'Hallazgo #' + hallazgo.id, nombre);

    const valores = Object.entries(hallazgo.valores || {});
    contenedor.append(
      h('section', { class: 'tarjeta' },
        h('h2', null, 'Datos'),
        h('table', null, h('tbody', null,
          fila('Especie', h('a', { href: '#/especies/' + hallazgo.especieId }, nombre)),
          fila('Fecha de observación', ui.fecha(hallazgo.fechaObservacion)),
          fila('Departamento', hallazgo.departamento),
          fila('Versión de la ficha', 'v' + hallazgo.versionFicha + ' (la vigente cuando se registró)'),
          fila('Registrado', ui.fechaHora(hallazgo.registradoEn)),
          fila('Investigador', '#' + hallazgo.investigadorId)))),
      h('section', { class: 'tarjeta' },
        h('h2', null, 'Datos de la especie'),
        valores.length ? h('table', null, h('tbody', null, valores.map(([k, v]) => fila(formularios.nombreBonito(k), texto(v)))))
          : ui.vacio('Sin datos de ficha.'),
        // La ruta GPS y los archivos revelan la ubicación: el backend no los manda aquí (RNF-06)
        (hallazgo.valoresProtegidos || []).length ? h('p', { class: 'suave pequeno' },
          'Datos de ubicación protegidos: ', hallazgo.valoresProtegidos.map(formularios.nombreBonito).join(', '),
          '. Se ven con "Ver la ubicación exacta", si su rol lo permite.') : null),
      h('section', { class: 'tarjeta' },
        h('h2', null, 'Ubicación'),
        h('p', null, 'Departamento: ', h('strong', null, hallazgo.departamento), '.'),
        h('p', { class: 'suave pequeno' }, 'La coordenada exacta está en una zona protegida. Para verla hay que pedirla a la autorización, que revisa su rol y deja registro de la consulta.')));

    for (const accion of accionesDelDetalle) {
      const pieza = await accion(hallazgo);
      if (pieza) contenedor.append(pieza);
    }
  }

  function fila(rotulo, valor) {
    return h('tr', null, h('th', { style: 'width:35%' }, rotulo), h('td', null, valor));
  }

  /** Muestra cualquier valor de la ficha: texto, número, o la lista de puntos de una ruta GPS. */
  function texto(v) {
    if (Array.isArray(v)) return v.map((p) => (p && p.lat !== undefined ? p.lat + ', ' + p.lon : JSON.stringify(p))).join(' → ');
    if (typeof v === 'number') return ui.numero(v);
    if (v && typeof v === 'object') return JSON.stringify(v);
    return String(v);
  }
})();
