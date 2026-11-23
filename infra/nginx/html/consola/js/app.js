/*
 * Arranque de la consola: inicio de sesión, menú según el rol y navegación (#/pantalla).
 * Va de último en index.html: para entonces todas las pantallas ya se registraron.
 */
(function () {
  'use strict';
  const { api, sesion, h, vaciar, ui, modulos, ROLES } = window.BioHub;

  const USUARIOS_DEMO = [
    ['investigador@biohub.co', 'INVESTIGADOR'],
    ['admin.cientifico@biohub.co', 'ADMINISTRADOR_CIENTIFICO'],
    ['director@biohub.co', 'DIRECTOR_CIENTIFICO'],
    ['auditor@biohub.co', 'AUDITOR_CIENTIFICO'],
    ['altorango@biohub.co', 'ALTO_RANGO'],
    ['analista@biohub.co', 'ANALISTA'],
    ['admin.plataforma@biohub.co', 'ADMINISTRADOR_PLATAFORMA']
  ];

  const pantallaLogin = document.getElementById('pantalla-login');
  const pantallaConsola = document.getElementById('pantalla-consola');
  const contenido = document.getElementById('contenido');
  const menu = document.getElementById('menu');
  const botonMenu = document.getElementById('boton-menu');

  /* ---------- Inicio de sesión ---------- */

  function mostrarLogin(motivo) {
    pantallaConsola.hidden = true;
    pantallaLogin.hidden = false;
    document.title = 'Entrar · BioHub';
    vaciar(document.getElementById('login-error'), motivo ? ui.mensaje('aviso', motivo) : null);
    document.getElementById('correo').focus();
  }

  document.getElementById('usuarios-demo').append(...USUARIOS_DEMO.map(([correo, rol]) =>
    h('li', null, h('button', {
      type: 'button',
      onClick: () => {
        document.getElementById('correo').value = correo;
        document.getElementById('clave').value = 'BioHub2026!';
        document.getElementById('clave').focus();
      }
    }, ROLES[rol] + ' · ' + correo))));

  document.getElementById('form-login').addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const correo = document.getElementById('correo').value.trim();
    const clave = document.getElementById('clave').value;
    const caja = document.getElementById('login-error');
    if (!correo || !clave) {
      vaciar(caja, ui.mensaje('error', 'Escriba su correo y su clave.'));
      return;
    }
    const boton = evento.target.querySelector('button[type=submit]');
    try {
      await ui.conBotonOcupado(boton, async () => {
        const s = await api('POST', '/auth/login', { correo, clave }, { sinSesion: true });
        sesion.guardar({
          token: s.token, nombre: s.nombre, rol: s.rol, correo,
          expiraEn: Date.now() + (s.expiraEnSegundos - 30) * 1000
        });
        const yo = await api.get('/usuarios/yo');
        sesion.guardar(Object.assign(sesion.actual(), { id: yo.id }));
      }, 'Entrando…');
      document.getElementById('clave').value = '';
      vaciar(caja);
      entrar();
    } catch (e) {
      vaciar(caja, e.estado === 401
        ? ui.mensaje('error', 'Correo o clave incorrectos.')
        : ui.error(e));
    }
  });

  document.getElementById('boton-salir').addEventListener('click', () => sesion.cerrar());
  document.addEventListener('biohub:sesion-cerrada', (e) => {
    // Se quita la pantalla de la dirección sin disparar la navegación (borraría el aviso)
    history.replaceState(null, '', location.pathname);
    mostrarLogin(e.detail);
  });

  /* ---------- Consola ---------- */

  function disponibles() {
    const s = sesion.actual();
    // "menu" (si la pantalla lo define) dice quién la ve en el menú: los actores de su caso de uso.
    // "roles" dice quién puede abrirla por dirección; el permiso real lo decide el backend.
    return modulos.filter((m) => (m.menu || m.roles || null) === null || (m.menu || m.roles).includes(s.rol));
  }

  function entrar() {
    const s = sesion.actual();
    pantallaLogin.hidden = true;
    pantallaConsola.hidden = false;
    document.getElementById('nombre-usuario').textContent = s.nombre;
    document.getElementById('rol-usuario').textContent = ROLES[s.rol] || s.rol;
    armarMenu();
    if (!location.hash || location.hash === '#') location.hash = '#/inicio';
    else navegar();
  }

  function armarMenu() {
    const grupos = new Map();
    for (const m of disponibles()) {
      if (!grupos.has(m.grupo)) grupos.set(m.grupo, []);
      grupos.get(m.grupo).push(m);
    }
    vaciar(menu, [...grupos].map(([grupo, lista]) => [
      grupo ? h('div', { class: 'grupo' }, grupo) : null,
      lista.map((m) => h('a', { href: '#/' + m.id, 'data-modulo': m.id }, m.titulo))
    ]),
    // En el celular no cabe en la barra de arriba: va al final del menú
    h('a', { href: '#/mi-clave', class: 'solo-celular', 'data-modulo': 'mi-clave' }, 'Cambiar mi clave'));
  }

  botonMenu.addEventListener('click', () => {
    const abierto = menu.classList.toggle('abierto');
    botonMenu.setAttribute('aria-expanded', String(abierto));
  });

  /** #/informes/2026-10 -> pantalla "informes", parámetros ["2026-10"] */
  async function navegar() {
    const s = sesion.actual();
    if (!s) { mostrarLogin(); return; }
    // Lo que va después de "?" son filtros que lee la pantalla misma (#/hallazgos?especie=3)
    const ruta = location.hash.replace(/^#\/?/, '').split('?')[0];
    const [id, ...parametros] = ruta.split('/').map(decodeURIComponent);
    const modulo = modulos.find((m) => m.id === (id || 'inicio'));

    menu.classList.remove('abierto');
    botonMenu.setAttribute('aria-expanded', 'false');
    for (const a of menu.querySelectorAll('a')) {
      if (a.dataset.modulo === (modulo && modulo.id)) a.setAttribute('aria-current', 'page');
      else a.removeAttribute('aria-current');
    }

    vaciar(contenido);
    if (!modulo) {
      ui.titulo(contenido, 'Página no encontrada');
      contenido.append(h('p', null, h('a', { href: '#/inicio' }, 'Volver al inicio')));
      return;
    }
    if (modulo.roles && !modulo.roles.includes(s.rol)) {
      ui.titulo(contenido, modulo.titulo);
      contenido.append(ui.mensaje('error', 'Su rol (' + (ROLES[s.rol] || s.rol) + ') no tiene acceso a esta pantalla.'));
      return;
    }
    try {
      await modulo.mostrar(contenido, parametros);
    } catch (e) {
      contenido.append(ui.error(e));
    }
    contenido.focus({ preventScroll: true });
  }

  window.addEventListener('hashchange', navegar);

  if (sesion.actual()) entrar();
  else mostrarLogin();
})();
