/*
 * Especies y fichas taxonómicas (HU-02, RF-02, RNF-01).
 * Todo el personal científico las consulta. Solo el administrador científico crea especies
 * y agrega o retira atributos, sin cambiar código: la ficha es datos.
 *
 * Las piezas para editar atributos (formulario y tabla) las usa también la pantalla de grupos.
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui, sesion, catalogo, formularios } = window.BioHub;
  const { error, limpiarErrores, marcarErrores } = formularios;

  const CIENTIFICOS = ['INVESTIGADOR', 'ADMINISTRADOR_CIENTIFICO', 'DIRECTOR_CIENTIFICO', 'AUDITOR_CIENTIFICO', 'ALTO_RANGO'];
  const esAdmin = () => sesion.tiene('ADMINISTRADOR_CIENTIFICO');

  BioHub.registrar({
    id: 'especies',
    titulo: 'Especies y fichas',
    grupo: 'Fichas taxonómicas',
    roles: CIENTIFICOS,
    resumen: 'Qué datos se registran de cada especie. El administrador los cambia sin tocar código.',
    mostrar(contenedor, [id]) {
      if (id === 'nueva') return nuevaEspecie(contenedor);
      return id ? detalle(contenedor, id) : lista(contenedor);
    }
  });

  /* ---------- Piezas compartidas con grupos ---------- */

  /** Tabla de atributos de una ficha. Si se pasa alRetirar, cada atributo activo tiene su botón. */
  function tablaAtributos(atributos, alRetirar) {
    if (!atributos.length) return ui.vacio('La ficha no tiene atributos.');
    return h('div', { class: 'tabla-envoltura' }, h('table', null,
      h('thead', null, h('tr', null, h('th', null, 'Atributo'), h('th', null, 'Tipo'), h('th', null, 'Detalle'),
        h('th', null, 'Estado'), alRetirar ? h('th') : null)),
      h('tbody', null, atributos.map((a) => h('tr', null,
        h('td', null, h('code', null, a.nombre)),
        h('td', null, (catalogo.TIPOS[a.tipo] || { nombre: a.tipo }).nombre),
        h('td', { class: 'pequeno' }, a.opciones && a.opciones.length ? 'Opciones: ' + a.opciones.join(', ') : a.unidad ? 'Unidad: ' + a.unidad : '—'),
        h('td', null, !a.activo ? h('span', { class: 'chip gris' }, 'Retirado')
          : a.obligatorio ? h('span', { class: 'chip' }, 'Obligatorio') : h('span', { class: 'chip info' }, 'Opcional')),
        alRetirar ? h('td', null, a.activo ? botonRetirar(a.nombre, alRetirar) : null) : null)))));
  }

  /** Retirar pide confirmar en el mismo botón (dos clics), sin ventanas emergentes. */
  function botonRetirar(nombre, alRetirar) {
    const boton = h('button', { class: 'secundario pequeno', type: 'button', 'aria-label': 'Retirar ' + nombre }, 'Retirar');
    let confirmando = false;
    boton.addEventListener('click', async () => {
      if (!confirmando) {
        confirmando = true;
        boton.textContent = '¿Seguro? Clic otra vez';
        boton.className = 'peligro pequeno';
        setTimeout(() => {
          if (confirmando) { confirmando = false; boton.textContent = 'Retirar'; boton.className = 'secundario pequeno'; }
        }, 4000);
        return;
      }
      confirmando = false;
      await ui.conBotonOcupado(boton, () => alRetirar(nombre), 'Retirando…');
    });
    return boton;
  }

  /**
   * Campos para definir un atributo. Devuelve { campos, leer() }.
   * prefijo distingue varios en la misma página (crear especie con varios atributos).
   */
  function camposAtributo(prefijo) {
    const nombre = h('input', { id: prefijo + '-nombre', required: true, placeholder: 'temperatura_agua', autocomplete: 'off' });
    const tipo = h('select', { id: prefijo + '-tipo' },
      Object.entries(catalogo.TIPOS).map(([k, t]) => h('option', { value: k }, t.nombre)));
    const obligatorio = h('input', { type: 'checkbox', id: prefijo + '-obligatorio' });
    const opciones = h('input', { id: prefijo + '-opciones', placeholder: 'macho, hembra, juvenil' });
    const unidad = h('input', { id: prefijo + '-unidad', placeholder: '°C' });
    const ayudaTipo = h('div', { class: 'ayuda-campo' });
    const cajaOpciones = h('div', null, h('label', { for: prefijo + '-opciones' }, 'Opciones (separadas por coma)'), opciones, error('opciones'));
    const cajaUnidad = h('div', null, h('label', { for: prefijo + '-unidad' }, 'Unidad ', h('span', { class: 'opcional' }, '(opcional)')), unidad);
    const actualizar = () => {
      ayudaTipo.textContent = catalogo.TIPOS[tipo.value].ayuda;
      cajaOpciones.hidden = tipo.value !== 'LISTA';
      cajaUnidad.hidden = tipo.value !== 'NUMERO';
    };
    tipo.addEventListener('change', actualizar);
    actualizar();
    return {
      campos: h('div', null,
        h('div', { class: 'fila-campos' },
          h('div', null, h('label', { for: prefijo + '-nombre' }, 'Nombre del atributo'), nombre,
            h('div', { class: 'ayuda-campo' }, 'Minúsculas, números y guion bajo.'), error('nombre')),
          h('div', null, h('label', { for: prefijo + '-tipo' }, 'Tipo de dato'), tipo, ayudaTipo, error('tipo'))),
        h('div', { class: 'fila-campos' }, cajaOpciones, cajaUnidad),
        h('label', { for: prefijo + '-obligatorio', style: 'font-weight:400' }, obligatorio, 'Obligatorio al registrar un hallazgo')),
      leer: () => ({
        nombre: nombre.value.trim(),
        tipo: tipo.value,
        obligatorio: obligatorio.checked,
        opciones: tipo.value === 'LISTA' ? opciones.value.split(',').map((o) => o.trim()).filter(Boolean) : [],
        unidad: tipo.value === 'NUMERO' && unidad.value.trim() ? unidad.value.trim() : null
      }),
      limpiar: () => { nombre.value = ''; opciones.value = ''; unidad.value = ''; obligatorio.checked = false; }
    };
  }

  /** Formulario "agregar atributo". enviar(atributo) hace la llamada; alTerminar(respuesta) refresca. */
  function formularioAgregar(titulo, explicacion, enviar, alTerminar) {
    const atributo = camposAtributo('nuevo-atr');
    const resultado = h('div');
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Agregar atributo');
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('h2', null, titulo), explicacion ? h('p', { class: 'suave pequeno' }, explicacion) : null,
      atributo.campos, resultado, h('div', { class: 'acciones' }, boton));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      limpiarErrores(form);
      vaciar(resultado);
      try {
        const respuesta = await ui.conBotonOcupado(boton, () => enviar(atributo.leer()), 'Agregando…');
        atributo.limpiar();
        await alTerminar(respuesta);
      } catch (err) {
        marcarErrores(form, err.detalles || []);
        vaciar(resultado, ui.error(err));
      }
    });
    return form;
  }

  BioHub.fichas = { tablaAtributos, camposAtributo, formularioAgregar };

  /* ---------- Lista de especies ---------- */

  async function lista(contenedor) {
    ui.titulo(contenedor, 'Especies y fichas', 'Cada especie tiene una ficha: la lista de datos que se registran en campo.');
    if (esAdmin()) contenedor.append(h('div', { class: 'acciones', style: 'margin:0 0 16px' },
      h('a', { class: 'boton primario', href: '#/especies/nueva' }, 'Nueva especie')));
    const [especies, grupos] = await Promise.all([catalogo.especies(), api.get('/grupos')]);
    const grupoDe = mapaGrupos(grupos);
    contenedor.append(especies.length ? h('div', { class: 'tarjeta tabla-envoltura' }, h('table', null,
      h('thead', null, h('tr', null, h('th', null, 'Nombre científico'), h('th', null, 'Nombre común'), h('th', null, 'Grupo'), h('th', null, 'Estado'))),
      h('tbody', null, especies.map((e) => h('tr', { class: 'clic', onClick: () => { location.hash = '#/especies/' + e.id; } },
        h('td', null, h('a', { href: '#/especies/' + e.id }, h('em', null, e.nombreCientifico))),
        h('td', null, e.nombreComun || '—'),
        h('td', null, grupoDe.has(e.id) ? grupoDe.get(e.id).grupo.nombre : '—'),
        h('td', null, e.enPeligro ? h('span', { class: 'chip alerta' }, 'En peligro') : null))))))
      : ui.vacio('No hay especies.'));
  }

  function mapaGrupos(grupos) {
    const m = new Map();
    for (const g of grupos) for (const miembro of g.especies) m.set(miembro.especieId, { grupo: g, hereda: miembro.heredaFicha });
    return m;
  }

  /* ---------- Detalle de una especie ---------- */

  async function detalle(contenedor, id) {
    contenedor.append(h('p', { class: 'migas' }, h('a', { href: '#/especies' }, '← Todas las especies')));
    const especie = (await catalogo.especies()).find((e) => e.id === Number(id));
    if (!especie) {
      contenedor.append(ui.mensaje('info', 'No existe la especie ' + id + '.'));
      return;
    }
    ui.titulo(contenedor, especie.nombreCientifico, (especie.nombreComun || '') + (especie.enPeligro ? ' · en peligro' : ''));
    const zona = h('div');
    contenedor.append(zona);

    const pintar = async () => {
      const [ficha, grupos] = await Promise.all([api.get('/especies/' + id + '/ficha'), api.get('/grupos')]);
      const membresia = mapaGrupos(grupos).get(Number(id));
      vaciar(zona,
        membresia ? ui.mensaje('info', membresia.hereda
          ? 'Pertenece al grupo ' + membresia.grupo.nombre + ' y hereda su ficha: los cambios del grupo llegan aquí. Si cambia esta ficha a mano, la especie deja de heredar.'
          : 'Pertenece al grupo ' + membresia.grupo.nombre + ', con ficha propia (no hereda los cambios del grupo).') : null,
        h('section', { class: 'tarjeta' },
          h('h2', null, 'Ficha vigente · versión ' + ficha.version),
          h('p', { class: 'suave pequeno' }, 'Cada cambio crea una versión nueva. Los hallazgos ya registrados conservan la versión con que se guardaron.'),
          tablaAtributos(ficha.atributos, esAdmin() ? async (nombre) => {
            try {
              await api.delete('/especies/' + id + '/ficha/atributos/' + encodeURIComponent(nombre));
              ui.aviso('Atributo "' + nombre + '" retirado. Los valores ya registrados se conservan.');
              await pintar();
            } catch (e) {
              ui.aviso(e.message, 'error');
            }
          } : null)),
        esAdmin() ? formularioAgregar('Agregar un atributo a esta especie', null,
          (atributo) => api.post('/especies/' + id + '/ficha/atributos', atributo),
          async (ficha2) => { ui.aviso('Atributo agregado. Ficha versión ' + ficha2.version + '.'); await pintar(); }) : null,
        h('p', null, h('a', { href: '#/hallazgos?especie=' + id }, 'Ver los hallazgos de esta especie')));
    };
    await pintar();
  }

  /* ---------- Nueva especie ---------- */

  async function nuevaEspecie(contenedor) {
    contenedor.append(h('p', { class: 'migas' }, h('a', { href: '#/especies' }, '← Todas las especies')));
    ui.titulo(contenedor, 'Nueva especie', 'Defina su ficha inicial. Después se pueden agregar o retirar atributos.');
    if (!esAdmin()) {
      contenedor.append(ui.mensaje('error', 'Solo el administrador científico crea especies.'));
      return;
    }
    const cientifico = h('input', { id: 'nombre-cientifico', required: true, placeholder: 'Panthera onca' });
    const comun = h('input', { id: 'nombre-comun', required: true, placeholder: 'Jaguar' });
    const peligro = h('input', { type: 'checkbox', id: 'en-peligro' });
    const listaAtributos = h('div');
    const atributos = [];
    const agregarFila = () => {
      const a = camposAtributo('atr-' + atributos.length);
      atributos.push(a);
      listaAtributos.append(h('div', { class: 'tarjeta', style: 'box-shadow:none' },
        h('h3', { style: 'margin-top:0' }, 'Atributo ' + atributos.length), a.campos));
    };
    agregarFila();
    const resultado = h('div');
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Crear especie');
    const form = h('form', { novalidate: true },
      h('section', { class: 'tarjeta' },
        h('h2', null, 'La especie'),
        h('div', { class: 'fila-campos' },
          h('div', null, h('label', { for: 'nombre-cientifico' }, 'Nombre científico'), cientifico, error('nombreCientifico')),
          h('div', null, h('label', { for: 'nombre-comun' }, 'Nombre común'), comun,
            h('div', { class: 'ayuda-campo' }, 'Es lo primero que lee un visitante en el portal.'), error('nombreComun'))),
        h('label', { for: 'en-peligro', style: 'font-weight:400' }, peligro, 'En peligro: sus coordenadas exactas solo las ve el personal de alto rango')),
      h('section', { class: 'tarjeta' },
        h('h2', null, 'Ficha inicial'), listaAtributos,
        h('button', { type: 'button', class: 'secundario', onClick: agregarFila }, '+ Otro atributo')),
      resultado,
      h('div', { class: 'acciones' }, boton));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      limpiarErrores(form);
      vaciar(resultado);
      const cuerpo = {
        nombreCientifico: cientifico.value.trim(),
        nombreComun: comun.value.trim(),
        enPeligro: peligro.checked,
        atributos: atributos.map((a) => a.leer()).filter((a) => a.nombre)
      };
      try {
        const ficha = await ui.conBotonOcupado(boton, () => api.post('/especies', cuerpo), 'Creando…');
        catalogo.olvidarEspecies();
        ui.aviso('Especie creada con su ficha versión ' + ficha.version + '.');
        location.hash = '#/especies/' + ficha.especieId;
      } catch (err) {
        marcarErrores(form, err.detalles || []);
        vaciar(resultado, ui.error(err));
      }
    });
    contenedor.append(form);
  }
})();
