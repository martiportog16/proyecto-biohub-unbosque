/*
 * Usuarios y roles (P-28, S-01).
 *  - Administrador de la plataforma: crea usuarios (con una clave temporal que se muestra una
 *    sola vez), les cambia el rol y los desactiva o reactiva.
 *  - Director científico: solo da o quita el rol de alto rango, como dice el supuesto S-01
 *    ("el Instituto, a través de los directores científicos, define quién es de alto rango").
 * Todo cambio pide un motivo y queda en la bitácora del usuario, que nadie puede editar.
 * El permiso real lo decide el backend: aquí solo se muestran los botones que sirven.
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui, sesion, ROLES } = window.BioHub;
  const { error: errorDe, limpiarErrores, marcarErrores } = BioHub.formularios;

  const CIENTIFICOS = ['INVESTIGADOR', 'ADMINISTRADOR_CIENTIFICO', 'DIRECTOR_CIENTIFICO', 'AUDITOR_CIENTIFICO', 'ALTO_RANGO'];
  const esPlataforma = () => sesion.tiene('ADMINISTRADOR_PLATAFORMA');
  const TIPOS = {
    ALTA: 'Creado', CAMBIO_ROL: 'Cambio de rol', DESACTIVACION: 'Desactivado',
    REACTIVACION: 'Reactivado', CAMBIO_CLAVE: 'Cambió su clave'
  };

  BioHub.registrar({
    id: 'usuarios',
    titulo: 'Usuarios y roles',
    grupo: 'Plataforma',
    roles: ['ADMINISTRADOR_PLATAFORMA', 'DIRECTOR_CIENTIFICO'],
    resumen: 'Quién entra a BioHub y con qué rol. El alto rango lo da un director científico (S-01).',
    mostrar(contenedor, [id]) {
      if (id === 'nuevo') return nuevo(contenedor);
      if (id) return detalle(contenedor, id);
      return lista(contenedor);
    }
  });

  const nombreRol = (rol) => ROLES[rol] || rol;
  const chipEstado = (activo) => activo ? h('span', { class: 'chip' }, 'Activo') : h('span', { class: 'chip gris' }, 'Inactivo');

  /* ---------- Lista ---------- */

  async function lista(contenedor) {
    ui.titulo(contenedor, 'Usuarios y roles', esPlataforma()
      ? 'Cree usuarios, cámbieles el rol o desactívelos. El rol de alto rango lo da un director científico (S-01).'
      : 'Usted decide quién es personal de alto rango (S-01). Los demás cambios los hace el administrador de la plataforma.');
    if (esPlataforma()) {
      contenedor.append(h('div', { class: 'acciones', style: 'margin:0 0 16px' },
        h('a', { class: 'boton primario', href: '#/usuarios/nuevo' }, '+ Nuevo usuario')));
    }
    const zona = h('div', null, ui.cargando());
    contenedor.append(zona);
    try {
      const usuarios = await api.get('/usuarios');
      vaciar(zona, h('div', { class: 'tarjeta tabla-envoltura' }, h('table', null,
        h('thead', null, h('tr', null, h('th', null, 'Nombre'), h('th', null, 'Correo'), h('th', null, 'Rol'), h('th', null, 'Estado'))),
        h('tbody', null, usuarios.map((u) => h('tr', { class: 'clic', onClick: () => { location.hash = '#/usuarios/' + u.id; } },
          h('td', null, h('a', { href: '#/usuarios/' + u.id }, u.nombre)),
          h('td', { class: 'pequeno' }, u.correo),
          h('td', null, u.rol === 'ALTO_RANGO' ? h('strong', null, nombreRol(u.rol)) : nombreRol(u.rol)),
          h('td', null, chipEstado(u.activo))))))));
    } catch (e) {
      vaciar(zona, ui.error(e));
    }
  }

  /* ---------- Nuevo usuario (solo la plataforma) ---------- */

  function nuevo(contenedor) {
    contenedor.append(h('p', { class: 'migas' }, h('a', { href: '#/usuarios' }, '← Todos los usuarios')));
    ui.titulo(contenedor, 'Nuevo usuario', 'Recibe una clave temporal; al entrar, la cambia por una suya.');
    if (!esPlataforma()) {
      contenedor.append(ui.mensaje('error', 'Solo el administrador de la plataforma crea usuarios.'));
      return;
    }
    const nombre = h('input', { id: 'usuario-nombre', required: true, placeholder: 'Carolina Rojas', autocomplete: 'off' });
    const correo = h('input', { id: 'usuario-correo', type: 'email', required: true, placeholder: 'carolina.rojas@biohub.co', autocomplete: 'off' });
    const rol = h('select', { id: 'usuario-rol', required: true }, h('option', { value: '' }, 'Escoja…'),
      Object.keys(ROLES).filter((r) => r !== 'ALTO_RANGO').map((r) => h('option', { value: r }, nombreRol(r))));
    const motivo = h('input', { id: 'usuario-motivo', required: true, placeholder: 'Ingresa al grupo de ecología del Orinoco', autocomplete: 'off' });
    const resultado = h('div', { 'aria-live': 'polite' });
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Crear usuario');
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('div', { class: 'fila-campos' },
        h('div', null, h('label', { for: 'usuario-nombre' }, 'Nombre completo'), nombre, errorDe('nombre')),
        h('div', null, h('label', { for: 'usuario-correo' }, 'Correo de la persona'), correo, errorDe('correo'))),
      h('div', { class: 'fila-campos' },
        h('div', null, h('label', { for: 'usuario-rol' }, 'Rol'), rol,
          h('div', { class: 'ayuda-campo' }, 'El alto rango no está aquí: lo da un director científico después (S-01).'), errorDe('rol')),
        h('div', null, h('label', { for: 'usuario-motivo' }, 'Motivo'), motivo, errorDe('motivo'))),
      resultado, h('div', { class: 'acciones' }, boton));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      limpiarErrores(form);
      vaciar(resultado);
      try {
        const creado = await ui.conBotonOcupado(boton, () => api.post('/usuarios', {
          nombre: nombre.value, correo: correo.value, rol: rol.value, motivo: motivo.value
        }), 'Creando…');
        form.replaceWith(claveUnaVez(creado));
      } catch (err) {
        marcarErrores(form, err.detalles || []);
        vaciar(resultado, ui.error(err));
      }
    });
    contenedor.append(form);
  }

  /** La clave temporal se muestra UNA sola vez: BioHub solo guarda su hash. */
  function claveUnaVez(creado) {
    const campo = h('input', { id: 'clave-temporal', readonly: true, value: creado.claveTemporal, style: 'font-family:ui-monospace,Consolas,monospace' });
    const copiar = h('button', { type: 'button', class: 'secundario pequeno' }, 'Copiar');
    copiar.addEventListener('click', async () => {
      campo.select();
      try {
        await navigator.clipboard.writeText(creado.claveTemporal);
        copiar.textContent = 'Copiada';
      } catch (e) {
        copiar.textContent = 'Selecciónela y cópiela con Ctrl+C';
      }
    });
    const u = creado.usuario;
    return h('section', { class: 'tarjeta' },
      ui.mensaje('aviso', u.nombre + ' quedó creado como ' + nombreRol(u.rol) + '. Entréguele su clave temporal por un canal seguro: no se volverá a mostrar.'),
      h('p', null, 'Correo: ', h('strong', null, u.correo)),
      h('label', { for: 'clave-temporal' }, 'Clave temporal'),
      h('div', { class: 'acciones', style: 'margin-top:0' }, h('div', { style: 'flex:1 1 280px' }, campo), copiar),
      h('p', { class: 'ayuda-campo' }, 'Al entrar, la persona debe cambiarla en "Cambiar clave", arriba a la derecha.'),
      h('p', null, h('a', { href: '#/usuarios/' + u.id }, 'Ver el usuario'), ' · ', h('a', { href: '#/usuarios' }, 'Volver a la lista')));
  }

  /* ---------- Detalle ---------- */

  async function detalle(contenedor, id) {
    contenedor.append(h('p', { class: 'migas' }, h('a', { href: '#/usuarios' }, '← Todos los usuarios')));
    let u;
    try {
      u = await api.get('/usuarios/' + id);
    } catch (e) {
      contenedor.append(e.estado === 404 ? ui.mensaje('info', 'No existe el usuario #' + id + '.') : ui.error(e));
      return;
    }
    ui.titulo(contenedor, u.nombre, u.correo);
    const esYo = Number(u.id) === Number(sesion.actual().id);
    contenedor.append(h('section', { class: 'tarjeta' },
      h('table', null, h('tbody', null,
        h('tr', null, h('th', { style: 'width:35%' }, 'Rol'), h('td', null, h('strong', null, nombreRol(u.rol)))),
        h('tr', null, h('th', null, 'Estado'), h('td', null, chipEstado(u.activo)))))));

    if (esYo) {
      contenedor.append(ui.mensaje('info', 'Es su propio usuario: nadie puede cambiar su propio rol ni desactivarse.'));
    } else {
      const accion = esPlataforma() ? accionesPlataforma(u) : accionesDirector(u);
      if (accion) contenedor.append(accion);
      if (esPlataforma()) contenedor.append(formularioEstado(u));
    }
    contenedor.append(await bitacora(u.id));
  }

  /** Formulario genérico: un control (o ninguno) + motivo + botón; manda el cambio y recarga. */
  function formularioCambio({ titulo, explicacion, control, etiquetaControl, textoBoton, claseBoton, ruta, cuerpo, exito }) {
    const motivo = h('input', { id: 'cambio-motivo-' + ruta, required: true, autocomplete: 'off' });
    const resultado = h('div');
    const boton = h('button', { type: 'submit', class: claseBoton || 'primario' }, textoBoton);
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('h2', null, titulo),
      explicacion ? h('p', { class: 'suave pequeno' }, explicacion) : null,
      h('div', { class: 'fila-campos' },
        control ? h('div', null, h('label', { for: control.id }, etiquetaControl), control, errorDe('rol')) : null,
        h('div', null, h('label', { for: motivo.id }, 'Motivo'), motivo, errorDe('motivo'))),
      resultado, h('div', { class: 'acciones' }, boton));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      limpiarErrores(form);
      vaciar(resultado);
      try {
        await ui.conBotonOcupado(boton, () => api.put(ruta, cuerpo(motivo.value)), 'Guardando…');
        ui.aviso(exito);
        window.dispatchEvent(new HashChangeEvent('hashchange'));
      } catch (err) {
        marcarErrores(form, err.detalles || []);
        vaciar(resultado, ui.error(err));
      }
    });
    return form;
  }

  function accionesPlataforma(u) {
    if (u.rol === 'ALTO_RANGO') {
      return ui.mensaje('info', u.nombre + ' es personal de alto rango: solo un director científico puede quitarle ese rol (S-01).');
    }
    if (!u.activo) return null;
    const rol = h('select', { id: 'rol-nuevo' }, h('option', { value: '' }, 'Escoja…'),
      Object.keys(ROLES).filter((r) => r !== 'ALTO_RANGO' && r !== u.rol).map((r) => h('option', { value: r }, nombreRol(r))));
    return formularioCambio({
      titulo: 'Cambiar el rol', control: rol, etiquetaControl: 'Rol nuevo', textoBoton: 'Cambiar el rol',
      explicacion: 'El cambio vale desde su próximo inicio de sesión.',
      ruta: '/usuarios/' + u.id + '/rol', cuerpo: (motivo) => ({ rol: rol.value, motivo }),
      exito: 'Se cambió el rol de ' + u.nombre + '.'
    });
  }

  function accionesDirector(u) {
    if (!u.activo) return ui.mensaje('info', u.nombre + ' está inactivo.');
    if (u.rol === 'ALTO_RANGO') {
      const rol = h('select', { id: 'rol-nuevo' }, h('option', { value: '' }, 'Escoja…'),
        CIENTIFICOS.filter((r) => r !== 'ALTO_RANGO').map((r) => h('option', { value: r }, nombreRol(r))));
      return formularioCambio({
        titulo: 'Quitar el alto rango', control: rol, etiquetaControl: 'Rol que tendrá', textoBoton: 'Quitar el alto rango',
        claseBoton: 'peligro', explicacion: 'Dejará de ver las coordenadas exactas de las especies en peligro desde ya.',
        ruta: '/usuarios/' + u.id + '/rol', cuerpo: (motivo) => ({ rol: rol.value, motivo }),
        exito: u.nombre + ' ya no es personal de alto rango.'
      });
    }
    return formularioCambio({
      titulo: 'Dar el alto rango',
      explicacion: 'Podrá ver las coordenadas exactas de las especies en peligro (RF-10). Cada consulta suya queda registrada.',
      textoBoton: 'Dar el alto rango',
      ruta: '/usuarios/' + u.id + '/rol', cuerpo: (motivo) => ({ rol: 'ALTO_RANGO', motivo }),
      exito: u.nombre + ' ahora es personal de alto rango.'
    });
  }

  function formularioEstado(u) {
    return formularioCambio({
      titulo: u.activo ? 'Desactivar' : 'Reactivar',
      explicacion: u.activo ? 'No podrá iniciar sesión. El usuario no se borra: su rastro queda.' : 'Podrá volver a iniciar sesión con su clave.',
      textoBoton: u.activo ? 'Desactivar' : 'Reactivar', claseBoton: u.activo ? 'peligro' : 'primario',
      ruta: '/usuarios/' + u.id + '/estado', cuerpo: (motivo) => ({ activo: !u.activo, motivo }),
      exito: u.nombre + (u.activo ? ' quedó desactivado.' : ' quedó activo de nuevo.')
    });
  }

  async function bitacora(id) {
    try {
      const cambios = await api.get('/usuarios/' + id + '/cambios');
      return h('section', { class: 'tarjeta' }, h('h2', null, 'Bitácora'),
        h('p', { class: 'suave pequeno' }, 'Cada cambio queda aquí con su autor y su motivo. Nadie puede editarla ni borrarla.'),
        cambios.length ? h('div', { class: 'tabla-envoltura' }, h('table', null,
          h('thead', null, h('tr', null, h('th', null, 'Fecha'), h('th', null, 'Qué'), h('th', null, 'Quién'), h('th', null, 'Motivo'))),
          h('tbody', null, cambios.slice().reverse().map((c) => h('tr', null,
            h('td', { class: 'pequeno' }, ui.fechaHora(c.realizadoEn)),
            h('td', null, TIPOS[c.tipo] || c.tipo,
              c.tipo === 'CAMBIO_ROL' ? h('div', { class: 'pequeno suave' }, nombreRol(c.rolAnterior) + ' → ' + nombreRol(c.rolNuevo)) : null,
              c.tipo === 'ALTA' ? h('div', { class: 'pequeno suave' }, nombreRol(c.rolNuevo)) : null),
            h('td', null, c.autor),
            h('td', { class: 'pequeno' }, c.motivo))))))
          : ui.vacio('Sin cambios registrados (los usuarios de demostración vienen de la migración).'));
    } catch (e) {
      return ui.error(e);
    }
  }
})();
