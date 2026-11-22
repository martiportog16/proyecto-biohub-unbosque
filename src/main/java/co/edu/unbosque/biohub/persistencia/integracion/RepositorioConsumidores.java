package co.edu.unbosque.biohub.persistencia.integracion;

import java.util.Optional;

import co.edu.unbosque.biohub.dominio.integracion.Consumidor;

/** Consumidores externos y sus límites (P-31: se guardan en la base). */
public interface RepositorioConsumidores {

    Optional<Consumidor> buscarPorHuella(String huellaApiKey);
}
