package br.com.doc.ingest.adapter.nfe;

import br.com.doc.ingest.adapter.nfe.doczip.DocZipDecodificado;
import br.com.doc.ingest.adapter.nfe.doczip.DocZipDecoder;
import br.com.doc.ingest.adapter.nfe.mapping.CStatMapper;
import br.com.doc.ingest.adapter.nfe.mapping.TradutorRespostaDistDfe;
import br.com.doc.ingest.adapter.nfe.soap.ContratoDistDfe;
import br.com.doc.ingest.adapter.nfe.soap.DocZip;
import br.com.doc.ingest.adapter.nfe.soap.EnvelopeDistDfe;
import br.com.doc.ingest.adapter.nfe.soap.LeitorRespostaDistDfe;
import br.com.doc.ingest.adapter.nfe.soap.RetDistDfeInt;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.adapter.nfe.transporte.FornecedorTransporte;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte;
import br.com.doc.ingest.adapter.nfe.transporte.TransporteSoap;
import br.com.doc.ingest.domain.adapter.AdapterDescriptor;
import br.com.doc.ingest.domain.adapter.BuscarLoteResultado;
import br.com.doc.ingest.domain.adapter.BuscarUmResultado;
import br.com.doc.ingest.domain.adapter.Capacidades;
import br.com.doc.ingest.domain.adapter.ContextoConsulta;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.adapter.SourceAdapter;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Documento;
import br.com.doc.ingest.domain.model.FonteId;
import br.com.doc.ingest.domain.model.Nsu;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter da distribuição de DF-e do Ambiente Nacional (distNSU).
 *
 * <p>Fluxo do {@code buscarLote}: monta o distDFeInt, envia pelo {@link TransporteSoap}, grava o envelope
 * bruto no {@link BlobStore} antes de interpretar (inv. 5), lê o {@code retDistDFeInt}, grava cada docZip
 * descompactado (sha256 na chave, inv. 7) e devolve só metadado + {@code BlobRef} + {@link Desfecho} (inv. 3 e 4).
 */
public final class NfeNacionalAdapter implements SourceAdapter {

