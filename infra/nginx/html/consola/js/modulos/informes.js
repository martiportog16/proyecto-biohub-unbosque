/*
 * Analista: índices mensuales de presión sobre los ecosistemas (HU-11, RF-14).
 * El informe lo calcula el batch en su propia réplica (RNF-09). Desde aquí se consulta
 * y se puede pedir el de un mes que todavía no exista.
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui } = window.BioHub;

  const UMBRAL_ALTA = 0.5;
  const ESTADOS = {
    PENDIENTE: ['En cola', 'aviso'],
    ATENDIDA: ['Listo', ''],
    SIN_DATOS: ['Sin hallazgos ese mes', 'gris'],
    INFORME_YA_EXISTE: ['Ya existía', 'info']
  };
  let temporizador = null;

  BioHub.registrar({
    id: 'informes',
    titulo: 'Índices de presión',
    grupo: 'Analítica',
    roles: ['ANALISTA'],
    resumen: 'Informes mensuales por departamento y solicitudes al proceso batch.',
    mostrar(contenedor, [periodo]) {
      clearTimeout(temporizador);
      return periodo ? detalle(contenedor, periodo) : lista(contenedor);
    }
  });

  /* ---------- Lista de informes y solicitudes ---------- */

  async function lista(contenedor) {
    ui.titulo(contenedor, 'Índices de presión',
      'Si en un departamento se ven menos especies de lo habitual, el índice sube (de 0 a 1).');

    const zonaInformes = h('div', null, ui.cargando('Cargando informes…'));
    const zonaSolicitudes = h('div');
    contenedor.append(
      h('section', { class: 'tarjeta' }, h('h2', null, 'Informes generados'), zonaInformes),
      formularioSolicitud(zonaSolicitudes),
      h('section', { class: 'tarjeta' }, h('h2', null, 'Mis solicitudes'), zonaSolicitudes));

    try {
      const informes = await api.get('/informes');
      vaciar(zonaInformes, informes.length ? tablaInformes(informes)
        : ui.vacio('Todavía no hay informes. El batch genera uno el día 1 de cada mes, o puede pedir uno abajo.'));
    } catch (e) {
      vaciar(zonaInformes, ui.error(e));
    }
    await cargarSolicitudes(zonaSolicitudes);
  }

  function tablaInformes(informes) {
    return h('div', { class: 'tabla-envoltura' }, h('table', null,
      h('thead', null, h('tr', null, h('th', null, 'Mes'), h('th', null, 'Generado'), h('th', { class: 'numero' }, 'Departamentos'), h('th'))),
      h('tbody', null, informes.map((i) => h('tr', {
        class: 'clic',
        onClick: () => { location.hash = '#/informes/' + i.periodo; }
      },
        h('td', null, h('strong', null, ui.periodo(i.periodo))),
        h('td', null, ui.fechaHora(i.generadoEn)),
        h('td', { class: 'numero' }, i.totalDepartamentos),
        h('td', null, h('a', { href: '#/informes/' + i.periodo }, 'Ver')))))));
  }

  function formularioSolicitud(zonaSolicitudes) {
    const mes = h('input', { type: 'month', id: 'mes-solicitud', required: true, max: ui.hoy().slice(0, 7) });
    const resultado = h('div');
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Pedir informe');
    return h('form', {
      class: 'tarjeta',
      onSubmit: async (e) => {
        e.preventDefault();
        vaciar(resultado);
        if (!mes.value) {
          resultado.append(ui.mensaje('error', 'Escoja un mes.'));
          return;
        }
        try {
          const s = await ui.conBotonOcupado(boton, () => api.post('/informes/solicitudes', { periodo: mes.value }), 'Enviando…');
          if (s.estado === 'INFORME_YA_EXISTE') {
            resultado.append(ui.mensaje('info', 'El informe de ' + ui.periodo(s.periodo) + ' ya existe.'),
              h('p', null, h('a', { href: '#/informes/' + s.periodo }, 'Abrir el informe')));
          } else {
            resultado.append(ui.mensaje('exito', 'Solicitud enviada. El batch la atiende en menos de un minuto; esta lista se actualiza sola.'));
          }
          await cargarSolicitudes(zonaSolicitudes);
        } catch (err) {
          resultado.append(ui.error(err));
        }
      }
    },
    h('h2', null, 'Pedir el informe de un mes'),
    h('p', { class: 'suave pequeno' }, 'Útil para un mes pasado que no tiene informe. El cálculo corre en el proceso batch, en su propia réplica, sin frenar el registro de hallazgos.'),
    h('label', { for: 'mes-solicitud' }, 'Mes'),
    h('div', { class: 'acciones', style: 'margin-top:0' }, h('div', { style: 'flex:0 1 220px' }, mes), boton),
    resultado);
  }

  async function cargarSolicitudes(zona) {
    clearTimeout(temporizador);
    try {
      const solicitudes = await api.get('/informes/solicitudes');
      vaciar(zona, solicitudes.length ? h('div', { class: 'tabla-envoltura' }, h('table', null,
        h('thead', null, h('tr', null, h('th', null, 'Mes'), h('th', null, 'Pedida'), h('th', null, 'Estado'), h('th', null, 'Atendida'))),
        h('tbody', null, solicitudes.map((s) => {
          const [texto, clase] = ESTADOS[s.estado] || [s.estado, 'gris'];
          return h('tr', null,
            h('td', null, s.estado === 'ATENDIDA'
              ? h('a', { href: '#/informes/' + s.periodo }, ui.periodo(s.periodo)) : ui.periodo(s.periodo)),
            h('td', null, ui.fechaHora(s.solicitadoEn)),
            h('td', null, h('span', { class: 'chip ' + clase }, texto)),
            h('td', null, ui.fechaHora(s.atendidaEn)));
        }))))
        : ui.vacio('No ha pedido informes.'));
      // Mientras haya alguna en cola, se revisa cada 10 s (el batch revisa cada 30 s)
      if (solicitudes.some((s) => s.estado === 'PENDIENTE') && document.body.contains(zona)) {
        temporizador = setTimeout(() => cargarSolicitudes(zona), 10000);
      }
    } catch (e) {
      vaciar(zona, ui.error(e));
    }
  }

  /* ---------- Detalle de un informe ---------- */

  async function detalle(contenedor, periodo) {
    contenedor.append(h('p', { class: 'migas' }, h('a', { href: '#/informes' }, '← Todos los informes')));
    const cuerpo = h('div', null, ui.cargando('Cargando el informe…'));
    contenedor.append(cuerpo);
    let informe;
    try {
      informe = await api.get('/informes/' + encodeURIComponent(periodo));
    } catch (e) {
      vaciar(cuerpo, e.estado === 404
        ? ui.mensaje('info', 'No hay informe de ' + ui.periodo(periodo) + '. Puede pedirlo en la lista de informes.')
        : ui.error(e));
      return;
    }
    vaciar(cuerpo);
    ui.titulo(cuerpo, 'Índices de ' + ui.periodo(informe.periodo), 'Generado el ' + ui.fechaHora(informe.generadoEn));

    const conDatos = informe.indices.filter((i) => !i.sinDatos);
    const altos = conDatos.filter((i) => i.valor >= UMBRAL_ALTA);
    cuerpo.append(h('div', { class: 'cifras' },
      cifra(informe.totalDepartamentos, 'departamentos con hallazgos'),
      cifra(altos.length, 'con presión alta (≥ ' + ui.numero(UMBRAL_ALTA, 1) + ')'),
      cifra(informe.indices.length - conDatos.length, 'sin meses anteriores para comparar')));

    cuerpo.append(seccionMapa(informe));

    const soloAltos = h('input', { type: 'checkbox', id: 'solo-altos' });
    const tabla = h('div', { class: 'tabla-envoltura' });
    const pintar = () => vaciar(tabla, tablaIndices(soloAltos.checked ? altos : ordenar(informe.indices)));
    soloAltos.addEventListener('change', pintar);
    pintar();

    cuerpo.append(h('section', { class: 'tarjeta' },
      h('div', { class: 'acciones', style: 'margin-top:0;justify-content:space-between' },
        h('h2', { style: 'margin:0' }, 'Por departamento'),
        h('label', { for: 'solo-altos', style: 'margin:0;font-weight:400' }, soloAltos, 'Solo presión alta')),
      tabla),
    h('section', { class: 'tarjeta' }, h('h2', null, 'Cómo se calcula'),
      h('p', null, 'Índice = (B − E) / B, y nunca menos de 0.'),
      h('ul', null,
        h('li', null, 'E: especies distintas vistas en el departamento ese mes.'),
        h('li', null, 'B: promedio de E en los meses anteriores (hasta 3).')),
      h('p', { class: 'suave' }, 'Un índice de 0 significa que se vieron tantas especies como siempre o más. Uno de 1 significa que no se vio ninguna. Es un indicio para revisar la zona, no una prueba de deforestación.')));
  }

  /* ---------- Mapa de presión: los mismos datos de la tabla, sobre Colombia ---------- */

  // Escalas: índice (claro = sin presión, rojo = presión alta) y especies vistas (más verde = más especies)
  const ESCALA_INDICE = [
    { hasta: 0, color: '#dcebe0', texto: '0 (sin presión)' },
    { hasta: 0.25, color: '#f3e0a6', texto: 'hasta 0,25' },
    { hasta: UMBRAL_ALTA, color: '#eeae6c', texto: 'hasta ' + ui.numero(UMBRAL_ALTA, 2) },
    { hasta: 0.75, color: '#d9694f', texto: 'alta, hasta 0,75' },
    { hasta: Infinity, color: '#a92f25', texto: 'alta, más de 0,75' }
  ];
  const ESCALA_ESPECIES = ['#e3f0e7', '#b9dbc4', '#82bf98', '#4a9a6b', '#1c6e4c'];
  const SIN_DATOS = '#c9cfca';
  const COLOMBIA = [[-4.3, -79.1], [12.6, -66.8]];   // sin San Andrés: si no, el país se ve diminuto
  let geoDepartamentos = null;

  function seccionMapa(informe) {
    const porNombre = new Map(informe.indices.map((i) => [i.departamento, i]));
    const maxEspecies = Math.max(1, ...informe.indices.map((i) => i.especiesEnElMes || 0));
    const lienzo = h('div', { class: 'mapa mapa-informe', role: 'img',
      'aria-label': 'Mapa de Colombia coloreado por departamento. Los mismos datos están en la tabla de abajo.' });
    const leyenda = h('ul', { class: 'leyenda' });
    const opcion = (valor, texto, marcada) => h('label', { class: 'opcion-mapa' },
      h('input', { type: 'radio', name: 'medida-mapa', value: valor, checked: marcada }), texto);
    const selector = h('div', { class: 'selector-mapa', role: 'radiogroup', 'aria-label': 'Qué colorear' },
      opcion('indice', 'Índice de presión', true), opcion('especies', 'Especies vistas este mes', false));
    let medida = 'indice';
    let capa = null;

    const colorDe = (i) => {
      if (!i || i.sinDatos && medida === 'indice') return SIN_DATOS;
      if (medida === 'especies') {
        const paso = Math.min(4, Math.floor((i.especiesEnElMes / maxEspecies) * 5));
        return ESCALA_ESPECIES[paso];
      }
      return ESCALA_INDICE.find((e) => i.valor <= e.hasta).color;
    };
    const textoDe = (nombre, i) => {
      if (!i) return nombre + ': sin hallazgos este mes';
      if (medida === 'especies') return nombre + ': ' + ui.numero(i.especiesEnElMes) + ' especies vistas';
      if (i.sinDatos) return nombre + ': sin meses anteriores para comparar';
      return nombre + ': índice ' + ui.numero(i.valor, 2) + (i.valor >= UMBRAL_ALTA ? ' (presión alta)' : '');
    };
    const pintarLeyenda = () => {
      const filas = medida === 'indice'
        ? ESCALA_INDICE.map((e) => [e.color, e.texto]).concat([[SIN_DATOS, 'sin datos']])
        : [[ESCALA_ESPECIES[0], 'pocas especies'], [ESCALA_ESPECIES[2], ''], [ESCALA_ESPECIES[4], 'más especies (' + ui.numero(maxEspecies) + ')']];
      vaciar(leyenda, filas.map(([color, texto]) => h('li', null, h('span', { class: 'muestra', style: 'background:' + color }), texto)));
    };
    const repintar = () => {
      if (capa) capa.setStyle((f) => estilo(f));
      if (capa) capa.eachLayer((l) => { const n = l.feature.properties.nombre; l.setTooltipContent(h('span', null, textoDe(n, porNombre.get(n)))); });
      pintarLeyenda();
    };
    const estilo = (f) => ({ fillColor: colorDe(porNombre.get(f.properties.nombre)), fillOpacity: 0.92, color: '#ffffff', weight: 1 });
    selector.addEventListener('change', (ev) => { medida = ev.target.value; repintar(); });

    const seccion = h('section', { class: 'tarjeta' },
      h('div', { class: 'acciones', style: 'margin-top:0;justify-content:space-between' },
        h('h2', { style: 'margin:0' }, 'Mapa de presión'), selector),
      lienzo, leyenda,
      h('p', { class: 'suave pequeno' }, 'Pase el cursor sobre un departamento para ver su valor. San Andrés y Providencia está en la tabla.'));

    // Leaflet necesita el lienzo ya en la página para medirlo
    requestAnimationFrame(async () => {
      if (typeof L === 'undefined') { lienzo.replaceWith(ui.mensaje('aviso', 'No se pudo cargar el mapa. Los datos están en la tabla.')); return; }
      const mapa = L.map(lienzo, { attributionControl: false, scrollWheelZoom: false, zoomSnap: 0.25 });
      mapa.fitBounds(COLOMBIA, { padding: [6, 6] });
      try {
        if (!geoDepartamentos) geoDepartamentos = await (await fetch('datos/colombia-departamentos.geojson')).json();
        capa = L.geoJSON(geoDepartamentos, {
          style: estilo,
          onEachFeature: (f, l) => {
            l.bindTooltip(h('span', null, textoDe(f.properties.nombre, porNombre.get(f.properties.nombre))), { sticky: true });
            l.on('mouseover', () => l.setStyle({ weight: 2.5, color: '#0e3b2e' }));
            l.on('mouseout', () => capa.resetStyle(l));
          }
        }).addTo(mapa);
        pintarLeyenda();
      } catch (e) {
        lienzo.replaceWith(ui.mensaje('aviso', 'No se pudo cargar el mapa. Los datos están en la tabla.'));
      }
    });
    return seccion;
  }

  function ordenar(indices) {
    return [...indices].sort((a, b) => (b.sinDatos ? -1 : b.valor) - (a.sinDatos ? -1 : a.valor)
      || a.departamento.localeCompare(b.departamento));
  }

  function cifra(valor, rotulo) {
    return h('div', { class: 'cifra' }, h('div', { class: 'valor' }, ui.numero(valor)), h('div', { class: 'rotulo' }, rotulo));
  }

  function tablaIndices(indices) {
    if (!indices.length) return ui.vacio('Ningún departamento tiene presión alta este mes.');
    return h('table', null,
      h('thead', null, h('tr', null,
        h('th', null, 'Departamento'), h('th', { class: 'numero' }, 'Índice'), h('th', { style: 'width:30%' }),
        h('th', { class: 'numero' }, 'Especies este mes'), h('th', { class: 'numero' }, 'Promedio anterior'))),
      h('tbody', null, indices.map((i) => h('tr', null,
        h('td', null, i.departamento),
        h('td', { class: 'numero' }, i.sinDatos ? h('span', { class: 'chip gris' }, 'sin datos') : ui.numero(i.valor, 2)),
        h('td', null, i.sinDatos ? null : h('div', {
          class: 'barra-valor' + (i.valor >= UMBRAL_ALTA ? ' alta' : ''),
          role: 'img', 'aria-label': 'Índice ' + ui.numero(i.valor, 2)
        }, h('div', { style: 'width:' + Math.round(i.valor * 100) + '%' }))),
        h('td', { class: 'numero' }, ui.numero(i.especiesEnElMes)),
        h('td', { class: 'numero' }, i.sinDatos ? '—' : ui.numero(i.base, 1))))));
  }
})();
