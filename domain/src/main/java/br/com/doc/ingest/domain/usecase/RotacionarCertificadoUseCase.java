package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.CertificadoVigente;
import br.com.doc.ingest.domain.cofre.RepositorioCertificados;
import br.com.doc.ingest.domain.cofre.SituacaoTitular;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.EstadoColetaRepositorio;
import br.com.doc.ingest.domain.coleta.ResultadoTroca;
import br.com.doc.ingest.domain.coleta.VinculoCertificado;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aponta o {@code estado_coleta} de cada chave do CNPJ do titular para o certificado vigente (DA-53),
 * sem tocar cursor, NSU, versão, lease nem bloqueio (§7.4, inv. 1 e 2). Nunca abre o .pfx (o cofre não é
 * dependência) e nunca apaga o certificado antigo (inv. 6). Só troca certificado nulo ou do mesmo tenant
 * (DA-54). Sem vigente (vencido, futuro, ausente) não troca: {@code CERT_VENCIDO} é da E4-S03.
 *
 * <p>Ao compor o worker (E4-S05), chamar {@code CacheSslContext.invalidar(fingerprintAntigo)} depois de
 * cada {@link DesfechoRotacao.Trocada}.
 */
public final class RotacionarCertificadoUseCase {

    private final RepositorioCertificados certificados;
    private final EstadoColetaRepositorio estados;
    private final Clock clock;

    public RotacionarCertificadoUseCase(RepositorioCertificados certificados, EstadoColetaRepositorio estados,
                                        Clock clock) {
        this.certificados = certificados;
        this.estados = estados;
        this.clock = clock;
    }

    public ResultadoRotacao executar(long tenantId, long titularEmpresaId) {
        SituacaoTitular situacao = CertificadoVigente.escolher(
                certificados.doTitular(tenantId, titularEmpresaId), clock.instant());
        Map<ChaveColeta, DesfechoRotacao> porChave = new LinkedHashMap<>();
        if (situacao instanceof SituacaoTitular.Vigente vigente) {
            CertificadoGuardado novo = vigente.certificado();
            for (VinculoCertificado vinculo : estados.certificadosDoCnpj(novo.cnpj())) {
                porChave.put(vinculo.chave(), rotacionar(vinculo.chave(), vinculo.certificadoId(), novo));
            }
        }
        return new ResultadoRotacao(situacao, porChave);
    }

    /** Tenta; em conflito relê o atual e tenta mais uma vez. */
    private DesfechoRotacao rotacionar(ChaveColeta chave, Long atual, CertificadoGuardado novo) {
        DesfechoRotacao desfecho = tentar(chave, atual, novo);
        if (desfecho instanceof DesfechoRotacao.Conflito conflito) {
            return tentar(chave, conflito.atual(), novo);
        }
        return desfecho;
    }

    private DesfechoRotacao tentar(ChaveColeta chave, Long atual, CertificadoGuardado novo) {
        if (atual != null) {
            if (atual == novo.id()) {
                return new DesfechoRotacao.SemMudanca();
            }
            if (certificados.buscar(novo.tenantId(), atual).isEmpty()) {
                return new DesfechoRotacao.OutroTenant(atual);
            }
        }
        return switch (estados.trocarCertificado(chave, atual, novo.id())) {
            case ResultadoTroca.Trocado t -> new DesfechoRotacao.Trocada();
            case ResultadoTroca.SemMudanca s -> new DesfechoRotacao.SemMudanca();
            case ResultadoTroca.Conflito c -> new DesfechoRotacao.Conflito(c.atual());
            case ResultadoTroca.NaoEncontrada n -> new DesfechoRotacao.NaoEncontrada();
        };
    }
}
