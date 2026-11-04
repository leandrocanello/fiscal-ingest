package br.com.doc.ingest.adapter.nfe;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.doc.ingest.adapter.fake.BlobStoreEmMemoria;
import br.com.doc.ingest.adapter.nfe.cert.GeradorPfxDeTeste;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte;
import br.com.doc.ingest.domain.adapter.ContextoConsulta;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.adapter.ManifestarResultado;
import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Propriedades do manifestar com transporte fake em memória (inv. 5). Sem rede. */
class NfeNacionalAdapterManifestarPropriedadesTest {

    private static final FonteId FONTE = new FonteId("NFE_AN");
    private static final Cnpj CNPJ = new Cnpj("11111111000191");
    private static final String CHAVE = "35261012345678000195550010000001231000000014";
    private static final String OUTRA = "35261012345678000195550010000001241000000012";
    private static final Instant AGORA = Instant.parse("2026-11-04T11:00:00Z");
    private static final String NFE = "http://www.portalfiscal.inf.br/nfe";
    private static final Credencial CREDENCIAL = nova();

    private static Credencial nova() {
        char[] senha = "senha-de-teste".toCharArray();
        return new Credencial(GeradorPfxDeTeste.moderno(senha.clone(), AGORA.minus(1, ChronoUnit.DAYS),
                AGORA.plus(365, ChronoUnit.DAYS)), senha);
    }

    private static String corpoSoap(String cStatLote, String cStatEvento, String chave, String tp, String seq) {
        String ret = cStatEvento == null ? "" : "<retEvento versao=\"1.00\"><infEvento><cStat>" + cStatEvento
                + "</cStat><chNFe>" + chave + "</chNFe><tpEvento>" + tp + "</tpEvento><nSeqEvento>" + seq
                + "</nSeqEvento></infEvento></retEvento>";
        return "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body>"
                + "<retEnvEvento versao=\"1.00\" xmlns=\"" + NFE + "\"><idLote>1</idLote><cStat>" + cStatLote
                + "</cStat>" + ret + "</retEnvEvento></soap:Body></soap:Envelope>";
    }

    @Provide
    Arbitrary<String> corpos() {
        Arbitrary<String> cstat = Arbitraries.of("128", "135", "136", "573", "650", "999", "");
        Arbitrary<String> chave = Arbitraries.of(CHAVE, OUTRA);
        Arbitrary<String> tp = Arbitraries.of("210210", "210200");
        Arbitrary<String> seq = Arbitraries.of("1", "2");
        Arbitrary<String> estruturado = Arbitraries.of("128", "100", "656").flatMap(lote ->
                cstat.flatMap(ev -> chave.flatMap(c -> tp.flatMap(t -> seq.map(s -> corpoSoap(lote, ev, c, t, s))))));
        return Arbitraries.oneOf(estruturado, Arbitraries.strings().ofMaxLength(60));
    }

    @Property(tries = 300)
    void todoCorpoGuardadoEOkSoComRetEventoDeSucessoDaMesmaChave(
            @ForAll("corpos") String corpo,
            @ForAll("status") int status,
            @ForAll boolean blobFalha) {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        var blobs = new BlobStoreEmMemoria();
        BlobStore store = new BlobStore() {
            @Override
            public br.com.doc.ingest.domain.blob.BlobRef gravar(String c, byte[] b, String sha) {
                if (blobFalha) {
                    throw new IllegalStateException("fora do ar");
                }
                return blobs.gravar(c, b, sha);
            }

            @Override
            public java.util.Optional<byte[]> ler(br.com.doc.ingest.domain.blob.BlobRef ref) {
                return blobs.ler(ref);
            }
        };
        var adapter = new NfeNacionalAdapter(FONTE, TipoAmbiente.HOMOLOGACAO, URI.create("http://localhost/d"), store,
                Clock.fixed(AGORA, ZoneOffset.UTC),
                c -> (e, a, env) -> new ResultadoTransporte.RespostaHttp(status, bytes),
                URI.create("http://localhost/e"));
        adapter.autenticar(CREDENCIAL);

        ManifestarResultado r = adapter.manifestar(new ContextoConsulta(FONTE, CNPJ), CHAVE, TipoManifestacao.CIENCIA)
                .orElseThrow();

        if (r.desfecho() instanceof Desfecho.Ok) {
            assertThat(blobFalha).isFalse();
            assertThat(status).isBetween(200, 299);
            assertThat(corpo).contains("<chNFe>" + CHAVE + "</chNFe>").contains("<tpEvento>210210</tpEvento>")
                    .contains("<nSeqEvento>1</nSeqEvento>");
            assertThat(corpo).containsPattern("<cStat>(135|136|573)</cStat><chNFe>");
        }
        if (blobFalha) {
            assertThat(r.envelope()).isEmpty();
        } else {
            assertThat(r.envelope()).isPresent();
            assertThat(blobs.ler(r.envelope().get())).hasValueSatisfying(b -> assertThat(b).isEqualTo(bytes));
        }
    }

    @Provide
    Arbitrary<Integer> status() {
        return Arbitraries.of(200, 200, 200, 201, 204, 301, 400, 401, 403, 404, 500, 503);
    }
}
