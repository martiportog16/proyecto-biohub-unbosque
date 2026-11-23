/*
 * Auditor: registro de accesos a coordenadas exactas (RNF-06, PR-07, PR-14).
 * Cada vez que alguien pide una coordenada queda una fila: quién, con qué rol, de qué
 * especie, si se permitió y por qué. Esta pantalla solo lee; la tabla no se puede
 * editar ni borrar desde la aplicación.
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui, catalogo, ROLES } = window.BioHub;

  const MOTIVOS = {
    ALTO_RANGO_ESPECIE_EN_PELIGRO: 'Alto rango y especie en peligro',
    CIENTIFICO_ESPECIE_FUERA_DE_PELIGRO: 'Rol científico y especie fuera de peligro',
    ESPECIE_EN_PELIGRO_EXIGE_ALTO_RANGO: 'Especie en peligro: exige alto rango',
    ROL_NO_CIENTIFICO: 'Rol no científico',
    USUARIO_INACTIVO: 'Usuario inactivo'
  };

  BioHub.registrar({
    id: 'accesos',
    titulo: 'Accesos a coordenadas',
    grupo: 'Auditoría',
    roles: ['AUDITOR_CIENTIFICO'],
    resumen: 'Quién pidió coordenadas exactas, de qué especie y si se le permitió.',
    mostrar
  });

  async function mostrar(contenedor) {
    ui.titulo(contenedor, 'Accesos a coordenadas',
      'Cada consulta de una ubicación exacta, permitida o negada. Las coordenadas no aparecen aquí: solo quién las pidió.');
    const consulta = new URLSearchParams(location.hash.split('?')[1] || '');
    const soloNegados = h('input', { type: 'checkbox', id: 'solo-negados', checked: consulta.get('negados') === '1' });
    const limite = h('select', { id: 'limite' }, [50, 200, 500].map((n) => h('option', { value: n }, 'Últimos ' + n)));
    limite.value = consulta.get('limite') || '50';
    const ir = () => {
      const p = new URLSearchParams();
      if (soloNegados.checked) p.set('negados', '1');
      if (limite.value !== '50') p.set('limite', limite.value);
      location.hash = '#/accesos' + (p.toString() ? '?' + p : '');
    };
    soloNegados.addEventListener('change', ir);
    limite.addEventListener('change', ir);

    const zona = h('div', null, ui.cargando());
    contenedor.append(h('div', { class: 'tarjeta acciones', style: 'margin-top:0' },
      h('label', { for: 'solo-negados', style: 'margin:0;font-weight:400' }, soloNegados, 'Solo los negados'),
      h('div', { style: 'margin-left:auto;min-width:160px' }, h('label', { for: 'limite', class: 'oculto-visual' }, 'Cuántos'), limite)), zona);

    try {
      const [accesos, especies] = await Promise.all([
        api.get('/coordenadas/accesos?soloNegados=' + soloNegados.checked + '&limite=' + limite.value), catalogo.especies()]);
      const nombres = new Map(especies.map((e) => [e.id, e.nombreCientifico]));
      const negados = accesos.filter((a) => !a.permitido).length;
      const enPeligro = accesos.filter((a) => a.especieEnPeligro && a.permitido).length;
      vaciar(zona,
        h('div', { class: 'cifras' },
          cifra(accesos.length, 'consultas en la lista'),
          cifra(negados, 'negadas'),
          cifra(enPeligro, 'permitidas de especies en peligro')),
        accesos.length ? h('div', { class: 'tarjeta tabla-envoltura' }, h('table', null,
          h('thead', null, h('tr', null, h('th', null, 'Cuándo'), h('th', null, 'Usuario'), h('th', null, 'Rol'),
            h('th', null, 'Especie'), h('th', null, 'Qué pidió'), h('th', null, 'Resultado'), h('th', null, 'Motivo'))),
          h('tbody', null, accesos.map((a) => h('tr', null,
            h('td', null, ui.fechaHora(a.ocurridoEn)),
            h('td', null, '#' + a.usuarioId),
            h('td', null, ROLES[a.rol] || a.rol),
            h('td', null, h('em', null, nombres.get(a.especieId) || 'Especie ' + a.especieId),
              a.especieEnPeligro ? [' ', h('span', { class: 'chip alerta' }, 'en peligro')] : null),
            h('td', null, a.hallazgoId ? h('a', { href: '#/hallazgos/' + a.hallazgoId }, 'Hallazgo #' + a.hallazgoId) : 'Mapa de la especie'),
            h('td', null, a.permitido ? h('span', { class: 'chip' }, 'Permitido') : h('span', { class: 'chip alerta' }, 'Negado')),
            h('td', { class: 'pequeno' }, MOTIVOS[a.motivo] || a.motivo))))))
          : ui.vacio(soloNegados.checked ? 'No hay consultas negadas.' : 'Nadie ha consultado coordenadas todavía.'));
    } catch (e) {
      vaciar(zona, ui.error(e));
    }
  }

  function cifra(valor, rotulo) {
    return h('div', { class: 'cifra' }, h('div', { class: 'valor' }, ui.numero(valor)), h('div', { class: 'rotulo' }, rotulo));
  }
})();
