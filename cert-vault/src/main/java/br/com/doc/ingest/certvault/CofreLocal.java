package br.com.doc.ingest.certvault;

import br.com.doc.ingest.certvault.ResultadoValidacao.PfxValidado;
import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.blob.BlobRef;
import br.com.doc.ingest.domain.blob.BlobStore;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.cofre.CertificadoGuardado;
import br.com.doc.ingest.domain.cofre.CofreDeCertificados;
import br.com.doc.ingest.domain.cofre.FalhaAbertura;
import br.com.doc.ingest.domain.cofre.NovoCertificado;
import br.com.doc.ingest.domain.cofre.PedidoGuarda;
import br.com.doc.ingest.domain.cofre.RepositorioCertificados;
import br.com.doc.ingest.domain.cofre.ResultadoAbertura;
import br.com.doc.ingest.domain.cofre.ResultadoGuarda;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link CofreDeCertificados} local com envelope encryption (CLAUDE.md §7.4, DA-35).
 *
 * <p>Guardar: valida o .pfx → cifra o .pfx e a senha com a mesma DEK nova, cada um com a sua AAD
 * ({@code tenant|titular|fingerprint|PFX ou SENHA}) → grava os dois envelopes em
 * {@code cofre/{tenant}/{fingerprint}/{pfx|senha}-{sha256}.env} → só então registra os metadados (inv. 5 e 7).
 * Em reenvio, o repositório devolve o registro existente; os envelopes novos ficam órfãos, mas são só cifrado.
 *
 * <p>Abrir: busca o registro do tenant → vencido devolve {@code Vencido} sem ler o blob store → decifra o .pfx e
 * a senha com a AAD reconstruída do registro, só em memória → devolve uma {@link Credencial} nova a cada chamada.
 * Blob ausente, trocado ou com KEK desconhecida vira {@code Indisponivel}, sem causa nem conteúdo.
 *
 * <p>As cópias claras do .pfx, da senha e da DEK feitas aqui são zeradas em {@code finally}. A senha vira
 * UTF-8 por {@link CharsetEncoder}, sem passar por {@code String}. Cópias internas do JDK (a chave em
 * {@code SecretKeySpec}, o {@code KeyStore} do validador) não são zeráveis: a zeragem reduz a exposição,
 * sem eliminá-la.
 */
public final class CofreLocal implements CofreDeCertificados {

    private static final String PARTE_PFX = "PFX";
    private static final String PARTE_SENHA = "SENHA";

    private final ValidadorPfx validador;
    private final EnvelopeCifrado envelope;
    private final ProvedorKek keks;
    private final BlobStore blobs;
    private final RepositorioCertificados repositorio;
    private final Clock relogio;

    public CofreLocal(ValidadorPfx validador, EnvelopeCifrado envelope, ProvedorKek keks, BlobStore blobs,
                      RepositorioCertificados repositorio, Clock relogio) {
        this.validador = Objects.requireNonNull(validador, "validador");
        this.envelope = Objects.requireNonNull(envelope, "envelope");
        this.keks = Objects.requireNonNull(keks, "keks");
        this.blobs = Objects.requireNonNull(blobs, "blobs");
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
    }

    @Override
    public ResultadoGuarda guardar(PedidoGuarda pedido) {
        Objects.requireNonNull(pedido, "pedido");
        byte[] pfx = pedido.pfx();
        char[] senha = pedido.senha();
        byte[] senhaUtf8 = null;
        byte[] dek = null;
        try {
            ResultadoValidacao validacao = validador.validar(pfx, senha, pedido.cnpjTitular());
            if (validacao instanceof ResultadoValidacao.Recusado recusado) {
                return new ResultadoGuarda.Recusado(recusado.motivo());
            }
            PfxValidado validado = (PfxValidado) validacao;
            senhaUtf8 = utf8(senha);
            dek = envelope.novaDek();
            Kek kek = keks.atual();
            BlobRef pfxCifrado = gravar(pedido, validado.fingerprint(), PARTE_PFX,
                    envelope.cifrar(pfx, dek, kek, aad(pedido, validado.fingerprint(), PARTE_PFX)));
            BlobRef senhaCifrada = gravar(pedido, validado.fingerprint(), PARTE_SENHA,
                    envelope.cifrar(senhaUtf8, dek, kek, aad(pedido, validado.fingerprint(), PARTE_SENHA)));
            return new ResultadoGuarda.Guardado(repositorio.registrarSeAusente(new NovoCertificado(
                    pedido.tenantId(), pedido.titularEmpresaId(), pedido.cnpjTitular(), validado.fingerprint(),
                    pfxCifrado, senhaCifrada, validado.validoDe(), validado.validoAte(), relogio.instant(),
                    pedido.enviadoPor())));
        } finally {
            Arrays.fill(pfx, (byte) 0);
            Arrays.fill(senha, '\0');
            zerar(senhaUtf8);
            zerar(dek);
        }
    }

