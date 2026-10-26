package br.com.doc.ingest.adapter.fake;

import br.com.doc.ingest.domain.adapter.*;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Documento;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter FAKE em memoria - implementa SourceAdapter para testar o core sem SEFAZ.
 *
 * <p>Permite pre-carregar documentos por CNPJ e simula as operacoes do adapter:
 * <ul>
 *   <li>buscarLote: devolve os documentos pre-carregados para o CNPJ</li>
 *   <li>buscarUm: busca por NSU especifico entre os pre-carregados</li>
 *   <li>autenticar: no-op (nao faz nada)</li>
 * </ul>
 *
 * <p>{@code ultNsu} do lote = maior NSU dos documentos pre-carregados. Limitacao: como o fake
 * nao recebe o NSU atual (so o {@link Cursor} opaco), devolve {@code Nsu(0)} quando o lote e vazio.
 *
 * <p>Este adapter NAO deve ser usado em producao. Serve apenas para testes do core.
 */
public class FakeSourceAdapter implements SourceAdapter {

    private final FonteId fonteId;
    private final Map<Cnpj, List<Documento>> documentosPorCnpj = new ConcurrentHashMap<>();

    public FakeSourceAdapter(FonteId fonteId) {
        this.fonteId = fonteId;
    }

    /**
     * Pre-carrega documentos para um CNPJ (para configurar cenarios de teste).
     */
    public void preCarregar(Cnpj cnpj, List<Documento> documentos) {
        documentosPorCnpj.put(cnpj, new ArrayList<>(documentos));
    }

    @Override
    public AdapterDescriptor descritor() {
        return new AdapterDescriptor(
                fonteId,
                new Capacidades(true, true, true, false)
        );
    }

    @Override
    public void autenticar(Credencial credencial) {
        // FAKE: no-op
    }

    @Override
    public BuscarLoteResultado buscarLote(ContextoConsulta contexto, Cursor cursor) {
        List<Documento> documentos = documentosPorCnpj.getOrDefault(contexto.cnpj(), List.of());

        if (documentos.isEmpty()) {
            return new BuscarLoteResultado(
                    List.of(),
                    cursor,
                    new Nsu(0),
                    false,
                    new Desfecho.Vazio()
            );
        }

        return new BuscarLoteResultado(
                List.copyOf(documentos),
                cursor,
                documentos.stream().map(Documento::nsu).max(Comparator.naturalOrder()).orElseThrow(),
                false,
                new Desfecho.Ok()
        );
    }

    @Override
    public BuscarUmResultado buscarUm(FonteId fonte, Cnpj cnpj, Nsu nsu) {
        List<Documento> documentos = documentosPorCnpj.getOrDefault(cnpj, List.of());

        Optional<Documento> encontrado = documentos.stream()
                .filter(d -> d.nsu().equals(nsu))
                .findFirst();

        if (encontrado.isPresent()) {
            return new BuscarUmResultado(encontrado, new Desfecho.Ok());
        }

        return new BuscarUmResultado(Optional.empty(), new Desfecho.Vazio());
    }
}
