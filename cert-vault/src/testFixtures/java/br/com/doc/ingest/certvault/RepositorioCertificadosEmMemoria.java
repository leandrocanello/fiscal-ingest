package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.NovoCertificado;
import br.com.doc.ingest.domain.cofre.RepositorioCertificados;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** {@link RepositorioCertificados} em memória: {@code (tenant, titular, fingerprint)} é único, como será no Postgres. */
public final class RepositorioCertificadosEmMemoria implements RepositorioCertificados {

    private record Identidade(long tenantId, long titularEmpresaId, String fingerprint) {
    }

    private final Map<Identidade, CertificadoGuardado> registros = new ConcurrentHashMap<>();
    private final AtomicLong proximoId = new AtomicLong();

    @Override
    public CertificadoGuardado registrarSeAusente(NovoCertificado novo) {
        Identidade identidade = new Identidade(novo.tenantId(), novo.titularEmpresaId(), novo.fingerprint());
        return registros.computeIfAbsent(identidade, chave -> new CertificadoGuardado(proximoId.incrementAndGet(),
                novo.tenantId(), novo.titularEmpresaId(), novo.cnpj(), novo.fingerprint(), novo.pfxCifrado(),
                novo.senhaCifrada(), novo.validoDe(), novo.validoAte(), novo.enviadoEm(), novo.enviadoPor()));
    }

    @Override
    public Optional<CertificadoGuardado> buscar(long tenantId, long certificadoId) {
        return registros.values().stream()
                .filter(c -> c.tenantId() == tenantId && c.id() == certificadoId)
                .findFirst();
    }

    @Override
    public List<CertificadoGuardado> doTitular(long tenantId, long titularEmpresaId) {
        return registros.values().stream()
                .filter(c -> c.tenantId() == tenantId && c.titularEmpresaId() == titularEmpresaId)
                .toList();
    }

    @Override
    public List<CertificadoGuardado> vencendoAte(Instant limite) {
        return registros.values().stream()
                .filter(c -> !c.validoAte().isAfter(limite))
                .toList();
    }
}
