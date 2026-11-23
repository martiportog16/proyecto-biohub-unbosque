/*
 * Equipos de campo (HU-03, RF-03, RF-04, CU-03, CU-04).
 * Todo el personal científico consulta quién tiene cada equipo. El investigador, el
 * director y el administrador científico los registran y los pasan a otra persona.
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui, sesion, formularios } = window.BioHub;
  const { error, limpiarErrores, marcarErrores } = formularios;

  // CU-03 (asociar un equipo a su responsable) y CU-04 (consultar quién lo tiene): el investigador
  const puedeAsignar = () => sesion.tiene('INVESTIGADOR');
  const TIPOS = { DRON: 'Dron', CAMARA_TRAMPA: 'Cámara trampa', SENSOR: 'Sensor', GPS: 'GPS', GRABADORA_ACUSTICA: 'Grabadora acústica', OTRO: 'Otro' };

  BioHub.registrar({
    id: 'equipos',
    titulo: 'Equipos de campo',
    grupo: 'Equipos de campo',
    roles: ['INVESTIGADOR'],
    resumen: 'Drones, cámaras y sensores: quién tiene cada uno y su historial.',
    mostrar(contenedor, [id]) {
      if (id === 'nuevo') return nuevo(contenedor);
      return id ? detalle(contenedor, id) : lista(contenedor);
    }
  });

  /* ---------- Lista y búsqueda ---------- */

  async function lista(contenedor) {
    ui.titulo(contenedor, 'Equipos de campo', 'Cada equipo tiene siempre un responsable.');
    const consulta = new URLSearchParams(location.hash.split('?')[1] || '');
    const soloMios = consulta.get('mios') === '1';

    const codigo = h('input', { id: 'buscar-codigo', placeholder: 'DRON-007', autocomplete: 'off' });
    const resultadoBusqueda = h('div');
    const buscar = h('form', { class: 'tarjeta', novalidate: true },
      h('h2', null, '¿Quién tiene este equipo?'),
      h('label', { for: 'buscar-codigo' }, 'Código del equipo'),
      h('div', { class: 'acciones', style: 'margin-top:0' }, h('div', { style: 'flex:1 1 200px' }, codigo),
        h('button', { type: 'submit', class: 'primario' }, 'Buscar')),
      resultadoBusqueda);
    buscar.addEventListener('submit', async (e) => {
      e.preventDefault();
      vaciar(resultadoBusqueda);
      if (!codigo.value.trim()) return;
      try {
        const eq = await api.get('/equipos/codigo/' + encodeURIComponent(codigo.value.trim()));
        location.hash = '#/equipos/' + eq.id;
      } catch (err) {
        vaciar(resultadoBusqueda, err.estado === 404 ? ui.mensaje('info', 'No hay ningún equipo con el código ' + codigo.value.trim().toUpperCase() + '.') : ui.error(err));
      }
    });

    const zona = h('div', null, ui.cargando());
    const filtro = h('div', { class: 'acciones', style: 'margin:0 0 12px' },
      h('a', { class: 'boton ' + (soloMios ? 'secundario' : 'primario'), href: '#/equipos' }, 'Todos'),
      h('a', { class: 'boton ' + (soloMios ? 'primario' : 'secundario'), href: '#/equipos?mios=1' }, 'A mi cargo'),
      puedeAsignar() ? h('a', { class: 'boton secundario', href: '#/equipos/nuevo', style: 'margin-left:auto' }, '+ Registrar equipo') : null);
    contenedor.append(buscar, filtro, zona);

    try {
      const equipos = await api.get('/equipos' + (soloMios ? '?responsableId=' + sesion.actual().id : ''));
      vaciar(zona, equipos.length ? h('div', { class: 'tarjeta tabla-envoltura' }, h('table', null,
        h('thead', null, h('tr', null, h('th', null, 'Código'), h('th', null, 'Tipo'), h('th', null, 'Descripción'), h('th', null, 'Responsable'))),
        h('tbody', null, equipos.map((eq) => h('tr', { class: 'clic', onClick: () => { location.hash = '#/equipos/' + eq.id; } },
          h('td', null, h('a', { href: '#/equipos/' + eq.id }, h('code', null, eq.codigo))),
          h('td', null, TIPOS[eq.tipo] || eq.tipo),
          h('td', { class: 'pequeno' }, eq.descripcion || '—'),
          h('td', null, eq.responsable.nombre))))))
        : ui.vacio(soloMios ? 'No tiene equipos a su cargo.' : 'No hay equipos registrados.'));
    } catch (e) {
      vaciar(zona, ui.error(e));
    }
  }

  /** Lista de selección con el personal científico activo; la persona actual va preseleccionada. */
  async function selectorResponsable(id, preseleccion) {
    const personas = await api.get('/equipos/responsables-posibles');
    const select = h('select', { id, required: true }, h('option', { value: '' }, 'Escoja la persona…'),
      personas.map((p) => h('option', { value: p.id }, p.nombre + ' · ' + (BioHub.ROLES[p.rol] || p.rol))));
    if (preseleccion) select.value = String(preseleccion);
    return select;
  }

  /* ---------- Registrar ---------- */

  async function nuevo(contenedor) {
    contenedor.append(h('p', { class: 'migas' }, h('a', { href: '#/equipos' }, '← Todos los equipos')));
    ui.titulo(contenedor, 'Registrar equipo', 'Un equipo no se puede registrar sin responsable.');
    if (!puedeAsignar()) {
      contenedor.append(ui.mensaje('error', 'Su rol puede consultar los equipos, pero no registrarlos.'));
      return;
    }
    const codigo = h('input', { id: 'equipo-codigo', required: true, placeholder: 'DRON-007', autocomplete: 'off' });
    const tipo = h('select', { id: 'equipo-tipo', required: true }, h('option', { value: '' }, 'Escoja…'),
      Object.entries(TIPOS).map(([k, v]) => h('option', { value: k }, v)));
    const descripcion = h('input', { id: 'equipo-descripcion', placeholder: 'DJI Mavic 3, número de serie…' });
    const responsable = await selectorResponsable('equipo-responsable', sesion.actual().id);
    const resultado = h('div');
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Registrar equipo');
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('div', { class: 'fila-campos' },
        h('div', null, h('label', { for: 'equipo-codigo' }, 'Código'), codigo,
          h('div', { class: 'ayuda-campo' }, 'Letras, números y guiones. Debe ser único.'), error('codigo')),
        h('div', null, h('label', { for: 'equipo-tipo' }, 'Tipo'), tipo, error('tipo'))),
      h('label', { for: 'equipo-descripcion' }, 'Descripción ', h('span', { class: 'opcional' }, '(opcional)')), descripcion,
      h('label', { for: 'equipo-responsable' }, 'Responsable'), responsable, error('responsableId'),
      resultado, h('div', { class: 'acciones' }, boton));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      limpiarErrores(form);
      vaciar(resultado);
      try {
        const eq = await ui.conBotonOcupado(boton, () => api.post('/equipos', {
          codigo: codigo.value, tipo: tipo.value || null, descripcion: descripcion.value || null,
          responsableId: responsable.value ? Number(responsable.value) : null
        }), 'Registrando…');
        ui.aviso('Equipo ' + eq.codigo + ' registrado a cargo de ' + eq.responsable.nombre + '.');
        location.hash = '#/equipos/' + eq.id;
      } catch (err) {
        marcarErrores(form, err.detalles || []);
        vaciar(resultado, ui.error(err));
      }
    });
    contenedor.append(form);
  }

  /* ---------- Detalle, reasignar e historial ---------- */

  async function detalle(contenedor, id) {
    contenedor.append(h('p', { class: 'migas' }, h('a', { href: '#/equipos' }, '← Todos los equipos')));
    const zona = h('div', null, ui.cargando());
    contenedor.append(zona);

    const pintar = async () => {
      let equipo, historial;
      try {
        [equipo, historial] = await Promise.all([api.get('/equipos/' + encodeURIComponent(id)), api.get('/equipos/' + encodeURIComponent(id) + '/asignaciones')]);
      } catch (e) {
        vaciar(zona, e.estado === 404 ? ui.mensaje('info', 'No existe el equipo ' + id + '.') : ui.error(e));
        return;
      }
      vaciar(zona);
      ui.titulo(zona, equipo.codigo, (TIPOS[equipo.tipo] || equipo.tipo) + (equipo.descripcion ? ' · ' + equipo.descripcion : ''));
      zona.append(
        h('div', { class: 'cifras' },
          h('div', { class: 'cifra' }, h('div', { class: 'rotulo' }, 'Responsable actual'),
            h('div', { class: 'valor', style: 'font-size:1.25rem' }, equipo.responsable.nombre),
            h('div', { class: 'rotulo' }, (BioHub.ROLES[equipo.responsable.rol] || equipo.responsable.rol) + ' · ' + equipo.responsable.correo)),
          h('div', { class: 'cifra' }, h('div', { class: 'rotulo' }, 'Ha pasado por'),
            h('div', { class: 'valor' }, new Set(historial.map((a) => a.responsableId)).size),
            h('div', { class: 'rotulo' }, 'persona(s)'))),
        puedeAsignar() ? await formularioReasignar(equipo, pintar) : null,
        h('section', { class: 'tarjeta' }, h('h2', null, 'Historial de responsables'),
          h('p', { class: 'suave pequeno' }, 'No se puede editar ni borrar: es la trazabilidad del equipo.'),
          h('div', { class: 'tabla-envoltura' }, h('table', null,
            h('thead', null, h('tr', null, h('th', null, 'Fecha'), h('th', null, 'Recibió'), h('th', null, 'Motivo'))),
            h('tbody', null, [...historial].reverse().map((a) => h('tr', null,
              h('td', null, ui.fechaHora(a.asignadoEn)), h('td', null, a.responsable), h('td', null, a.motivo))))))));
    };
    await pintar();
  }

  async function formularioReasignar(equipo, alTerminar) {
    const responsable = await selectorResponsable('reasignar-responsable');
    const motivo = h('textarea', { id: 'reasignar-motivo', required: true, placeholder: 'Ana sale de vacaciones; Marta lo lleva a la salida al Meta' });
    const resultado = h('div');
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Pasar el equipo');
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('h2', null, 'Pasar a otra persona'),
      h('label', { for: 'reasignar-responsable' }, 'Nuevo responsable'), responsable, error('responsableId'),
      h('label', { for: 'reasignar-motivo' }, 'Motivo'), motivo, error('motivo'),
      resultado, h('div', { class: 'acciones' }, boton));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      limpiarErrores(form);
      vaciar(resultado);
      try {
        const eq = await ui.conBotonOcupado(boton, () => api.put('/equipos/' + equipo.id + '/responsable', {
          responsableId: responsable.value ? Number(responsable.value) : null, motivo: motivo.value
        }), 'Guardando…');
        ui.aviso(eq.codigo + ' ahora está a cargo de ' + eq.responsable.nombre + '.');
        await alTerminar();
      } catch (err) {
        marcarErrores(form, err.detalles || []);
        vaciar(resultado, ui.error(err));
      }
    });
    return form;
  }
})();
