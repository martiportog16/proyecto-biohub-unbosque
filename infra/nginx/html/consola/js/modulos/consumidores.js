/*
 * Administrador de la plataforma: consumidores externos de la API /v1 y su límite
 * de peticiones por minuto (HU-10 criterio 4, CU-12, RF-13).
 * El cambio se aplica con el sistema corriendo: el grupo público lo lee de su réplica
 * en la siguiente petición (medido: 143 ms, paquete 09b).
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui } = window.BioHub;

  const MINIMO = 1;
  const MAXIMO = 10000;

  BioHub.registrar({
    id: 'consumidores',
    titulo: 'Consumidores externos',
    grupo: 'Plataforma',
    roles: ['ADMINISTRADOR_PLATAFORMA'],
    resumen: 'Universidades y ONG que usan la API: su límite por minuto y si están activas.',
    mostrar
  });

  async function mostrar(contenedor) {
    ui.titulo(contenedor, 'Consumidores externos',
      'Cada universidad u ONG tiene su clave y su límite de peticiones por minuto. Si lo pasa, recibe 429 sin afectar a las demás.');
    const zona = h('div', null, ui.cargando());
    const zonaClave = h('div', { 'aria-live': 'polite' });
    contenedor.append(zonaClave, zona,
      h('p', { class: 'suave pequeno' }, 'Los cambios se aplican de inmediato, sin reiniciar nada. Las claves no se muestran: BioHub solo guarda su huella.'));
    const pintar = async () => {
      try {
        const consumidores = await api.get('/consumidores');
        vaciar(zona, consumidores.length ? h('div', { class: 'tarjeta tabla-envoltura' }, h('table', null,
          h('thead', null, h('tr', null, h('th', null, 'Consumidor'), h('th', null, 'Estado'),
            h('th', null, 'Límite por minuto'), h('th'))),
          h('tbody', null, consumidores.map((c) => fila(c, pintar)))))
          : ui.vacio('No hay consumidores externos.'));
      } catch (e) {
        vaciar(zona, ui.error(e));
      }
    };
    contenedor.append(formularioAlta(zonaClave, pintar));
    await pintar();
  }

  /* ---------- Alta de un consumidor (RF-12, HU-09) ---------- */

  function formularioAlta(zonaClave, alTerminar) {
    const nombre = h('input', { id: 'nuevo-nombre', required: true, placeholder: 'Universidad Nacional · Grupo de ecología' });
    const limite = h('input', { id: 'nuevo-limite', type: 'number', min: MINIMO, max: MAXIMO, step: 1, value: 100, inputmode: 'numeric' });
    const resultado = h('div');
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Dar de alta');
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('h2', null, 'Nuevo consumidor'),
      h('div', { class: 'fila-campos' },
        h('div', null, h('label', { for: 'nuevo-nombre' }, 'Nombre de la universidad u ONG'), nombre, BioHub.formularios.error('nombre')),
        h('div', null, h('label', { for: 'nuevo-limite' }, 'Límite por minuto'), limite,
          h('div', { class: 'ayuda-campo' }, 'El acordado por defecto es 100 (S-05).'), BioHub.formularios.error('limitePorMinuto'))),
      resultado, h('div', { class: 'acciones' }, boton));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      BioHub.formularios.limpiarErrores(form);
      vaciar(resultado);
      try {
        const creado = await ui.conBotonOcupado(boton, () => api.post('/consumidores', {
          nombre: nombre.value, limitePorMinuto: limite.value === '' ? null : Number(limite.value)
        }), 'Creando…');
        nombre.value = '';
        vaciar(zonaClave, claveUnaVez(creado));
        zonaClave.scrollIntoView({ behavior: 'smooth', block: 'start' });
        await alTerminar();
      } catch (err) {
        BioHub.formularios.marcarErrores(form, err.detalles || []);
        vaciar(resultado, ui.error(err));
      }
    });
    return form;
  }

  /** La API key se muestra UNA sola vez: BioHub guarda solo su huella y no la puede recuperar. */
  function claveUnaVez(creado) {
    const campo = h('input', { id: 'clave-nueva', readonly: true, value: creado.apiKey, style: 'font-family:ui-monospace,Consolas,monospace' });
    const copiar = h('button', { type: 'button', class: 'secundario pequeno' }, 'Copiar');
    copiar.addEventListener('click', async () => {
      campo.select();
      try {
        await navigator.clipboard.writeText(creado.apiKey);
        copiar.textContent = 'Copiada';
      } catch (e) {
        copiar.textContent = 'Selecciónela y cópiela con Ctrl+C';
      }
    });
    return h('section', { class: 'tarjeta' },
      ui.mensaje('aviso', creado.consumidor.nombre + ' quedó dado de alta. Copie su clave ahora y entréguesela por un canal seguro: no se volverá a mostrar.'),
      h('label', { for: 'clave-nueva' }, 'API key (encabezado X-API-Key)'),
      h('div', { class: 'acciones', style: 'margin-top:0' }, h('div', { style: 'flex:1 1 320px' }, campo), copiar),
      h('button', { type: 'button', class: 'secundario', style: 'margin-top:12px', onClick: (e) => e.target.closest('section').remove() },
        'Ya la guardé, ocultarla'));
  }

  function fila(c, alTerminar) {
    const limite = h('input', {
      type: 'number', min: MINIMO, max: MAXIMO, step: 1, value: c.limitePorMinuto, inputmode: 'numeric',
      id: 'limite-' + c.id, 'aria-label': 'Límite por minuto de ' + c.nombre, style: 'max-width:130px'
    });
    const guardar = h('button', { class: 'primario pequeno', type: 'button' }, 'Guardar');
    const estado = h('button', { class: (c.activo ? 'secundario' : 'primario') + ' pequeno', type: 'button' },
      c.activo ? 'Desactivar' : 'Activar');
    const mensaje = h('div', { class: 'ayuda-campo' });

    guardar.addEventListener('click', async () => {
      const n = Number(limite.value);
      if (!Number.isInteger(n) || n < MINIMO || n > MAXIMO) {
        mensaje.className = 'ayuda-campo error-campo';
        mensaje.textContent = 'Debe ser un número entero entre ' + MINIMO + ' y ' + ui.numero(MAXIMO) + '.';
        return;
      }
      await cambiar(guardar, c, { limitePorMinuto: n }, mensaje,
        c.nombre + ' ahora puede hacer ' + ui.numero(n) + ' peticiones por minuto.', alTerminar);
    });

    // Desactivar le quita el acceso de inmediato: se confirma con un segundo clic
    let confirmando = false;
    estado.addEventListener('click', async () => {
      if (c.activo && !confirmando) {
        confirmando = true;
        estado.textContent = '¿Seguro? Clic otra vez';
        estado.className = 'peligro pequeno';
        setTimeout(() => {
          if (confirmando) { confirmando = false; estado.textContent = 'Desactivar'; estado.className = 'secundario pequeno'; }
        }, 4000);
        return;
      }
      confirmando = false;
      await cambiar(estado, c, { activo: !c.activo }, mensaje,
        c.activo ? c.nombre + ' quedó desactivado: sus peticiones se rechazan desde ya (401).' : c.nombre + ' quedó activo de nuevo.', alTerminar);
    });

    return h('tr', null,
      h('td', null, h('strong', null, c.nombre)),
      h('td', null, c.activo ? h('span', { class: 'chip' }, 'Activo') : h('span', { class: 'chip gris' }, 'Desactivado')),
      h('td', null, h('div', { class: 'acciones', style: 'margin:0' }, limite, guardar), mensaje),
      h('td', null, estado));
  }

  async function cambiar(boton, c, cambio, mensaje, textoExito, alTerminar) {
    mensaje.className = 'ayuda-campo';
    mensaje.textContent = '';
    try {
      await ui.conBotonOcupado(boton, () => api.patch('/consumidores/' + c.id, cambio), 'Guardando…');
      ui.aviso(textoExito);
      await alTerminar();
    } catch (e) {
      mensaje.className = 'ayuda-campo error-campo';
      mensaje.textContent = e.message + (e.detalles && e.detalles.length ? ': ' + e.detalles.join('; ') : '');
    }
  }
})();
