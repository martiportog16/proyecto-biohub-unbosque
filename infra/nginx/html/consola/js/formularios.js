/*
 * Ayudas para formularios: mostrar cada error del backend (422) junto a su campo.
 * El backend responde "campo: motivo"; cada campo tiene debajo una caja con data-error="campo".
 */
(function () {
  'use strict';
  const { h } = window.BioHub;

  /* ---------- Errores junto a cada campo ---------- */

  function error(campo) {
    return h('div', { class: 'ayuda-campo error-campo', 'data-error': campo, hidden: true, role: 'alert' });
  }

  function mostrarError(raiz, campo, texto) {
    const caja = raiz.querySelector('[data-error="' + CSS.escape(campo) + '"]');
    if (!caja) return false;
    caja.textContent = texto;
    caja.hidden = false;
    const control = caja.parentElement.querySelector('input, select, textarea');
    if (control) control.setAttribute('aria-invalid', 'true');
    return true;
  }

  function limpiarErrores(raiz) {
    for (const caja of raiz.querySelectorAll('[data-error]')) caja.hidden = true;
    for (const c of raiz.querySelectorAll('[aria-invalid]')) c.removeAttribute('aria-invalid');
  }

  /** "conteo: es obligatorio" se muestra debajo del campo con data-error="conteo". */
  function marcarErrores(raiz, detalles) {
    for (const d of detalles) {
      const separador = d.indexOf(': ');
      if (separador > 0) mostrarError(raiz, d.slice(0, separador), d.slice(separador + 2));
    }
  }

  /** "numero_individuos" -> "Numero individuos" */
  function nombreBonito(nombre) {
    const t = nombre.replace(/_/g, ' ');
    return t.charAt(0).toUpperCase() + t.slice(1);
  }

  window.BioHub.formularios = { error, mostrarError, limpiarErrores, marcarErrores, nombreBonito };
})();
