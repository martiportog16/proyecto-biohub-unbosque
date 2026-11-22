/*
 * Grupos taxonómicos y sus fichas (HU-02 criterios 1 y 4, RF-02).
 * Un cambio en la ficha del grupo se COPIA a sus especies que la heredan, y a ninguna otra.
 * Todo el personal científico consulta; solo el administrador científico cambia.
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui, sesion, catalogo, formularios, fichas } = window.BioHub;
  const { error, limpiarErrores, marcarErrores } = formularios;

  const CIENTIFICOS = ['INVESTIGADOR', 'ADMINISTRADOR_CIENTIFICO', 'DIRECTOR_CIENTIFICO', 'AUDITOR_CIENTIFICO', 'ALTO_RANGO'];
  const esAdmin = () => sesion.tiene('ADMINISTRADOR_CIENTIFICO');

  BioHub.registrar({
    id: 'grupos',
    titulo: 'Grupos taxonómicos',
    grupo: 'Fichas taxonómicas',
    roles: CIENTIFICOS,
    resumen: 'Una ficha para todo un grupo (por ejemplo, las aves) que llega a sus especies.',
    mostrar(contenedor, [id]) {
      return id ? detalle(contenedor, id) : lista(contenedor);
    }
  });

  async function lista(contenedor) {
    ui.titulo(contenedor, 'Grupos taxonómicos',
      'Si una especie hereda la ficha de su grupo, recibe cada cambio del grupo. Las especies de otros grupos no se tocan.');
    const zona = h('div', null, ui.cargando());
    contenedor.append(zona);
    if (esAdmin()) contenedor.append(formularioNuevoGrupo());
    try {
      const grupos = await api.get('/grupos');
      vaciar(zona, grupos.length ? h('div', { class: 'tarjeta tabla-envoltura' }, h('table', null,
        h('thead', null, h('tr', null, h('th', null, 'Grupo'), h('th', null, 'Descripción'), h('th', { class: 'numero' }, 'Especies'),
          h('th', { class: 'numero' }, 'Heredan'), h('th', null, 'Ficha'))),
        h('tbody', null, grupos.map((g) => h('tr', { class: 'clic', onClick: () => { location.hash = '#/grupos/' + g.id; } },
          h('td', null, h('a', { href: '#/grupos/' + g.id }, g.nombre)),
          h('td', { class: 'pequeno' }, g.descripcion || '—'),
          h('td', { class: 'numero' }, g.especies.length),
          h('td', { class: 'numero' }, g.especies.filter((e) => e.heredaFicha).length),
          h('td', null, g.versionFicha ? 'v' + g.versionFicha : h('span', { class: 'chip gris' }, 'Sin ficha')))))))
        : ui.vacio('No hay grupos.'));
    } catch (e) {
      vaciar(zona, ui.error(e));
    }
  }

  function formularioNuevoGrupo() {
    const nombre = h('input', { id: 'grupo-nombre', required: true, placeholder: 'Felinos' });
    const descripcion = h('input', { id: 'grupo-descripcion', placeholder: 'Gatos grandes y pequeños' });
    const resultado = h('div');
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Crear grupo');
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('h2', null, 'Nuevo grupo'),
      h('div', { class: 'fila-campos' },
        h('div', null, h('label', { for: 'grupo-nombre' }, 'Nombre'), nombre, error('nombre')),
        h('div', null, h('label', { for: 'grupo-descripcion' }, 'Descripción ', h('span', { class: 'opcional' }, '(opcional)')), descripcion)),
      h('p', { class: 'ayuda-campo' }, 'Después de crearlo podrá definir su ficha y agregarle especies.'),
      resultado, h('div', { class: 'acciones' }, boton));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      limpiarErrores(form);
      vaciar(resultado);
      try {
        const g = await ui.conBotonOcupado(boton,
          () => api.post('/grupos', { nombre: nombre.value.trim(), descripcion: descripcion.value.trim() || null, atributos: [] }), 'Creando…');
        ui.aviso('Grupo "' + g.nombre + '" creado.');
        location.hash = '#/grupos/' + g.id;
      } catch (err) {
        marcarErrores(form, err.detalles || []);
        vaciar(resultado, ui.error(err));
      }
    });
    return form;
  }

  async function detalle(contenedor, id) {
    contenedor.append(h('p', { class: 'migas' }, h('a', { href: '#/grupos' }, '← Todos los grupos')));
    const zona = h('div', null, ui.cargando());
    contenedor.append(zona);

    const pintar = async () => {
      let grupo;
      try {
        grupo = await api.get('/grupos/' + encodeURIComponent(id));
      } catch (e) {
        vaciar(zona, e.estado === 404 ? ui.mensaje('info', 'No existe el grupo ' + id + '.') : ui.error(e));
        return;
      }
      const ficha = grupo.versionFicha ? await api.get('/grupos/' + id + '/ficha') : null;
      const especies = await catalogo.especies();
      const nombres = new Map(especies.map((e) => [e.id, e]));
      vaciar(zona);
      ui.titulo(zona, grupo.nombre, grupo.descripcion || '');

      const avisarCambio = async (cambio, que) => {
        const n = cambio.especiesActualizadas.length;
        ui.aviso(que + ' Ficha del grupo versión ' + cambio.ficha.version + '. Se copió a ' + n + (n === 1 ? ' especie.' : ' especies.'));
        await pintar();
      };

      zona.append(
        h('section', { class: 'tarjeta' },
          h('h2', null, ficha ? 'Ficha del grupo · versión ' + ficha.version : 'Ficha del grupo'),
          ficha ? fichas.tablaAtributos(ficha.atributos, esAdmin() ? async (nombre) => {
            try {
              await avisarCambio(await api.delete('/grupos/' + id + '/ficha/atributos/' + encodeURIComponent(nombre)), 'Atributo retirado.');
            } catch (e) {
              ui.aviso(e.message, 'error');
            }
          } : null) : ui.vacio('El grupo todavía no tiene ficha.' + (esAdmin() ? ' Agregue el primer atributo abajo.' : ''))),
        esAdmin() ? fichas.formularioAgregar('Agregar un atributo al grupo',
          'Se copia de inmediato, como versión nueva, a cada especie del grupo que hereda su ficha.',
          (atributo) => api.post('/grupos/' + id + '/ficha/atributos', atributo),
          (cambio) => avisarCambio(cambio, 'Atributo agregado.')) : null,
        h('section', { class: 'tarjeta' },
          h('h2', null, 'Especies del grupo'),
          grupo.especies.length ? h('table', null,
            h('thead', null, h('tr', null, h('th', null, 'Especie'), h('th', null, 'Ficha'))),
            h('tbody', null, grupo.especies.map((m) => {
              const e = nombres.get(m.especieId);
              return h('tr', null,
                h('td', null, h('a', { href: '#/especies/' + m.especieId }, h('em', null, e ? e.nombreCientifico : 'Especie ' + m.especieId))),
                h('td', null, m.heredaFicha ? h('span', { class: 'chip' }, 'Hereda la del grupo') : h('span', { class: 'chip gris' }, 'Ficha propia')));
            }))) : ui.vacio('El grupo no tiene especies.'),
          esAdmin() ? formularioAsignar(id, especies, grupo, pintar) : null));
    };
    await pintar();
  }

  function formularioAsignar(grupoId, especies, grupo, alTerminar) {
    const dentro = new Set(grupo.especies.map((m) => m.especieId));
    const candidatas = especies.filter((e) => !dentro.has(e.id));
    const especie = h('select', { id: 'asignar-especie' }, h('option', { value: '' }, 'Escoja la especie…'),
      candidatas.map((e) => h('option', { value: e.id }, e.nombreCientifico)));
    const hereda = h('input', { type: 'checkbox', id: 'asignar-hereda', checked: true });
    const resultado = h('div');
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Agregar al grupo');
    const form = h('form', { novalidate: true },
      h('h3', null, 'Agregar una especie'),
      h('label', { for: 'asignar-especie' }, 'Especie'), especie,
      h('div', { class: 'ayuda-campo' }, 'Si ya está en otro grupo, sale de ese.'),
      h('label', { for: 'asignar-hereda', style: 'font-weight:400' }, hereda, 'Que herede la ficha del grupo (recibe sus atributos ahora y en cada cambio)'),
      resultado, h('div', { class: 'acciones' }, boton));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      vaciar(resultado);
      if (!especie.value) {
        resultado.append(ui.mensaje('error', 'Escoja una especie.'));
        return;
      }
      try {
        await ui.conBotonOcupado(boton, () => api.put('/grupos/' + grupoId + '/especies',
          { especieId: Number(especie.value), heredarFicha: hereda.checked }), 'Agregando…');
        ui.aviso('Especie agregada al grupo.');
        await alTerminar();
      } catch (err) {
        vaciar(resultado, ui.error(err));
      }
    });
    return form;
  }
})();
