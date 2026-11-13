package br.com.doc.ingest.worker.processador;

import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.catalogo.CadastroEmpresas;
import br.com.doc.ingest.domain.catalogo.Catalogo;
import br.com.doc.ingest.domain.catalogo.Confianca;
import br.com.doc.ingest.domain.catalogo.OrigemArquivo;
import br.com.doc.ingest.domain.catalogo.ProvenienciaInformada;
import br.com.doc.ingest.domain.catalogo.Quarentena;
import br.com.doc.ingest.domain.catalogo.RegistroArquivo;
import br.com.doc.ingest.domain.catalogo.ResultadoRegistro;
import br.com.doc.ingest.domain.catalogo.RoteadorConteudo;
import br.com.doc.ingest.domain.parser.DfeAnalisado;
import br.com.doc.ingest.domain.parser.ParserDfe;
import br.com.doc.ingest.domain.parser.ResultadoParse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Processor da coleta (§6.3, passos 6–7): lê o bruto, faz o parse, roteia pelo conteúdo (inv. 10) e
 * registra no catálogo de cada tenant que tem o CNPJ consultado (§8.1, D3 da E3-S07).
 *
 * <p>É borda, não core (§9): só fala com portas (DA-32). Reprocessar o mesmo item é seguro, porque o
 * catálogo é idempotente; se um tenant falhar, a exceção sobe e o item inteiro é reprocessado.
 */
public final class ProcessadorColetado {

    private final BlobStore blobs;
    private final ParserDfe parser;
    private final Catalogo catalogo;
    private final CadastroEmpresas cadastro;
    private final Quarentena quarentena;

    public ProcessadorColetado(
            BlobStore blobs, ParserDfe parser, Catalogo catalogo, CadastroEmpresas cadastro, Quarentena quarentena) {
        this.blobs = Objects.requireNonNull(blobs, "blobs");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.cadastro = Objects.requireNonNull(cadastro, "cadastro");
        this.quarentena = Objects.requireNonNull(quarentena, "quarentena");
    }

    public ResultadoProcessamento processar(ItemColetado item) {
        Objects.requireNonNull(item, "item");
        var tenants = cadastro.tenantsDoCnpjConsultado(item.cnpjConsultado(), item.ambiente());
        if (tenants.isEmpty()) {
            return new ResultadoProcessamento.SemTenant();
        }
        // TODO(E3-S07, próximo passo): exceção própria e retentável para blob ausente
        var xml = blobs.ler(item.blob()).orElseThrow(() -> new IllegalStateException("blob indisponível"));
        return switch (parser.analisar(item.schema(), xml)) {
            case ResultadoParse.Desconhecido d -> new ResultadoProcessamento.SchemaDesconhecido();
            case ResultadoParse.Invalido i -> new ResultadoProcessamento.Invalido(i.motivo());
            case ResultadoParse.Analisado a -> registrar(item, a.dfe(), tenants);
        };
    }

    private ResultadoProcessamento registrar(ItemColetado item, DfeAnalisado dfe, List<Long> tenants) {
        var proveniencia = new ProvenienciaInformada(OrigemArquivo.DFE_AN, Confianca.OFICIAL,
                Optional.of(item.ledgerNsuId()), Optional.empty(), item.recebidoEm());
        List<ResultadoRegistro> resultados = new ArrayList<>();
        for (long tenantId : tenants) {
            var empresas = cadastro.empresasDoTenant(tenantId, item.ambiente());
            var vinculos = RoteadorConteudo.rotear(dfe.tipo(), dfe.cnpjs(), item.cnpjConsultado(), empresas);
            resultados.add(catalogo.registrar(new RegistroArquivo(tenantId, dfe.chave(), dfe.metadados(),
                    dfe.situacao(), dfe.tipo(), dfe.eventoTipo(), dfe.eventoSeq(), item.blob(), vinculos,
                    proveniencia)));
        }
        return new ResultadoProcessamento.Registrado(resultados);
    }
}
