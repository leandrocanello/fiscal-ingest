package br.com.doc.ingest.domain.adapter;

import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;

/**
 * A porta unica do adapter - todo adapter de fonte fiscal implementa esta interface.
 *
 * <p>O core consome esta porta; cada adapter (NF-e, CT-e, fake, etc.) a implementa.
 * O adapter TRADUZ o dialeto da origem para o vocabulario unico de {@link Desfecho}.
 *
 * <p>Esta interface estende {@link ConsultaPontualPort} para que o core possa
 * fazer gap-recovery via buscarUm sem acoplar a uma implementacao especifica.
 */
public interface SourceAdapter extends ConsultaPontualPort {

    /**
     * @return o descritor do adapter (id da fonte, capacidades, etc.)
     */
    AdapterDescriptor descritor();

    /**
     * Autentica na origem (ex: carrega certificado mTLS).
     */
    void autenticar(Credencial credencial);

    /**
     * Busca lote de documentos a partir do cursor.
     * O adapter persiste o XML no object store e devolve so metadado + BlobRef.
     *
     * @param contexto fonte + cnpj
     * @param cursor   cursor opaco (o core so guarda e devolve)
     * @return resultado com documentos, proximo cursor e desfecho normalizado
     */
    BuscarLoteResultado buscarLote(ContextoConsulta contexto, Cursor cursor);

    /**
     * Busca um documento pelo NSU especifico (consulta pontual / gap-fill).
     * Herdado de {@link ConsultaPontualPort#buscarUm(FonteId, Cnpj, Nsu)}.
     */
    @Override
    BuscarUmResultado buscarUm(FonteId fonte, Cnpj cnpj, Nsu nsu);
}
