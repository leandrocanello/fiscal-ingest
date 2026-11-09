package br.com.doc.ingest.domain.cofre;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Porta dos metadados de certificados guardados. Nunca recebe pfx nem senha em claro.
 */
public interface RepositorioCertificados {

    /**
     * Registra o certificado, ou devolve o registro atual se {@code (tenant, titular, fingerprint)}
     * já existir (reenvio do mesmo .pfx). Seguro sob concorrência: todos recebem o mesmo id.
     */
    CertificadoGuardado registrarSeAusente(NovoCertificado novo);

    /** Devolve vazio se o id não existe ou pertence a outro tenant. */
    Optional<CertificadoGuardado> buscar(long tenantId, long certificadoId);

    /** Todos os certificados do titular no tenant, em qualquer ordem; vazio se não houver. */
    List<CertificadoGuardado> doTitular(long tenantId, long titularEmpresaId);

    /** Certificados (de todos os tenants) com {@code validoAte <= limite}, inclusive os já vencidos. */
    List<CertificadoGuardado> vencendoAte(Instant limite);
}
