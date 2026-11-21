package co.edu.unbosque.biohub.presentacion.comun;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import co.edu.unbosque.biohub.aplicacion.autenticacion.CredencialesInvalidasException;
import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;

/**
 * Traduce las excepciones a códigos HTTP correctos (S16: "no responder siempre 200").
 * 4xx = error de quien llama; 5xx = error nuestro.
 */
@RestControllerAdvice
public class ManejadorErrores {

    public record RespuestaError(String error, List<String> detalles) {
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    ResponseEntity<RespuestaError> credenciales(CredencialesInvalidasException e) {
        return responder(HttpStatus.UNAUTHORIZED, e.getMessage(), List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<RespuestaError> sinPermiso(AccessDeniedException e) {
        return responder(HttpStatus.FORBIDDEN, "No tiene permiso para esta operación", List.of());
    }

    @ExceptionHandler(NoEncontradoException.class)
    ResponseEntity<RespuestaError> noEncontrado(NoEncontradoException e) {
        return responder(HttpStatus.NOT_FOUND, e.getMessage(), List.of());
    }

    @ExceptionHandler(ReglaDeNegocioException.class)
    ResponseEntity<RespuestaError> reglaDeNegocio(ReglaDeNegocioException e) {
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), e.getDetalles());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<RespuestaError> datosInvalidos(MethodArgumentNotValidException e) {
        List<String> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "Datos de la petición inválidos", detalles);
    }

    private ResponseEntity<RespuestaError> responder(HttpStatus estado, String mensaje, List<String> detalles) {
        return ResponseEntity.status(estado).body(new RespuestaError(mensaje, detalles));
    }
}
