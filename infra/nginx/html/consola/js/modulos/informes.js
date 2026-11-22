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
