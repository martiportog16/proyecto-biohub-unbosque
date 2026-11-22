/*
 * Núcleo de la consola científica: sesión, llamadas a la API, armado seguro de HTML
 * y piezas de interfaz que usan todas las pantallas.
 *
 * Seguridad:
 *  - Nada se arma con innerHTML: todo texto que viene de la API entra con textContent
 *    (función h). Así un nombre de especie como "<script>..." se muestra, no se ejecuta.
 *  - El token se guarda en sessionStorage: se borra al cerrar la pestaña.
 *  - El menú por rol es comodidad. El permiso real lo decide el backend (403).
 */
(function () {
  'use strict';

  const CLAVE_SESION = 'biohub.sesion';
  const API = '/api/cientifico';

  const ROLES = {
    INVESTIGADOR: 'Investigador',
    ADMINISTRADOR_CIENTIFICO: 'Administrador científico',
    DIRECTOR_CIENTIFICO: 'Director científico',
    AUDITOR_CIENTIFICO: 'Auditor científico',
    ALTO_RANGO: 'Personal de alto rango',
    ANALISTA: 'Analista',
    ADMINISTRADOR_PLATAFORMA: 'Administrador de la plataforma'
  };

  /* ---------- Sesión ---------- */

  // Respaldo en memoria si el navegador no deja usar sessionStorage (modo privado estricto)
  let enMemoria = null;

  const sesion = {
    actual() {
      let s = enMemoria;
      try {
        s = JSON.parse(sessionStorage.getItem(CLAVE_SESION)) || enMemoria;
      } catch (e) { /* sin almacenamiento o dato dañado: se usa la memoria */ }
      return s && s.token && s.expiraEn > Date.now() ? s : null;
    },
    guardar(s) {
      enMemoria = s;
      try { sessionStorage.setItem(CLAVE_SESION, JSON.stringify(s)); } catch (e) { /* queda en memoria */ }
    },
    cerrar(motivo) {
      enMemoria = null;
      try { sessionStorage.removeItem(CLAVE_SESION); } catch (e) { /* nada que borrar */ }
      document.dispatchEvent(new CustomEvent('biohub:sesion-cerrada', { detail: motivo || '' }));
    },
    tiene(...roles) {
      const s = sesion.actual();
      return !!s && roles.includes(s.rol);
    }
  };

  /* ---------- API ---------- */

  class ErrorApi extends Error {
    constructor(estado, mensaje, detalles) {
      super(mensaje);
      this.estado = estado;
      this.detalles = detalles || [];
    }
  }

  const MENSAJES_ESTADO = {
    0: 'No hay conexión con BioHub. Revise su red o intente de nuevo en unos segundos.',
    403: 'Su rol no tiene permiso para esta operación.',
    404: 'No se encontró lo que buscaba.',
    409: 'Otra persona hizo un cambio al mismo tiempo. Recargue y vuelva a intentarlo.',
    429: 'Demasiadas peticiones. Espere un momento.',
    500: 'Ocurrió un error en el servidor. Si se repite, avise al equipo técnico.',
    502: 'El servicio no está disponible en este momento. Intente de nuevo en unos segundos.',
    503: 'El servicio no está disponible en este momento. Intente de nuevo en unos segundos.',
    504: 'El servicio tardó demasiado en responder. Intente de nuevo.'
  };

  /**
   * Llama a la API científica. Devuelve el JSON, o null si la respuesta no tiene cuerpo.
   * Si falla, lanza ErrorApi con el mensaje que dio el backend (o uno claro si no dio).
   */
  async function api(metodo, ruta, cuerpo, opciones) {
    const s = sesion.actual();
    const cabeceras = { Accept: 'application/json' };
    if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';
    if (s && !(opciones && opciones.sinSesion)) cabeceras.Authorization = 'Bearer ' + s.token;

    let respuesta;
    try {
      respuesta = await fetch(API + ruta, {
        method: metodo,
        headers: cabeceras,
        body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo)
      });
    } catch (e) {
      throw new ErrorApi(0, MENSAJES_ESTADO[0]);
    }

    const texto = await respuesta.text();
    let datos = null;
    if (texto) {
      try { datos = JSON.parse(texto); } catch (e) { datos = null; }
    }

    if (respuesta.ok) return datos;

    if (respuesta.status === 401 && s && !(opciones && opciones.sinSesion)) {
      sesion.cerrar('Su sesión venció. Entre de nuevo.');
    }
    const mensaje = (datos && datos.error) || MENSAJES_ESTADO[respuesta.status]
      || ('Error ' + respuesta.status);
    throw new ErrorApi(respuesta.status, mensaje, datos && datos.detalles);
  }
  api.get = (ruta) => api('GET', ruta);
  api.post = (ruta, cuerpo) => api('POST', ruta, cuerpo === undefined ? {} : cuerpo);
  api.put = (ruta, cuerpo) => api('PUT', ruta, cuerpo);
  api.patch = (ruta, cuerpo) => api('PATCH', ruta, cuerpo);
  api.delete = (ruta) => api('DELETE', ruta);

  /* ---------- Armado seguro de elementos ---------- */

  /**
   * h('tag', {atributos}, ...hijos). Los hijos pueden ser texto, elementos, listas o null.
   * Atributos: class, style, onClick/onSubmit/... (eventos), hidden/disabled (booleanos), el resto como texto.
   */
  function h(etiqueta, atributos, ...hijos) {
    const el = document.createElement(etiqueta);
    for (const [nombre, valor] of Object.entries(atributos || {})) {
      if (valor === null || valor === undefined || valor === false) continue;
      if (nombre.startsWith('on') && typeof valor === 'function') {
        el.addEventListener(nombre.slice(2).toLowerCase(), valor);
      } else if (nombre === 'class') {
        el.className = valor;
      } else if (nombre === 'style') {
        // Por CSSOM y no como atributo: así lo permite la política de seguridad (CSP) de Nginx
        el.style.cssText = valor;
      } else if (valor === true) {
        el.setAttribute(nombre, '');
      } else if (nombre === 'value') {
        el.value = valor;
      } else {
        el.setAttribute(nombre, String(valor));
      }
    }
    agregar(el, hijos);
    return el;
  }

  function agregar(el, hijos) {
    for (const hijo of hijos) {
      if (hijo === null || hijo === undefined || hijo === false) continue;
      if (Array.isArray(hijo)) agregar(el, hijo);
      else if (hijo instanceof Node) el.appendChild(hijo);
      else el.appendChild(document.createTextNode(String(hijo)));
    }
  }

  function vaciar(el, ...hijos) {
    el.replaceChildren();
    agregar(el, hijos);
    return el;
  }

  /* ---------- Piezas de interfaz ---------- */

  const formatoFecha = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeZone: 'America/Bogota' });
  const formatoFechaHora = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Bogota' });
  const formatoNumero = new Intl.NumberFormat('es-CO');
  const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto',
    'septiembre', 'octubre', 'noviembre', 'diciembre'];

  const ui = {
    cargando(texto) {
      return h('p', { class: 'cargando', role: 'status' }, texto || 'Cargando…');
    },
    vacio(texto) {
      return h('p', { class: 'vacio' }, texto);
    },
    mensaje(tipo, texto, detalles) {
      return h('div', { class: 'mensaje ' + tipo, role: tipo === 'error' ? 'alert' : 'status' },
        texto,
        detalles && detalles.length ? h('ul', null, detalles.map((d) => h('li', null, ui.detalle(d)))) : null);
    },
    /** Muestra un error de la API, con la lista de motivos si el backend la dio (422). */
    error(e) {
      if (e instanceof ErrorApi) return ui.mensaje('error', e.message, e.detalles);
      console.error(e);
      return ui.mensaje('error', 'Algo salió mal en la consola. Recargue la página.');
    },
    /** "valores.conteo: es obligatorio" -> "conteo: es obligatorio" */
    detalle(texto) {
      return String(texto).replace(/^valores\./, '');
    },
    aviso(texto, tipo) {
      const caja = document.getElementById('avisos');
      const el = ui.mensaje(tipo || 'exito', texto);
      caja.appendChild(el);
      setTimeout(() => el.remove(), 5000);
    },
    fecha(valor) {
      if (!valor) return '—';
      // "2026-11-18" es una fecha sin hora: se muestra tal cual, sin pasarla por zonas horarias
      if (/^\d{4}-\d{2}-\d{2}$/.test(valor)) {
        const [a, m, d] = valor.split('-').map(Number);
        return d + ' ' + MESES[m - 1].slice(0, 3) + ' ' + a;
      }
      return formatoFecha.format(new Date(valor));
    },
    fechaHora(valor) {
      return valor ? formatoFechaHora.format(new Date(valor)) : '—';
    },
    /** "2026-10" -> "octubre de 2026" */
    periodo(valor) {
      const [a, m] = String(valor).split('-').map(Number);
      return MESES[m - 1] + ' de ' + a;
    },
    numero(valor, decimales) {
      if (valor === null || valor === undefined) return '—';
      return decimales === undefined ? formatoNumero.format(valor)
        : valor.toLocaleString('es-CO', { minimumFractionDigits: decimales, maximumFractionDigits: decimales });
    },
    /** Botón que se desactiva mientras su acción corre, para no enviar dos veces. */
    async conBotonOcupado(boton, accion, textoOcupado) {
      const original = boton.textContent;
      boton.disabled = true;
      if (textoOcupado) boton.textContent = textoOcupado;
      try {
        return await accion();
      } finally {
        boton.disabled = false;
        boton.textContent = original;
      }
    },
    /** Hoy en Colombia, como "AAAA-MM-DD" (no en UTC: después de las 7 p. m. UTC ya es mañana). */
    hoy() {
      return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date());
    },
    titulo(contenedor, titulo, subtitulo) {
      document.title = titulo + ' · BioHub';
      agregar(contenedor, [h('h1', null, titulo), subtitulo ? h('p', { class: 'subtitulo' }, subtitulo) : null]);
    }
  };

  /* ---------- Registro de pantallas ---------- */

  const modulos = [];

  /**
   * Cada pantalla se registra con:
   *   id        ruta (#/id)
   *   titulo    texto del menú
   *   grupo     sección del menú
   *   roles     quiénes la ven en el menú
   *   resumen   una línea para la pantalla de inicio
   *   mostrar(contenedor, parametros)  dibuja la pantalla
   */
  function registrar(modulo) {
    modulos.push(modulo);
  }

  window.BioHub = { api, ErrorApi, sesion, h, vaciar, ui, registrar, modulos, ROLES };
})();
