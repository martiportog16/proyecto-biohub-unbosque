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
    contenedor.append(zona,
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
    await pintar();
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
