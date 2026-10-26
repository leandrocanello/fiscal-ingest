package br.com.doc.ingest.adapter.nfe.transporte;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** {@link TransporteSoap} sobre {@code java.net.http}; o {@code HttpClient} (e seu SSLContext) é injetado. */
public final class TransporteHttpJdk implements TransporteSoap {

    private final HttpClient cliente;
    private final Duration timeout;
    private final long limiteBytes;

    public TransporteHttpJdk(HttpClient cliente, Duration timeout, long limiteBytes) {
        this.cliente = Objects.requireNonNull(cliente);
        this.timeout = Objects.requireNonNull(timeout);
        this.limiteBytes = limiteBytes;
    }

    @Override
    public ResultadoTransporte enviar(URI endpoint, String action, byte[] envelope) {
        HttpRequest requisicao = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/soap+xml; charset=utf-8; action=\"" + action + "\"")
                .POST(HttpRequest.BodyPublishers.ofByteArray(envelope))
                .build();
        try {
            HttpResponse<byte[]> resposta = cliente.send(requisicao, HttpResponse.BodyHandlers.ofByteArray());
            return new ResultadoTransporte.RespostaHttp(resposta.statusCode(), resposta.body());
        } catch (IOException e) {
            return new ResultadoTransporte.FalhaTransporte(ResultadoTransporte.Motivo.CONEXAO);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResultadoTransporte.FalhaTransporte(ResultadoTransporte.Motivo.CONEXAO);
        }
    }
}
