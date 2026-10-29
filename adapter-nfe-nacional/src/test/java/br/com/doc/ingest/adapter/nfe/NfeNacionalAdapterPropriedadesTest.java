package br.com.doc.ingest.adapter.nfe;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.adapter.nfe.soap.ContratoDistDfe;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte;
import br.com.doc.ingest.domain.adapter.BuscarLoteResultado;
import br.com.doc.ingest.domain.adapter.ContextoConsulta;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/** Propriedades do buscarLote com transporte fake em memória (inv. 1 e 5). Sem rede. */
class NfeNacionalAdapterPropriedadesTest {

    private static final FonteId FONTE = new FonteId("NFE_AN");
    private static final Cnpj CNPJ = new Cnpj("11111111000191");
    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";
    private static final String XML = "<resNFe xmlns=\"" + NFE_NS + "\"><xNome>Sintetico</xNome></resNFe>";

    @Provide
    Arbitrary<String> cStats() {
        return Arbitraries.of("137", "138", "656", "108", "109", "589", "999");
    }

    private static String gzipBase64(String xml) {
        var saida = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(saida)) {
            gz.write(xml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(saida.toByteArray());
    }

    private static String resposta(String cStat, long ultNsu, long maxNsu, int docs) {
        StringBuilder lote = new StringBuilder();
        for (int i = 0; i < docs; i++) {
            lote.append("<docZip NSU=\"").append(String.format("%015d", ultNsu + i))
                    .append("\" schema=\"resNFe_v1.01.xsd\">").append(gzipBase64(XML + "<!--" + i + "-->")).append("</docZip>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soap:Envelope xmlns:soap=\"" + ContratoDistDfe.NS_SOAP12
                + "\"><soap:Body><retDistDFeInt versao=\"1.01\" xmlns=\"" + NFE_NS + "\"><tpAmb>1</tpAmb>"
                + "<cStat>" + cStat + "</cStat><xMotivo>x</xMotivo><ultNSU>" + String.format("%015d", ultNsu)
                + "</ultNSU><maxNSU>" + String.format("%015d", maxNsu) + "</maxNSU>"
                + (docs == 0 ? "" : "<loteDistDFeInt>" + lote + "</loteDistDFeInt>")
                + "</retDistDFeInt></soap:Body></soap:Envelope>";
    }

    private static NfeNacionalAdapter adapter(BlobStore store, String corpo) {
        NfeNacionalAdapter a = new NfeNacionalAdapter(FONTE, TipoAmbiente.PRODUCAO,
                java.net.URI.create("http://localhost/fake"), store,
                Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC),
                cred -> (endpoint, action, envelope) ->
                        new ResultadoTransporte.RespostaHttp(200, corpo.getBytes(StandardCharsets.UTF_8)));
        a.autenticar(new Credencial(new byte[] {1}, new char[0]));
        return a;
    }

    @Property(tries = 200)
    void cursorNuncaRetrocedeSoOkEVazioMudamEDocumentoTemBlob(
            @ForAll("cStats") String cStat,
            @ForAll @LongRange(min = 0, max = 1_000_000) long entrada,
            @ForAll @LongRange(min = 0, max = 1_000_000) long ultNsu,
            @ForAll @LongRange(min = 0, max = 1_000_000) long maxNsu,
            @ForAll @IntRange(min = 0, max = 3) int docs) {
        var blobs = new BlobStoreEmMemoria();
        BuscarLoteResultado r = adapter(blobs, resposta(cStat, ultNsu, maxNsu, docs))
                .buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor(Long.toString(entrada)));

        long saida = CursorNsu.nsu(r.proximoCursor()).valor();
        assertThat(saida).isGreaterThanOrEqualTo(entrada);
        assertThat(r.ultNsu().valor()).isGreaterThanOrEqualTo(entrada);
        if (!(r.desfecho() instanceof Desfecho.Ok || r.desfecho() instanceof Desfecho.Vazio)) {
            assertThat(saida).isEqualTo(entrada);
            assertThat(r.documentos()).isEmpty();
        }
        for (var d : r.documentos()) {
            String chave = d.blobRef().orElseThrow();
            // o sha256 está no final da chave (inv. 7); o blob precisa existir
            String sha = chave.substring(chave.lastIndexOf('-') + 1, chave.length() - ".xml".length());
            assertThat(blobs.ler(new BlobRef(chave, sha))).isPresent();
        }
    }

    /** BlobStore que lança na n-ésima gravação. */
    private static final class Falha implements BlobStore {
        private final BlobStore alvo = new BlobStoreEmMemoria();
        private final int n;
        private int c;

        Falha(int n) {
            this.n = n;
        }

        @Override
        public BlobRef gravar(String chave, byte[] conteudo, String sha256) {
            if (++c == n) {
                throw new IllegalStateException("fora do ar");
            }
            return alvo.gravar(chave, conteudo, sha256);
        }

        @Override
        public Optional<byte[]> ler(BlobRef ref) {
            return alvo.ler(ref);
        }
    }

    @Property(tries = 40)
    void falhaEmQualquerGravacaoNaoAvancaNemDevolveDocumento(@ForAll @IntRange(min = 1, max = 4) int falharNa) {
        BuscarLoteResultado r = adapter(new Falha(falharNa), resposta("138", 20, 20, 3))
                .buscarLote(new ContextoConsulta(FONTE, CNPJ), new Cursor("10"));

        assertThat(r.documentos()).isEmpty();
        assertThat(r.proximoCursor()).isEqualTo(new Cursor("10"));
        assertThat(r.ultNsu().valor()).isEqualTo(10);
        assertThat(r.desfecho()).isInstanceOf(Desfecho.IndisponivelTemporario.class);
    }
}
