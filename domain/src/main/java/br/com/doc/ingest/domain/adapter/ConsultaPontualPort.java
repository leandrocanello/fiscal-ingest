package br.com.doc.ingest.domain.adapter;

import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;

/**
 * Porta de consulta pontual - busca um NSU especifico (usado para gap-fill).
 * O adapter implementa esta porta; o core a consome.
 */
public interface ConsultaPontualPort {

    /**
     * Busca um documento pelo NSU especifico.
     *
     * @param fonte a fonte de origem
     * @param cnpj  o CNPJ destinatario
     * @param nsu   o NSU a buscar
     * @return o resultado com documento (se encontrado) + desfecho normalizado
     */
    BuscarUmResultado buscarUm(FonteId fonte, Cnpj cnpj, Nsu nsu);
}
