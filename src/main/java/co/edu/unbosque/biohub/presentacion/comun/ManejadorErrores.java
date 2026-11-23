package co.edu.unbosque.biohub.presentacion.comun;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import co.edu.unbosque.biohub.aplicacion.autenticacion.CredencialesInvalidasException;
import co.edu.unbosque.biohub.aplicacion.autenticacion.DemasiadosIntentosException;
import co.edu.unbosque.biohub.aplicacion.comun.ConflictoException;
import co.edu.unbosque.biohub.aplicacion.comun.NoEncontradoException;
import co.edu.unbosque.biohub.aplicacion.comun.ReglaDeNegocioException;
import co.edu.unbosque.biohub.aplicacion.comun.SinPermisoException;

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

    @ExceptionHandler(SinPermisoException.class)
    ResponseEntity<RespuestaError> sinPermisoEnEsteCaso(SinPermisoException e) {
        return responder(HttpStatus.FORBIDDEN, e.getMessage(), List.of());
    }

    @ExceptionHandler(NoEncontradoException.class)
    ResponseEntity<RespuestaError> noEncontrado(NoEncontradoException e) {
        return responder(HttpStatus.NOT_FOUND, e.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictoException.class)
    ResponseEntity<RespuestaError> conflicto(ConflictoException e) {
        return responder(HttpStatus.CONFLICT, e.getMessage(), List.of());
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

    @ExceptionHandler(DemasiadosIntentosException.class)
    ResponseEntity<RespuestaError> demasiadosIntentos(DemasiadosIntentosException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getSegundosDeEspera()))
                .body(new RespuestaError(e.getMessage(), List.of()));
    }

    // ---- Errores de forma de la petición: el mismo formato {error, detalles} y en español.
    // (Antes salían con el JSON de Spring, en inglés; lo notó la auditoría.)

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<RespuestaError> cuerpoIlegible(HttpMessageNotReadableException e) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido o tiene un valor con el tipo equivocado",
                List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<RespuestaError> parametroMalo(MethodArgumentTypeMismatchException e) {
        return responder(HttpStatus.BAD_REQUEST, "Datos de la petición inválidos",
                List.of(e.getName() + ": el valor '" + e.getValue() + "' no tiene el formato esperado"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<RespuestaError> parametroFaltante(MissingServletRequestParameterException e) {
        return responder(HttpStatus.BAD_REQUEST, "Datos de la petición inválidos",
                List.of(e.getParameterName() + ": es obligatorio"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<RespuestaError> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "Esta dirección no acepta " + e.getMethod(), List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<RespuestaError> tipoNoSoportado(HttpMediaTypeNotSupportedException e) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Envíe los datos como JSON (Content-Type: application/json)",
                List.of());
    }

    private ResponseEntity<RespuestaError> responder(HttpStatus estado, String mensaje, List<String> detalles) {
        return ResponseEntity.status(estado).body(new RespuestaError(mensaje, detalles));
    }
}