    private static final String MODELO_NFE = "55";
    private static final Desfecho TEMPORARIO = new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5));
    private static final Desfecho CURSOR_INCONSISTENTE = new Desfecho.IndisponivelDefinitivo("cursor inconsistente");

    private final FonteId fonte;
    private final TipoAmbiente ambiente;
    private final URI endpoint;
    private final BlobStore blobs;
    private final Clock relogio;
    private final FornecedorTransporte fornecedor;
    private final TradutorRespostaDistDfe tradutor = new TradutorRespostaDistDfe(new CStatMapper());
    private final DocZipDecoder decoder = new DocZipDecoder();

    private volatile TransporteSoap transporte;

    public NfeNacionalAdapter(FonteId fonte, TipoAmbiente ambiente, URI endpoint, BlobStore blobs, Clock relogio,
                              FornecedorTransporte fornecedor) {
        this.fonte = Objects.requireNonNull(fonte, "fonte");
        this.ambiente = Objects.requireNonNull(ambiente, "ambiente");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.blobs = Objects.requireNonNull(blobs, "blobs");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        this.fornecedor = Objects.requireNonNull(fornecedor, "fornecedor");
    }

    @Override
    public AdapterDescriptor descritor() {
        return new AdapterDescriptor(fonte, new Capacidades(true, false, false, false));
    }

    @Override
    public void autenticar(Credencial credencial) {
        this.transporte = fornecedor.para(credencial);
    }

    @Override
    public BuscarLoteResultado buscarLote(ContextoConsulta contexto, Cursor cursor) {
        TransporteSoap atual = transporte;
        if (atual == null) {
            throw new IllegalStateException("buscarLote antes de autenticar");
        }
        Cnpj cnpj = contexto.cnpj();
        Nsu entrada = CursorNsu.nsu(cursor);
        Instant agora = relogio.instant();

        ResultadoTransporte resultado = atual.enviar(endpoint, ContratoDistDfe.ACTION,
                EnvelopeDistDfe.soap12DistNsu(ambiente, cnpj, entrada));
        if (!(resultado instanceof ResultadoTransporte.RespostaHttp http)) {
            return semAvanco(cursor, entrada, tradutor.deTransporte(resultado).orElseThrow(), Optional.empty());
        }

        // inv. 5: todo corpo HTTP é guardado antes de qualquer interpretação
        byte[] corpo = http.corpo();
        String shaEnvelope = Sha256.hex(corpo);
        BlobRef envelope;
        try {
            envelope = blobs.gravar(
                    ChavesBlobPull.envelope(fonte, ambiente, cnpj, agora, shaEnvelope), corpo, shaEnvelope);
        } catch (RuntimeException e) {
            return semAvanco(cursor, entrada, TEMPORARIO, Optional.empty());
        }
        Optional<BlobRef> refEnvelope = Optional.of(envelope);

        Optional<Desfecho> deHttp = tradutor.deTransporte(http);
        if (deHttp.isPresent()) {
            return semAvanco(cursor, entrada, deHttp.get(), refEnvelope);
        }

        LeitorRespostaDistDfe.Resultado leitura = LeitorRespostaDistDfe.ler(corpo);
        Desfecho desfecho = tradutor.deLeitura(leitura);
        if (!(leitura instanceof LeitorRespostaDistDfe.Valida valida)
                || !(desfecho instanceof Desfecho.Ok || desfecho instanceof Desfecho.Vazio)) {
            // inv. 1: só Ok/Vazio mudam o cursor; o ultNSU da resposta é ignorado
            return semAvanco(cursor, entrada, desfecho, refEnvelope);
        }
        RetDistDfeInt ret = valida.retorno();

        Nsu ultNsu;
        Nsu maxNsu;
        try {
            ultNsu = nsuDe(ret.getUltNSU());
            maxNsu = nsuDe(ret.getMaxNSU());
        } catch (RuntimeException e) {
            return semAvanco(cursor, entrada, TEMPORARIO, refEnvelope);
        }
        if (ultNsu.ehMenorQue(entrada)) {
            return semAvanco(cursor, entrada, CURSOR_INCONSISTENTE, refEnvelope);
        }
        if (desfecho instanceof Desfecho.Vazio) {
            return new BuscarLoteResultado(List.of(), CursorNsu.de(ultNsu), ultNsu, false, desfecho, refEnvelope);
        }

        // DA-14: docZip corrompido ou falha do BlobStore derrubam o lote inteiro (o envelope já está guardado)
        List<Documento> documentos = new ArrayList<>();
        List<DocZip> docZips = ret.getDocumentos() != null ? ret.getDocumentos() : List.of();
        try {
            for (DocZip docZip : docZips) {
                DocZipDecodificado decodificado = decoder.decodificar(docZip);
                String sha = Sha256.hex(decodificado.xml());
                BlobRef ref = blobs.gravar(ChavesBlobPull.documento(fonte, ambiente, cnpj, decodificado.nsu(), sha),
                        decodificado.xml(), sha);
                documentos.add(new Documento(idDe(cnpj, decodificado.nsu()), fonte, cnpj, MODELO_NFE,
                        decodificado.nsu(), agora, decodificado.tipo().name(), Optional.empty(),
                        Optional.of(ref.chave())));
            }
        } catch (RuntimeException e) {
            return semAvanco(cursor, entrada, TEMPORARIO, refEnvelope);
        }

        return new BuscarLoteResultado(List.copyOf(documentos), CursorNsu.de(ultNsu), ultNsu,
                ultNsu.ehMenorQue(maxNsu), desfecho, refEnvelope);
    }

    /** Nenhum documento, cursor e ultNSU de entrada (inv. 1 e 5). */
    private static BuscarLoteResultado semAvanco(Cursor cursor, Nsu entrada, Desfecho desfecho,
                                                 Optional<BlobRef> envelope) {
        return new BuscarLoteResultado(List.of(), cursor, entrada, false, desfecho, envelope);
    }

    @Override
    public BuscarUmResultado buscarUm(FonteId fonte, Cnpj cnpj, Nsu nsu) {
        return new BuscarUmResultado(Optional.empty(), new Desfecho.IndisponivelDefinitivo("consNSU ainda não suportado"));
    }

    private static Nsu nsuDe(String valor) {
        return new Nsu(Long.parseLong(valor.strip()));
    }

    /** Id determinístico: o mesmo NSU da mesma fonte/ambiente/CNPJ sempre dá o mesmo documento. */
    private UUID idDe(Cnpj cnpj, Nsu nsu) {
        String base = fonte.id() + "|" + ambiente.name() + "|" + cnpj.valor() + "|" + nsu.valor();
        return UUID.nameUUIDFromBytes(base.getBytes(StandardCharsets.UTF_8));
    }
}
