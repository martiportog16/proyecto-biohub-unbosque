/*
 * Investigador: registrar un hallazgo (HU-01, CU-01, RF-01).
 * El formulario se arma con la ficha VIGENTE de la especie: si el administrador agrega
 * un atributo, aparece aquí sin cambiar este código (RNF-01).
 * La validación de verdad la hace el backend; aquí solo se ayuda a llenar bien.
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui, catalogo, formularios } = window.BioHub;
  const { error, mostrarError, limpiarErrores, marcarErrores, nombreBonito } = formularios;

  BioHub.registrar({
    id: 'registrar',
    titulo: 'Registrar hallazgo',
    grupo: 'Registro científico',
    roles: ['INVESTIGADOR'],
    resumen: 'Formulario de campo que se adapta a la ficha de cada especie.',
    mostrar
  });

  async function mostrar(contenedor) {
    ui.titulo(contenedor, 'Registrar hallazgo',
      'Los campos de "Datos de la especie" cambian según la ficha de la especie que escoja.');
    const [especies, departamentos] = await Promise.all([catalogo.especies(), catalogo.departamentos()]);

    const selectEspecie = h('select', { id: 'especie', required: true },
      h('option', { value: '' }, 'Escoja la especie…'),
      especies.map((e) => h('option', { value: e.id },
        e.nombreCientifico + (e.nombreComun ? ' · ' + e.nombreComun : '') + (e.enPeligro ? ' · en peligro' : ''))));
    const fecha = h('input', { type: 'date', id: 'fecha', required: true, max: ui.hoy(), value: ui.hoy() });
    const departamento = h('select', { id: 'departamento', required: true },
      h('option', { value: '' }, 'Escoja el departamento…'),
      departamentos.map((d) => h('option', { value: d }, d)));
    const latitud = h('input', { type: 'number', id: 'latitud', step: 'any', min: -90, max: 90, required: true, inputmode: 'decimal' });
    const longitud = h('input', { type: 'number', id: 'longitud', step: 'any', min: -180, max: 180, required: true, inputmode: 'decimal' });
    const precision = h('input', { type: 'number', id: 'precision', min: 0, step: 1, inputmode: 'numeric' });
    const zonaFicha = h('div', null, h('p', { class: 'suave' }, 'Escoja una especie para ver sus campos.'));
    const resultado = h('div', { 'aria-live': 'polite' });
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Registrar hallazgo');
    let ficha = null;

    selectEspecie.addEventListener('change', async () => {
      ficha = null;
      vaciar(resultado);
      if (!selectEspecie.value) {
        vaciar(zonaFicha, h('p', { class: 'suave' }, 'Escoja una especie para ver sus campos.'));
        return;
      }
      vaciar(zonaFicha, ui.cargando('Cargando la ficha…'));
      try {
        ficha = await api.get('/especies/' + selectEspecie.value + '/ficha');
        vaciar(zonaFicha, camposDeFicha(ficha));
      } catch (e) {
        vaciar(zonaFicha, ui.error(e));
      }
    });

    const botonUbicacion = h('button', { type: 'button', class: 'secundario pequeno' }, 'Usar mi ubicación');
    botonUbicacion.addEventListener('click', () => {
      if (!navigator.geolocation) {
        ui.aviso('Este navegador no da la ubicación. Escríbala a mano.', 'aviso');
        return;
      }
      botonUbicacion.disabled = true;
      navigator.geolocation.getCurrentPosition((pos) => {
        latitud.value = pos.coords.latitude.toFixed(6);
        longitud.value = pos.coords.longitude.toFixed(6);
        precision.value = Math.round(pos.coords.accuracy);
        botonUbicacion.disabled = false;
      }, () => {
        ui.aviso('No se pudo leer la ubicación. Escríbala a mano.', 'aviso');
        botonUbicacion.disabled = false;
      }, { enableHighAccuracy: true, timeout: 15000 });
    });

    const form = h('form', { novalidate: true },
      h('section', { class: 'tarjeta' },
        h('h2', null, 'Qué y cuándo'),
        h('label', { for: 'especie' }, 'Especie'), selectEspecie, error('especieId'),
        h('div', { class: 'fila-campos' },
          h('div', null, h('label', { for: 'fecha' }, 'Fecha de observación'), fecha, error('fechaObservacion')),
          h('div', null, h('label', { for: 'departamento' }, 'Departamento'), departamento, error('departamento')))),
      h('section', { class: 'tarjeta' },
        h('h2', null, 'Datos de la especie'), zonaFicha),
      h('section', { class: 'tarjeta' },
        h('h2', null, 'Ubicación exacta'),
        h('p', { class: 'suave pequeno' }, 'Se guarda en una zona protegida de la base. No se muestra en el portal ni en la API externa; solo la consulta el personal autorizado y cada consulta queda registrada.'),
        h('div', { class: 'fila-campos' },
          h('div', null, h('label', { for: 'latitud' }, 'Latitud'), latitud, error('coordenada.latitud')),
          h('div', null, h('label', { for: 'longitud' }, 'Longitud'), longitud, error('coordenada.longitud')),
          h('div', null, h('label', { for: 'precision' }, 'Precisión en metros ', h('span', { class: 'opcional' }, '(opcional)')), precision, error('coordenada.precisionMetros'))),
        h('div', { class: 'acciones' }, botonUbicacion),
        error('coordenada')),
      resultado,
      h('div', { class: 'acciones' }, boton));

    form.addEventListener('submit', async (evento) => {
      evento.preventDefault();
      limpiarErrores(form);
      vaciar(resultado);
      const valores = ficha ? leerValores(ficha, zonaFicha) : {};
      if (valores === null) {
        resultado.append(ui.mensaje('error', 'Revise los campos marcados.'));
        return;
      }
      const cuerpo = {
        especieId: selectEspecie.value ? Number(selectEspecie.value) : null,
        fechaObservacion: fecha.value || null,
        departamento: departamento.value || null,
        valores,
        coordenada: {
          latitud: latitud.value === '' ? null : Number(latitud.value),
          longitud: longitud.value === '' ? null : Number(longitud.value),
          precisionMetros: precision.value === '' ? null : Number(precision.value)
        }
      };
      try {
        const hallazgo = await ui.conBotonOcupado(boton, () => api.post('/hallazgos', cuerpo), 'Registrando…');
        catalogo.olvidarEspecies();
        vaciar(resultado, ui.mensaje('exito', 'Hallazgo #' + hallazgo.id + ' registrado con la versión ' + hallazgo.versionFicha + ' de la ficha.'),
          h('p', null, h('a', { href: '#/hallazgos/' + hallazgo.id }, 'Ver el hallazgo'), ' · ',
            h('a', { href: '#/registrar', onClick: () => setTimeout(() => location.reload(), 0) }, 'Registrar otro')));
        boton.disabled = true;
        resultado.scrollIntoView({ behavior: 'smooth', block: 'center' });
      } catch (e) {
        marcarErrores(form, e.detalles || []);
        vaciar(resultado, ui.error(e));
      }
    });

    contenedor.append(form);
  }

  /* ---------- Campos que salen de la ficha ---------- */

  function camposDeFicha(ficha) {
    const activos = ficha.atributos.filter((a) => a.activo);
    if (!activos.length) return ui.vacio('Esta especie no tiene atributos en su ficha.');
    return [
      h('p', { class: 'suave pequeno' }, 'Ficha versión ' + ficha.version + '. El hallazgo queda amarrado a esta versión.'),
      activos.map(campoDeAtributo)
    ];
  }

  function campoDeAtributo(a) {
    const id = 'atr-' + a.nombre;
    const comun = { id, 'data-atributo': a.nombre, 'data-tipo': a.tipo, required: a.obligatorio };
    let control;
    switch (a.tipo) {
      case 'NUMERO':
        control = h('input', Object.assign({ type: 'number', step: 'any', inputmode: 'decimal' }, comun));
        break;
      case 'FECHA':
        control = h('input', Object.assign({ type: 'date', max: ui.hoy() }, comun));
        break;
      case 'LISTA':
        control = h('select', comun, h('option', { value: '' }, a.obligatorio ? 'Escoja…' : '(sin dato)'),
          (a.opciones || []).map((o) => h('option', { value: o }, o)));
        break;
      case 'RUTA_GPS':
        control = h('textarea', Object.assign({ placeholder: '4.6097, -74.0817\n4.6101, -74.0820' }, comun));
        break;
      case 'ARCHIVO':
        control = h('input', Object.assign({ type: 'text', placeholder: 'grabaciones/canto-0412.wav' }, comun));
        break;
      default:
        control = h('input', Object.assign({ type: 'text' }, comun));
    }
    const ayuda = a.tipo === 'RUTA_GPS' ? 'Un punto por línea: latitud, longitud. Mínimo 2 puntos.'
      : a.tipo === 'ARCHIVO' ? 'Nombre o enlace del archivo, con su extensión (.wav, .jpg…).'
        : a.unidad ? 'En ' + a.unidad + '.' : null;
    return h('div', null,
      h('label', { for: id }, nombreBonito(a.nombre), a.unidad ? ' (' + a.unidad + ')' : '',
        a.obligatorio ? null : h('span', { class: 'opcional' }, ' (opcional)')),
      control,
      ayuda ? h('div', { class: 'ayuda-campo' }, ayuda) : null,
      error(a.nombre));
  }

  /** Lee los campos de la ficha. Devuelve null si alguno está mal escrito (por ejemplo, la ruta GPS). */
  function leerValores(ficha, zona) {
    const valores = {};
    let bien = true;
    for (const control of zona.querySelectorAll('[data-atributo]')) {
      const nombre = control.dataset.atributo;
      const texto = control.value.trim();
      if (texto === '') continue;   // vacío = no se envía; si era obligatorio, lo dice el backend
      switch (control.dataset.tipo) {
        case 'NUMERO':
          valores[nombre] = Number(texto);
          break;
        case 'RUTA_GPS': {
          const puntos = texto.split('\n').map((l) => l.trim()).filter(Boolean).map((l) => l.split(/[,;\s]+/).map(Number));
          if (puntos.some((p) => p.length !== 2 || p.some(Number.isNaN))) {
            mostrarError(zona, nombre, 'Cada línea debe tener latitud y longitud, por ejemplo: 4.6097, -74.0817');
            bien = false;
          }
          valores[nombre] = puntos.map(([lat, lon]) => ({ lat, lon }));
          break;
        }
        default:
          valores[nombre] = texto;
      }
    }
    return bien ? valores : null;
  }

})();
