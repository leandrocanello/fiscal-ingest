package br.com.doc.ingest.domain.catalogo;

import br.com.doc.ingest.domain.model.ChaveAcesso;
import java.util.Optional;

/** Porta do catálogo: um documento canônico por {@code (tenant, chave)}, com N arquivos e N proveniências (inv. 8). */
public interface Catalogo {

    /** Registra o arquivo e seus vínculos numa única transação. */
    ResultadoRegistro registrar(RegistroArquivo registro);

    /** Ficha do documento do tenant; vazio se a chave não existe nesse tenant. */
    Optional<FichaDocumento> consultar(long tenantId, ChaveAcesso chave);
}
