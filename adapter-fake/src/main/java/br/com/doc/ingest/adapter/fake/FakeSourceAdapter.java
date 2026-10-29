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
 * <p>Cursor do fake = {@code ultNsu} decimal ({@code ""} = 0, DA-19): so devolve NSU maior que o cursor;
 * {@code Vazio} devolve o cursor e o NSU de entrada.
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
        long entrada = nsuDoCursor(cursor);
        List<Documento> novos = documentosPorCnpj.getOrDefault(contexto.cnpj(), List.of()).stream()
                .filter(d -> d.nsu().valor() > entrada)
                .toList();

        if (novos.isEmpty()) {
            return new BuscarLoteResultado(List.of(), cursor, new Nsu(entrada), false, new Desfecho.Vazio());
        }

        Nsu ultNsu = novos.stream().map(Documento::nsu).max(Comparator.naturalOrder()).orElseThrow();
        return new BuscarLoteResultado(
                novos,
                new Cursor(Long.toString(ultNsu.valor())),
                ultNsu,
                false,
                new Desfecho.Ok()
        );
    }

    /** Cursor do fake = ultNsu decimal; "" equivale a 0 (DA-19). */
    private static long nsuDoCursor(Cursor cursor) {
        String token = cursor.token();
        if (token.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("cursor do fake deve ser decimal: " + token, e);
        }
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
