package co.edu.unbosque.biohub.dominio.integracion;

/** Una universidad u ONG que consume la API externa /v1 (RF-12, RF-13). */
public record Consumidor(long id, String nombre, int limitePorMinuto, boolean activo) {
}