    @Override
    public ResultadoAbertura abrir(long tenantId, long certificadoId) {
        Optional<CertificadoGuardado> encontrado = repositorio.buscar(tenantId, certificadoId);
        if (encontrado.isEmpty()) {
            return new ResultadoAbertura.NaoEncontrado();
        }
        CertificadoGuardado certificado = encontrado.get();
        if (relogio.instant().isAfter(certificado.validoAte())) {
            return new ResultadoAbertura.Vencido(certificado.validoAte());
        }
        byte[] pfx = null;
        byte[] senhaUtf8 = null;
        char[] senha = null;
        try {
            ResultadoDecifra decifraPfx = decifrar(certificado, PARTE_PFX, certificado.pfxCifrado());
            if (decifraPfx instanceof ResultadoDecifra.Falha falha) {
                return new ResultadoAbertura.Indisponivel(falha.motivo());
            }
            pfx = ((ResultadoDecifra.Decifrado) decifraPfx).conteudo();
            ResultadoDecifra decifraSenha = decifrar(certificado, PARTE_SENHA, certificado.senhaCifrada());
            if (decifraSenha instanceof ResultadoDecifra.Falha falha) {
                return new ResultadoAbertura.Indisponivel(falha.motivo());
            }
            senhaUtf8 = ((ResultadoDecifra.Decifrado) decifraSenha).conteudo();
            senha = utf16(senhaUtf8);
            if (senha == null) {
                return new ResultadoAbertura.Indisponivel(FalhaAbertura.ENVELOPE_INVALIDO);
            }
            return new ResultadoAbertura.Aberto(new Credencial(pfx, senha));
        } finally {
            zerar(pfx);
            zerar(senhaUtf8);
            if (senha != null) {
                Arrays.fill(senha, '\0');
            }
        }
    }

    private ResultadoDecifra decifrar(CertificadoGuardado certificado, String parte, BlobRef ref) {
        return blobs.ler(ref)
                .map(cifrado -> envelope.decifrar(cifrado, aad(certificado.tenantId(),
                        certificado.titularEmpresaId(), certificado.fingerprint(), parte), keks))
                .orElseGet(() -> new ResultadoDecifra.Falha(FalhaAbertura.BLOB_AUSENTE));
    }

    private BlobRef gravar(PedidoGuarda pedido, String fingerprint, String parte, byte[] cifrado) {
        String sha256 = Sha256.hex(cifrado);
        String chave = "cofre/" + pedido.tenantId() + "/" + fingerprint + "/"
                + parte.toLowerCase(Locale.ROOT) + "-" + sha256 + ".env";
        return blobs.gravar(chave, cifrado, sha256);
    }

    /** Contexto autenticado do envelope: trocar blobs entre certificados ou partes não decifra (DA-35). */
    private static byte[] aad(PedidoGuarda pedido, String fingerprint, String parte) {
        return aad(pedido.tenantId(), pedido.titularEmpresaId(), fingerprint, parte);
    }

    private static byte[] aad(long tenantId, long titularEmpresaId, String fingerprint, String parte) {
        return (tenantId + "|" + titularEmpresaId + "|" + fingerprint + "|" + parte).getBytes(StandardCharsets.UTF_8);
    }

    /** UTF-8 exato da senha, sem {@code String}; o buffer intermediário é zerado. */
    private static byte[] utf8(char[] senha) {
        CharsetEncoder codificador = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer buffer = null;
        try {
            buffer = codificador.encode(CharBuffer.wrap(senha));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            // sem causa: o erro de codificação poderia apontar a posição na senha (inv. 13)
            throw new IllegalArgumentException("senha com caractere inválido em UTF-16");
        } finally {
            if (buffer != null && buffer.hasArray()) {
                Arrays.fill(buffer.array(), (byte) 0);
            }
        }
    }

    /** Senha de volta a {@code char[]}, sem {@code String}; vazio se o UTF-8 for inválido. O buffer é zerado. */
    private static char[] utf16(byte[] utf8) {
        CharsetDecoder decodificador = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer buffer = null;
        try {
            buffer = decodificador.decode(ByteBuffer.wrap(utf8));
            char[] senha = new char[buffer.remaining()];
            buffer.get(senha);
            return senha;
        } catch (CharacterCodingException e) {
            return null;
        } finally {
            if (buffer != null && buffer.hasArray()) {
                Arrays.fill(buffer.array(), '\0');
            }
        }
    }

    private static void zerar(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
