package co.edu.unbosque.biohub.persistencia.consultapublica;

import java.util.List;
import java.util.Map;

import co.edu.unbosque.biohub.dominio.consultapublica.PresenciaEnDepartamento;

/**
 * Lecturas del portal público. Solo toca tablas del esquema "public": el usuario
 * app_publico ni siquiera puede entrar al esquema "protegido" (V1).
 */
public interface RepositorioConsultaPublica {

    /** Cuántos hallazgos tiene cada especie (id de especie → total). */
    Map<Long, Long> hallazgosPorEspecie();

    /** En qué departamentos se ha visto la especie, del que más hallazgos tiene al que menos. */
    List<PresenciaEnDepartamento> presencia(long especieId);
}
