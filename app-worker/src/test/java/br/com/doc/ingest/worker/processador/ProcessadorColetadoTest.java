package br.com.doc.ingest.worker.processador;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.domain.blob.BlobIndisponivelException;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.catalogo.CadastroEmpresas;
import br.com.doc.ingest.domain.catalogo.Catalogo;
import br.com.doc.ingest.domain.catalogo.CnpjsDoConteudo;
import br.com.doc.ingest.domain.catalogo.ArquivoFicha;
import br.com.doc.ingest.domain.catalogo.Confianca;
import br.com.doc.ingest.domain.catalogo.Direcao;
import br.com.doc.ingest.domain.catalogo.EmpresaDoTenant;
import br.com.doc.ingest.domain.catalogo.EstadoDocumento;
import br.com.doc.ingest.domain.catalogo.FalhaCatalogoException;
import br.com.doc.ingest.domain.catalogo.FichaDocumento;
import br.com.doc.ingest.domain.catalogo.MetadadosDocumento;
import br.com.doc.ingest.domain.catalogo.MotivoQuarentena;
import br.com.doc.ingest.domain.catalogo.OrigemArquivo;
import br.com.doc.ingest.domain.catalogo.Quarentena;
import br.com.doc.ingest.domain.catalogo.RegistroArquivo;
import br.com.doc.ingest.domain.catalogo.ResultadoRegistro;
import br.com.doc.ingest.domain.catalogo.SituacaoDocumento;
import br.com.doc.ingest.domain.catalogo.TipoArquivo;
import br.com.doc.ingest.domain.catalogo.TipoEmpresa;
import br.com.doc.ingest.domain.catalogo.VinculoEmpresa;
import br.com.doc.ingest.domain.model.Ambiente;
import br.com.doc.ingest.domain.model.ChaveAcesso;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;
import br.com.doc.ingest.domain.parser.DfeAnalisado;
import br.com.doc.ingest.domain.parser.ParserDfe;
import br.com.doc.ingest.domain.parser.ResultadoParse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessadorColetadoTest {

    private static final Cnpj CONSULTADO = new Cnpj("11111111000191");
    private static final Cnpj EMITENTE = new Cnpj("22222222000191");
    private static final Cnpj DESTINATARIO = new Cnpj("33333333000191");
    private static final ChaveAcesso CHAVE = new ChaveAcesso("35261022222222000191550010000001231000001230");
    private static final Instant RECEBIDO_EM = Instant.parse("2026-11-12T10:00:00Z");
    private static final byte[] XML = "<sintetico/>".getBytes(StandardCharsets.UTF_8);

    private final BlobStoreEmMemoria blobs = new BlobStoreEmMemoria();
    private final ParserFixo parser = new ParserFixo();
    private final CatalogoFake catalogo = new CatalogoFake();
    private final CadastroFake cadastro = new CadastroFake();
    private final QuarentenaFake quarentena = new QuarentenaFake();
    private final ProcessadorColetado processador =
            new ProcessadorColetado(blobs, parser, catalogo, cadastro, quarentena);

    @Test
    void registraNotaComOsVinculosDoRoteador() {
        cadastro.tenant(1L, empresa(10L, EMITENTE), empresa(11L, DESTINATARIO));
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty());

        var resultado = processador.processar(item());

        assertThat(resultado).isInstanceOf(ResultadoProcessamento.Registrado.class);
        assertThat(catalogo.registros).singleElement().satisfies(r -> {
            assertThat(r.tenantId()).isEqualTo(1L);
            assertThat(r.chave()).isEqualTo(CHAVE);
            assertThat(r.situacao()).isEqualTo(SituacaoDocumento.AUTORIZADA);
            assertThat(r.blob()).isEqualTo(item().blob());
            assertThat(r.vinculos()).containsExactly(
                    new VinculoEmpresa(10L, Direcao.SAIDA), new VinculoEmpresa(11L, Direcao.ENTRADA));
        });
    }

    @Test
    void provenienciaDaColetaEhOficialComLedger() {
        cadastro.tenant(1L);
        parser.resultado = analisado(TipoArquivo.RESUMO, Optional.empty(), Optional.empty());

        processador.processar(item());

        var proveniencia = catalogo.registros.getFirst().proveniencia();
        assertThat(proveniencia.origem()).isEqualTo(OrigemArquivo.DFE_AN);
        assertThat(proveniencia.confianca()).isEqualTo(Confianca.OFICIAL);
        assertThat(proveniencia.ledgerNsuId()).contains(77L);
        assertThat(proveniencia.recebimentoId()).isEmpty();
        assertThat(proveniencia.recebidoEm()).isEqualTo(RECEBIDO_EM);
    }

    @Test
    void tipoEEventoVemDoParser() {
        cadastro.tenant(1L, empresa(10L, EMITENTE));
        parser.resultado = analisado(TipoArquivo.EVENTO, Optional.of("110111"), Optional.of(1));

        processador.processar(item());

        var registro = catalogo.registros.getFirst();
        assertThat(registro.tipo()).isEqualTo(TipoArquivo.EVENTO);
        assertThat(registro.eventoTipo()).contains("110111");
        assertThat(registro.eventoSeq()).contains(1);
        assertThat(registro.vinculos()).containsExactly(new VinculoEmpresa(10L, Direcao.SAIDA));
        assertThat(parser.schemaRecebido).isEqualTo("procEventoNFe_v1.00.xsd");
        assertThat(parser.xmlRecebido).isEqualTo(XML);
    }

    @Test
    void cnpjServindoDoisTenantsRegistraEmCadaUm() {
        cadastro.tenant(1L, empresa(10L, CONSULTADO), empresa(11L, EMITENTE));
        cadastro.tenant(2L, empresa(20L, CONSULTADO));
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty());

        var resultado = processador.processar(item());

        assertThat(catalogo.registros).extracting(RegistroArquivo::tenantId).containsExactly(1L, 2L);
        assertThat(catalogo.registros.get(0).vinculos()).containsExactly(new VinculoEmpresa(11L, Direcao.SAIDA));
        assertThat(catalogo.registros.get(1).vinculos()).isEmpty();
        assertThat(((ResultadoProcessamento.Registrado) resultado).resultados()).hasSize(2);
    }

    @Test
    void falhaNoSegundoTenantPropagaEReprocessarConclui() {
        cadastro.tenant(1L, empresa(10L, EMITENTE));
        cadastro.tenant(2L, empresa(20L, EMITENTE));
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty());
        catalogo.falharUmaVezNoTenant = 2L;

        assertThatThrownBy(() -> processador.processar(item())).isInstanceOf(FalhaCatalogoException.class);
        var resultado = processador.processar(item());

        assertThat(resultado).isInstanceOf(ResultadoProcessamento.Registrado.class);
        assertThat(((ResultadoProcessamento.Registrado) resultado).resultados())
                .extracting(ResultadoRegistro::documentoId).containsExactly(1L, 2L);
        assertThat(catalogo.documentos).hasSize(2);
    }

    @Test
    void semTenantDevolveSemTenantENaoRegistra() {
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty());

        var resultado = processador.processar(item());

        assertThat(resultado).isInstanceOf(ResultadoProcessamento.SemTenant.class);
        assertThat(catalogo.registros).isEmpty();
        assertThat(quarentena.abertas).isEmpty();
    }

    @Test
    void comandoEResultadoNaoCarregamBytes() {
        List<Class<?>> tipos = new ArrayList<>(List.of(ItemColetado.class));
        tipos.addAll(List.of(ResultadoProcessamento.class.getPermittedSubclasses()));

        for (var tipo : tipos) {
            assertThat(tipo.isRecord()).as(tipo.getSimpleName()).isTrue();
            for (RecordComponent componente : tipo.getRecordComponents()) {
                assertThat(componente.getType()).as(tipo.getSimpleName() + "." + componente.getName())
                        .isNotEqualTo(byte[].class);
            }
        }
    }

    @Test
    void semVinculoAbreQuarentenaSemEmpresa() {
        cadastro.tenant(1L);
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty());

        var resultado = (ResultadoProcessamento.Registrado) processador.processar(item());

        var arquivoId = resultado.resultados().getFirst().arquivoId();
        assertThat(quarentena.abertas).containsExactly(arquivoId);
        assertThat(quarentena.motivos).containsExactly(MotivoQuarentena.SEM_EMPRESA);
    }

    @Test
    void documentoComVinculoNaoAbreQuarentena() {
        cadastro.tenant(1L, empresa(10L, EMITENTE));
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty());

        processador.processar(item());

        assertThat(quarentena.abertas).isEmpty();
    }

    @Test
    void eventoDeDocumentoJaVinculadoNaoAbreQuarentena() {
        cadastro.tenant(1L, empresa(11L, DESTINATARIO));
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty());
        processador.processar(item());
        parser.resultado = analisado(TipoArquivo.EVENTO, Optional.of("210210"), Optional.of(1));

        processador.processar(item("evento.xml"));

        assertThat(catalogo.registros.getLast().vinculos()).isEmpty();
        assertThat(quarentena.abertas).isEmpty();
    }

    @Test
    void vinculoNovoFechaQuarentenaDoDocumento() {
        cadastro.tenant(1L, empresa(11L, DESTINATARIO));
        parser.resultado = analisado(TipoArquivo.EVENTO, Optional.of("210210"), Optional.of(1));
        processador.processar(item("evento.xml"));
        assertThat(quarentena.abertas).hasSize(1);
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty());

        processador.processar(item());

        assertThat(quarentena.abertas).isEmpty();
        assertThat(quarentena.resolvidas).isEqualTo(1);
    }

    @Test
    void reprocessarDepoisDeCadastrarAEmpresaFechaQuarentena() {
        cadastro.tenant(1L);
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty());
        processador.processar(item());
        assertThat(quarentena.abertas).hasSize(1);
        cadastro.tenant(1L, empresa(10L, EMITENTE));

        processador.processar(item());

        assertThat(quarentena.abertas).isEmpty();
        assertThat(catalogo.documentos).hasSize(1);
    }

    @Test
    void reprocessarAbreAQuarentenaQueFaltou() {
        cadastro.tenant(1L);
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty());
        quarentena.falharUmaVez = true;
        assertThatThrownBy(() -> processador.processar(item())).isInstanceOf(FalhaCatalogoException.class);

        var resultado = (ResultadoProcessamento.Registrado) processador.processar(item());

        assertThat(resultado.resultados().getFirst().arquivoNovo()).isFalse();
        assertThat(quarentena.abertas).containsExactly(resultado.resultados().getFirst().arquivoId());
    }

    @Test
    void blobAusenteLancaENaoRegistra() {
        cadastro.tenant(1L, empresa(10L, EMITENTE));
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty());
        var ausente = new BlobRef("raw/pull/NFE_AN/homologacao/" + CONSULTADO.valor() + "/ausente.xml", Sha256.hex(XML));
        var item = new ItemColetado(new FonteId("NFE_AN"), CONSULTADO, Ambiente.HOMOLOGACAO, new Nsu(5L), 77L,
                ausente, "procNFe_v4.00.xsd", RECEBIDO_EM);

        assertThatThrownBy(() -> processador.processar(item))
                .isInstanceOfSatisfying(BlobIndisponivelException.class, e -> {
                    assertThat(e.retentavel()).isTrue();
                    assertThat(e.getMessage()).doesNotContain(CONSULTADO.valor()).doesNotContain(ausente.chave());
                });
        assertThat(parser.schemaRecebido).isNull();
        assertThat(catalogo.registros).isEmpty();
        assertThat(quarentena.abertas).isEmpty();
    }

    @Test
    void schemaDesconhecidoNaoApagaNemRegistra() {
        cadastro.tenant(1L, empresa(10L, EMITENTE));
        parser.resultado = new ResultadoParse.Desconhecido();
        var item = item();

        var resultado = processador.processar(item);

        assertThat(resultado).isInstanceOf(ResultadoProcessamento.SchemaDesconhecido.class);
        assertThat(blobs.ler(item.blob())).hasValueSatisfying(b -> assertThat(b).isEqualTo(XML));
        assertThat(catalogo.registros).isEmpty();
        assertThat(quarentena.abertas).isEmpty();
    }

    @Test
    void xmlInvalidoNaoRegistra() {
        cadastro.tenant(1L, empresa(10L, EMITENTE));
        parser.resultado = new ResultadoParse.Invalido("xml malformado");
        var item = item();

        var resultado = processador.processar(item);

        assertThat(resultado).isEqualTo(new ResultadoProcessamento.Invalido("xml malformado"));
        assertThat(blobs.ler(item.blob())).isPresent();
        assertThat(catalogo.registros).isEmpty();
        assertThat(quarentena.abertas).isEmpty();
    }

    @Test
    void falhaNaoExpoeCpfDoDestinatario() {
        var cpf = "12345678909";
        cadastro.tenant(1L, empresa(10L, EMITENTE));
        parser.resultado = analisado(TipoArquivo.COMPLETO, Optional.empty(), Optional.empty(),
                new CnpjsDoConteudo(Optional.of(EMITENTE), Optional.of(cpf), Optional.empty(), List.of()));
        catalogo.falharUmaVezNoTenant = 1L;

        assertThatThrownBy(() -> processador.processar(item()))
                .satisfies(e -> assertThat(String.valueOf(e)).doesNotContain(cpf))
                .satisfies(e -> assertThat(e.getCause()).isNull());
        assertThat(item().toString()).doesNotContain(cpf);
    }

    // ---------------------------------------------------------------- apoio

    private ItemColetado item() {
        return item("sintetico.xml");
    }

    private ItemColetado item(String nome) {
        var conteudo = (nome + XML.length).getBytes(StandardCharsets.UTF_8);
        var bytes = nome.equals("sintetico.xml") ? XML : conteudo;
        var ref = blobs.gravar("raw/pull/NFE_AN/" + nome, bytes, Sha256.hex(bytes));
        return new ItemColetado(new FonteId("NFE_AN"), CONSULTADO, Ambiente.HOMOLOGACAO, new Nsu(5L), 77L,
                ref, "procEventoNFe_v1.00.xsd", RECEBIDO_EM);
    }

    private static EmpresaDoTenant empresa(long id, Cnpj cnpj) {
        return new EmpresaDoTenant(id, cnpj, TipoEmpresa.EMPRESA);
    }

    private static ResultadoParse analisado(TipoArquivo tipo, Optional<String> eventoTipo, Optional<Integer> eventoSeq) {
        return analisado(tipo, eventoTipo, eventoSeq, new CnpjsDoConteudo(Optional.of(EMITENTE),
                Optional.of(DESTINATARIO.valor()), Optional.empty(), List.of()));
    }

    private static ResultadoParse analisado(
            TipoArquivo tipo, Optional<String> eventoTipo, Optional<Integer> eventoSeq, CnpjsDoConteudo cnpjs) {
        var metadados = new MetadadosDocumento(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(EMITENTE), Optional.empty(), Optional.empty(), Optional.empty());
        var situacao = tipo == TipoArquivo.COMPLETO ? SituacaoDocumento.AUTORIZADA : SituacaoDocumento.SO_RESUMO;
        return new ResultadoParse.Analisado(new DfeAnalisado(CHAVE, metadados, situacao, tipo, eventoTipo, eventoSeq, cnpjs));
    }

    static final class ParserFixo implements ParserDfe {
        ResultadoParse resultado = new ResultadoParse.Desconhecido();
        String schemaRecebido;
        byte[] xmlRecebido;

        @Override
        public ResultadoParse analisar(String schema, byte[] xml) {
            schemaRecebido = schema;
            xmlRecebido = xml.clone();
            return resultado;
        }
    }

    /** Catálogo em memória: um documento por (tenant, chave), um arquivo por (documento, sha256), vínculos somados. */
    static final class CatalogoFake implements Catalogo {
        final List<RegistroArquivo> registros = new ArrayList<>();
        final Map<String, Long> documentos = new HashMap<>();
        final Map<Long, Set<VinculoEmpresa>> vinculosPorDocumento = new HashMap<>();
        final Map<String, Long> arquivos = new HashMap<>();
        final Map<Long, Long> documentoDoArquivo = new HashMap<>();
        Long falharUmaVezNoTenant;

        @Override
        public ResultadoRegistro registrar(RegistroArquivo registro) {
            if (falharUmaVezNoTenant != null && falharUmaVezNoTenant == registro.tenantId()) {
                falharUmaVezNoTenant = null;
                throw new FalhaCatalogoException("40001");
            }
            registros.add(registro);
            var chave = registro.tenantId() + "/" + registro.chave().valor();
            boolean criado = !documentos.containsKey(chave);
            long id = documentos.computeIfAbsent(chave, k -> (long) documentos.size() + 1);
            var chaveArquivo = id + "/" + registro.blob().sha256();
            boolean arquivoNovo = !arquivos.containsKey(chaveArquivo);
            long arquivoId = arquivos.computeIfAbsent(chaveArquivo, k -> (long) arquivos.size() + 100);
            documentoDoArquivo.put(arquivoId, id);
            var vinculos = vinculosPorDocumento.computeIfAbsent(id, k -> new LinkedHashSet<>());
            var novos = registro.vinculos().stream().filter(vinculos::add).toList();
            return new ResultadoRegistro(id, arquivoId, criado, false, arquivoNovo, arquivoNovo, novos);
        }

        @Override
        public Optional<FichaDocumento> consultar(long tenantId, ChaveAcesso chave) {
            var id = documentos.get(tenantId + "/" + chave.valor());
            if (id == null) {
                return Optional.empty();
            }
            var estado = new EstadoDocumento(new MetadadosDocumento(Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                    SituacaoDocumento.SO_RESUMO, false);
            List<ArquivoFicha> arquivosDoDocumento = new ArrayList<>();
            return Optional.of(new FichaDocumento(id, tenantId, chave, estado,
                    List.copyOf(vinculosPorDocumento.get(id)), arquivosDoDocumento, RECEBIDO_EM));
        }
    }

    static final class CadastroFake implements CadastroEmpresas {
        private final Map<Long, List<EmpresaDoTenant>> porTenant = new java.util.TreeMap<>();

        void tenant(long tenantId, EmpresaDoTenant... empresas) {
            porTenant.put(tenantId, List.of(empresas));
        }

        @Override
        public List<Long> tenantsDoCnpjConsultado(Cnpj cnpjConsultado, Ambiente ambiente) {
            return List.copyOf(porTenant.keySet());
        }

        @Override
        public List<EmpresaDoTenant> empresasDoTenant(long tenantId, Ambiente ambiente) {
            return porTenant.getOrDefault(tenantId, List.of());
        }
    }

    /** Quarentena em memória; usa o catálogo fake para saber os arquivos de cada documento. */
    final class QuarentenaFake implements Quarentena {
        final Set<Long> abertas = new LinkedHashSet<>();
        final List<MotivoQuarentena> motivos = new ArrayList<>();
        int resolvidas;
        boolean falharUmaVez;

        @Override
        public boolean abrir(long tenantId, long arquivoId, MotivoQuarentena motivo) {
            if (falharUmaVez) {
                falharUmaVez = false;
                throw new FalhaCatalogoException("08006");
            }
            boolean abriu = abertas.add(arquivoId);
            if (abriu) {
                motivos.add(motivo);
            }
            return abriu;
        }

        @Override
        public int resolverDoDocumento(long tenantId, long documentoId) {
            int antes = abertas.size();
            abertas.removeIf(a -> Objects.equals(catalogo.documentoDoArquivo.get(a), documentoId));
            resolvidas += antes - abertas.size();
            return antes - abertas.size();
        }
    }
}
