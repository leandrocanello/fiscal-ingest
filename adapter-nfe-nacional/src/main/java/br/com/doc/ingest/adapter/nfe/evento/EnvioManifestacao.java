package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.adapter.nfe.ChavesBlobPull;
import br.com.doc.ingest.adapter.nfe.cert.CertificadoCarregado;
import br.com.doc.ingest.adapter.nfe.mapping.CStatEventoMapper;
import br.com.doc.ingest.adapter.nfe.mapping.TradutorRespostaEvento;
import br.com.doc.ingest.adapter.nfe.soap.TipoAmbiente;
import br.com.doc.ingest.adapter.nfe.transporte.ResultadoTransporte;
import br.com.doc.ingest.adapter.nfe.transporte.TransporteSoap;
import br.com.doc.ingest.domain.adapter.Desfecho;
import br.com.doc.ingest.domain.adapter.ManifestarResultado;
import br.com.doc.ingest.domain.adapter.TipoManifestacao;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.FonteId;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * Envia a manifestação ao NFeRecepcaoEvento: monta e assina o evento, envia, grava a resposta bruta antes de
 * interpretar (inv. 5) e traduz para {@link Desfecho} (inv. 4). O evento e a resposta não saem daqui (inv. 3).
 *
 * <p>O {@code idLote} vem do relógio em milissegundos (13 dígitos, cabe nos 15 do leiaute). Ele só identifica o lote;
 * reenviar a mesma ciência é idempotente pelo {@code 573} (decisão E2-S13 D1).
 */
public final class EnvioManifestacao {

    private static final int N_SEQ_EVENTO = 1;
    private static final ZoneId FUSO_EVENTO = ZoneId.of("America/Sao_Paulo");
    private static final Desfecho TEMPORARIO = new Desfecho.IndisponivelTemporario(Duration.ofMinutes(5));

    private final FonteId fonte;
    private final TipoAmbiente ambiente;
    private final URI endpoint;
    private final BlobStore blobs;
    private final Clock relogio;
    private final EventoManifestacao evento;
    private final TradutorRespostaEvento tradutor = new TradutorRespostaEvento(new CStatEventoMapper());

    public EnvioManifestacao(FonteId fonte, TipoAmbiente ambiente, URI endpoint, BlobStore blobs, Clock relogio) {
        this.fonte = Objects.requireNonNull(fonte, "fonte");
        this.ambiente = Objects.requireNonNull(ambiente, "ambiente");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.blobs = Objects.requireNonNull(blobs, "blobs");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        this.evento = new EventoManifestacao(relogio, FUSO_EVENTO, AlgoritmoAssinatura.PADRAO);
    }

    public ManifestarResultado enviar(TransporteSoap transporte, CertificadoCarregado certificado, Cnpj autor,
                                      String chave, TipoManifestacao tipo) {
        byte[] assinado = evento.assinado(ambiente, autor, chave, tipo, N_SEQ_EVENTO, certificado);
        ResultadoTransporte resultado = transporte.enviar(endpoint, ContratoRecepcaoEvento.ACTION,
                EnvelopeEnvEvento.soap12(assinado, relogio.millis()));
        if (!(resultado instanceof ResultadoTransporte.RespostaHttp http)) {
            return new ManifestarResultado(tradutor.deTransporte(resultado).orElseThrow(), Optional.empty());
        }

        // inv. 5: todo corpo HTTP é guardado antes de qualquer interpretação
        byte[] corpo = http.corpo();
        String sha = Sha256.hex(corpo);
        BlobRef envelope;
        try {
            envelope = blobs.gravar(ChavesBlobPull.envioEvento(fonte, ambiente, autor, relogio.instant(), sha), corpo, sha);
        } catch (RuntimeException e) {
            return new ManifestarResultado(TEMPORARIO, Optional.empty());
        }

        var enviado = new TradutorRespostaEvento.EventoEnviado(chave, ContratoEvento.tpEvento(tipo), N_SEQ_EVENTO);
        Desfecho desfecho = tradutor.deTransporte(http)
                .orElseGet(() -> tradutor.deLeitura(LeitorRetEnvEvento.ler(corpo), enviado));
        return new ManifestarResultado(desfecho, Optional.of(envelope));
    }
}
