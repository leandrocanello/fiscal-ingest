package br.com.doc.ingest.worker.processador;

import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;

import java.time.Instant;
import java.util.Objects;

/**
 * Um documento já coletado e gravado no blob store, à espera do processor (§6.3, passo 6).
 * Só metadado, NSU e {@link BlobRef}: nunca o XML (inv. 3).
 *
 * @param fonte          fonte da coleta
 * @param cnpjConsultado CNPJ consultado na fonte (não é a empresa dona: ela sai do conteúdo, inv. 10)
 * @param ambiente       ambiente da coleta
 * @param nsu            NSU do documento
 * @param ledgerNsuId    linha do ledger que registrou o NSU
 * @param blob           onde o bruto foi gravado
 * @param schema         schema informado pela fonte
 * @param recebidoEm     momento da coleta
 */
public record ItemColetado(
        FonteId fonte,
        Cnpj cnpjConsultado,
        Ambiente ambiente,
        Nsu nsu,
        long ledgerNsuId,
        BlobRef blob,
        String schema,
        Instant recebidoEm) {

    public ItemColetado {
        Objects.requireNonNull(fonte, "fonte");
        Objects.requireNonNull(cnpjConsultado, "cnpjConsultado");
        Objects.requireNonNull(ambiente, "ambiente");
        Objects.requireNonNull(nsu, "nsu");
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(recebidoEm, "recebidoEm");
    }
}
