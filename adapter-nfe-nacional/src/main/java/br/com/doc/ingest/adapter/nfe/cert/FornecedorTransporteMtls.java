package br.com.doc.ingest.adapter.nfe.cert;

import br.com.doc.ingest.adapter.nfe.transporte.FornecedorTransporte;
import br.com.doc.ingest.adapter.nfe.transporte.TransporteHttpJdk;
import br.com.doc.ingest.adapter.nfe.transporte.TransporteSoap;
import br.com.doc.ingest.domain.adapter.Credencial;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

/**
 * {@link FornecedorTransporte} de produção: o {@link javax.net.ssl.SSLContext} mTLS vem do {@link CacheSslContext}
 * (o .pfx não é reaberto a cada autenticação) e vira um {@link TransporteHttpJdk}. Não retém a {@link Credencial};
 * .pfx inválido sai como {@link CertificadoInvalidoException}, sem senha na mensagem (inv. 13).
 */
public final class FornecedorTransporteMtls implements FornecedorTransporte {

    private final CacheSslContext cache;
    private final Duration timeout;
    private final long limiteBytes;

    public FornecedorTransporteMtls(CacheSslContext cache, Duration timeout, long limiteBytes) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.limiteBytes = limiteBytes;
    }

    @Override
    public TransporteSoap para(Credencial credencial) {
        HttpClient cliente = HttpClient.newBuilder()
                .sslContext(cache.obter(credencial))
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        return new TransporteHttpJdk(cliente, timeout, limiteBytes);
    }
}
