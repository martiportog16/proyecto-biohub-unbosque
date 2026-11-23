/*
 * Cambiar mi clave (P-28). Cualquier usuario, con su clave actual.
 * Sirve sobre todo para cambiar la clave temporal que se recibe al ser creado.
 * No aparece en el menú: se abre con "Cambiar clave", arriba a la derecha.
 */
(function () {
  'use strict';
  const { api, h, vaciar, ui } = window.BioHub;
  const { error: errorDe, limpiarErrores, marcarErrores } = BioHub.formularios;

  BioHub.registrar({
    id: 'mi-clave',
    titulo: 'Cambiar mi clave',
    grupo: '',
    menu: [],          // no va en el menú; todos los roles pueden abrirla
    mostrar
  });

  function mostrar(contenedor) {
    ui.titulo(contenedor, 'Cambiar mi clave', 'Mínimo 10 caracteres. Use una frase que recuerde y que no use en otros sitios.');
    const actual = h('input', { id: 'clave-actual', type: 'password', required: true, autocomplete: 'current-password' });
    const nueva = h('input', { id: 'clave-nueva', type: 'password', required: true, minlength: 10, autocomplete: 'new-password' });
    const repetir = h('input', { id: 'clave-repetir', type: 'password', required: true, autocomplete: 'new-password' });
    const resultado = h('div', { 'aria-live': 'polite' });
    const boton = h('button', { type: 'submit', class: 'primario' }, 'Cambiar la clave');
    const form = h('form', { class: 'tarjeta', novalidate: true },
      h('div', null, h('label', { for: 'clave-actual' }, 'Clave actual'), actual, errorDe('claveActual')),
      h('div', null, h('label', { for: 'clave-nueva' }, 'Clave nueva'), nueva, errorDe('claveNueva')),
      h('div', null, h('label', { for: 'clave-repetir' }, 'Repita la clave nueva'), repetir, errorDe('repetir')),
      resultado, h('div', { class: 'acciones' }, boton));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      limpiarErrores(form);
      vaciar(resultado);
      if (nueva.value !== repetir.value) {
        BioHub.formularios.mostrarError(form, 'repetir', 'No coincide con la clave nueva.');
        return;
      }
      try {
        await ui.conBotonOcupado(boton, () => api.put('/usuarios/yo/clave', { claveActual: actual.value, claveNueva: nueva.value }), 'Guardando…');
        form.reset();
        vaciar(resultado, ui.mensaje('exito', 'Listo: su clave cambió. Úsela la próxima vez que entre.'));
      } catch (err) {
        marcarErrores(form, err.detalles || []);
        vaciar(resultado, ui.error(err));
      }
    });
    contenedor.append(form);
  }
})();
