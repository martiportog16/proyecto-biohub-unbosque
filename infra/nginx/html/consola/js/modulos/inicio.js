/* Pantalla de inicio: lo que puede hacer cada rol, en tarjetas. */
(function () {
  'use strict';
  const { h, ui, sesion, modulos, ROLES } = window.BioHub;

  const QUE_HACE = {
    INVESTIGADOR: 'Registra hallazgos de campo y asocia cada equipo de campo a su responsable.',
    ADMINISTRADOR_CIENTIFICO: 'Define las fichas taxonómicas de especies y grupos, sin cambiar el código.',
    DIRECTOR_CIENTIFICO: 'Reclasifica hallazgos cuando hay nueva evidencia y decide quién es personal de alto rango.',
    AUDITOR_CIENTIFICO: 'Revisa el historial de cada hallazgo y quién consultó coordenadas exactas.',
    ALTO_RANGO: 'Consulta el mapa con las coordenadas exactas, incluidas las de especies en peligro.',
    ANALISTA: 'Consulta los índices mensuales de presión sobre los ecosistemas.',
    ADMINISTRADOR_PLATAFORMA: 'Administra los usuarios y sus roles, y los consumidores externos de la API.'
  };

  BioHub.registrar({
    id: 'inicio',
    titulo: 'Inicio',
    grupo: '',
    mostrar(contenedor) {
      const s = sesion.actual();
      ui.titulo(contenedor, 'Hola, ' + s.nombre.split(' ')[0], ROLES[s.rol] + '. ' + (QUE_HACE[s.rol] || ''));
      const opciones = modulos.filter((m) => m.id !== 'inicio' && (!(m.menu || m.roles) || (m.menu || m.roles).includes(s.rol)));
      contenedor.append(opciones.length
        ? h('div', { class: 'rejilla' }, opciones.map((m) =>
          h('a', { class: 'tarjeta-enlace', href: '#/' + m.id }, h('strong', null, m.titulo), h('span', null, m.resumen || ''))))
        : ui.vacio('Su rol todavía no tiene pantallas en la consola. Puede usar la API desde Swagger.'));
      contenedor.append(h('p', { class: 'suave pequeno' }, 'Su sesión dura 60 minutos. ',
        h('a', { href: '/swagger-ui.html' }, 'Documentación de la API (Swagger)'), ' · ',
        h('a', { href: '/' }, 'Portal público')));
    }
  });
})();
