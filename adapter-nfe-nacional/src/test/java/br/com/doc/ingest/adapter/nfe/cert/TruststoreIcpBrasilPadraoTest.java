package br.com.doc.ingest.adapter.nfe.cert;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TruststoreIcpBrasilPadraoTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void indiceVersionadoBateComOsArquivosEHaPeloMenosUmaRaizVigente() throws Exception {
        // carregar() confere o sha256 de cada arquivo contra o indice.txt do classpath
        KeyStore ks = TruststoreIcpBrasil.carregar(Map.of());

        int vigentes = 0;
        int total = 0;
        for (String alias : Collections.list(ks.aliases())) {
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            total++;
            assertThat(cert.getBasicConstraints()).as("CA: %s", alias).isGreaterThanOrEqualTo(0);
            if (!cert.getNotAfter().before(Date.from(RELOGIO.instant()))
                    && !cert.getNotBefore().after(Date.from(RELOGIO.instant()))) {
                vigentes++;
            }
        }
        assertThat(total).isGreaterThanOrEqualTo(2);
        assertThat(vigentes).isGreaterThanOrEqualTo(1);
    }
}
