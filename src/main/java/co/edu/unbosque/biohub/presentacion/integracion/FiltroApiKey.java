package co.edu.unbosque.biohub.presentacion.integracion;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import co.edu.unbosque.biohub.aplicacion.integracion.ServicioLimitePeticiones;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioLimitePeticiones.Decision;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioLimitePeticiones.Excedido;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioLimitePeticiones.NoAutorizado;
import co.edu.unbosque.biohub.aplicacion.integracion.ServicioLimitePeticiones.Permitido;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Protege la API externa /api/v1/** (RF-13, HU-09).
 *
 * Cada petición trae su API key en el encabezado X-API-Key. Antes de llegar al
 * controlador se verifica la llave y el límite por minuto del consumidor:
 *   401 -> falta la llave o no es válida
 *   429 -> se pasó del límite; Retry-After dice cuántos segundos esperar
 */
@Component
@ConditionalOnWebApplication
public class FiltroApiKey extends OncePerRequestFilter {

    public static final String ENCABEZADO = "X-API-Key";
    public static final String ATRIBUTO_CONSUMIDOR = "biohub.consumidor";

    private final ServicioLimitePeticiones limite;

    public FiltroApiKey(ServicioLimitePeticiones limite) {
        this.limite = limite;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return !peticion.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        Decision decision = limite.verificar(peticion.getHeader(ENCABEZADO));

        switch (decision) {
            case NoAutorizado n -> error(respuesta, HttpStatus.UNAUTHORIZED,
                    "Falta la API key en el encabezado X-API-Key o no es válida");
            case Excedido e -> {
                respuesta.setHeader("Retry-After", String.valueOf(e.segundosParaReintentar()));
                respuesta.setHeader("X-RateLimit-Limit", String.valueOf(e.limite()));
                respuesta.setHeader("X-RateLimit-Remaining", "0");
                error(respuesta, HttpStatus.TOO_MANY_REQUESTS,
                        "Superó su límite de " + e.limite() + " peticiones por minuto");
            }
            case Permitido p -> {
                respuesta.setHeader("X-RateLimit-Limit", String.valueOf(p.limite()));
                respuesta.setHeader("X-RateLimit-Remaining", String.valueOf(p.restantes()));
                peticion.setAttribute(ATRIBUTO_CONSUMIDOR, p);
                cadena.doFilter(peticion, respuesta);
            }
        }
    }

    private void error(HttpServletResponse respuesta, HttpStatus estado, String mensaje) throws IOException {
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.getWriter().write("{\"error\":\"" + mensaje + "\",\"detalles\":[]}");
    }
}
