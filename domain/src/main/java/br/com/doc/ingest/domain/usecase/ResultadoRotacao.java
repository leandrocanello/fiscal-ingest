package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.cofre.SituacaoTitular;
import br.com.doc.ingest.domain.coleta.ChaveColeta;

import java.util.Map;
import java.util.Objects;

/**
 * Resultado de {@link RotacionarCertificadoUseCase}: a situação do titular e um desfecho por chave.
 * Sem certificado vigente, {@code porChave} fica vazio.
 */
public record ResultadoRotacao(SituacaoTitular situacao, Map<ChaveColeta, DesfechoRotacao> porChave) {

    public ResultadoRotacao {
        Objects.requireNonNull(situacao, "situacao");
        porChave = Map.copyOf(Objects.requireNonNull(porChave, "porChave"));
    }
}
