package br.com.doc.ingest.domain.catalogo;

import br.com.doc.ingest.domain.model.ChaveAcesso;
import java.util.Optional;

/** Porta do catálogo: um documento canônico por {@code (tenant, chave)}, com N arquivos e N proveniências (inv. 8). */
public interface Catalogo {

    /**
     * Registra o arquivo e seus vínculos numa única transação.
     *
     * @throws FalhaCatalogoException se a operação falhar; nada fica gravado
     */
    ResultadoRegistro registrar(RegistroArquivo registro);

    /**
     * Ficha do documento do tenant; vazio se a chave não existe nesse tenant.
     *
     * @throws FalhaCatalogoException se a consulta falhar
     */
    Optional<FichaDocumento> consultar(long tenantId, ChaveAcesso chave);
}
