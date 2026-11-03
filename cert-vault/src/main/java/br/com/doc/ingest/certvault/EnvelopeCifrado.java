package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.cofre.FalhaAbertura;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Envelope encryption com AES-256-GCM (CLAUDE.md §7.4, DA-35). Formato, autocontido:
 * {@code versão(1) | tamanho do kekId(1) | kekId | nonce(12) + DEK embrulhada(48) | nonce(12) | cifrado + tag(16)}.
 *
 * <p>O cabeçalho (versão e kekId) e a AAD autenticam as duas camadas: alterar qualquer byte ou
 * trocar o envelope de certificado ou de parte não decifra.
 *
 * <p>As cópias claras da DEK e da KEK feitas aqui são zeradas em {@code finally}. O JDK copia a
 * chave dentro de {@link SecretKeySpec}, e essa cópia não é zerável: a zeragem reduz o tempo de
 * exposição em memória, sem eliminá-la.
 */
public final class EnvelopeCifrado {

    private static final byte VERSAO = 1;
    private static final String TRANSFORMACAO = "AES/GCM/NoPadding";
    private static final String ALGORITMO_CHAVE = "AES";
    private static final int TAMANHO_DEK = 32;
    private static final int TAMANHO_NONCE = 12;
    private static final int TAMANHO_TAG = 16;
    private static final int TAMANHO_DEK_EMBRULHADA = TAMANHO_DEK + TAMANHO_TAG;
    private static final int MAX_KEK_ID = 255;
    private static final int TAMANHO_FIXO_CABECALHO = 2;
    private static final int TAMANHO_APOS_KEK_ID =
            TAMANHO_NONCE + TAMANHO_DEK_EMBRULHADA + TAMANHO_NONCE + TAMANHO_TAG;

    private final SecureRandom aleatorio = new SecureRandom();

    /** @return DEK nova de 256 bits; quem chama deve zerá-la após o uso */
    public byte[] novaDek() {
        byte[] dek = new byte[TAMANHO_DEK];
        aleatorio.nextBytes(dek);
        return dek;
    }

    /**
     * Cifra o conteúdo com a DEK e embrulha a DEK com a KEK, cada camada com o seu nonce aleatório.
     *
     * @param aad contexto que precisa ser igual ao decifrar
     */
    public byte[] cifrar(byte[] conteudo, byte[] dek, Kek kek, byte[] aad) {
        if (dek == null || dek.length != TAMANHO_DEK) {
            throw new IllegalArgumentException("a DEK deve ter " + TAMANHO_DEK + " bytes");
        }
        byte[] cabecalho = cabecalho(kek.id());
        byte[] chaveKek = kek.chave();
        try {
            byte[] nonceKek = novoNonce();
            byte[] dekEmbrulhada = aplicar(Cipher.ENCRYPT_MODE, chaveKek, nonceKek, cabecalho, aad, dek);
            byte[] nonce = novoNonce();
            byte[] cifrado = aplicar(Cipher.ENCRYPT_MODE, dek, nonce, cabecalho, aad, conteudo);
            return ByteBuffer.allocate(cabecalho.length + TAMANHO_NONCE + dekEmbrulhada.length
                            + TAMANHO_NONCE + cifrado.length)
                    .put(cabecalho).put(nonceKek).put(dekEmbrulhada).put(nonce).put(cifrado)
                    .array();
        } catch (GeneralSecurityException e) {
            // Sem causa encadeada: nada do que veio do provedor de criptografia sai do cofre.
            throw new IllegalStateException("falha ao cifrar com " + TRANSFORMACAO);
        } finally {
            Arrays.fill(chaveKek, (byte) 0);
        }
    }

    /** Nunca lança por causa do envelope: a falha volta tipada e sem causa encadeada. */
    public ResultadoDecifra decifrar(byte[] envelope, byte[] aad, ProvedorKek keks) {
        if (envelope.length < TAMANHO_FIXO_CABECALHO || envelope[0] != VERSAO) {
            return new ResultadoDecifra.Falha(FalhaAbertura.ENVELOPE_INVALIDO);
        }
        int tamanhoKekId = Byte.toUnsignedInt(envelope[1]);
        int tamanhoCabecalho = TAMANHO_FIXO_CABECALHO + tamanhoKekId;
        if (envelope.length < tamanhoCabecalho + TAMANHO_APOS_KEK_ID) {
            return new ResultadoDecifra.Falha(FalhaAbertura.ENVELOPE_INVALIDO);
        }
        String kekId = new String(envelope, TAMANHO_FIXO_CABECALHO, tamanhoKekId, StandardCharsets.UTF_8);
        return keks.porId(kekId)
                .map(kek -> abrir(envelope, tamanhoCabecalho, aad, kek))
                .orElseGet(() -> new ResultadoDecifra.Falha(FalhaAbertura.KEK_DESCONHECIDA));
    }

    private ResultadoDecifra abrir(byte[] envelope, int tamanhoCabecalho, byte[] aad, Kek kek) {
        ByteBuffer corpo = ByteBuffer.wrap(envelope, tamanhoCabecalho, envelope.length - tamanhoCabecalho);
        byte[] cabecalho = Arrays.copyOf(envelope, tamanhoCabecalho);
        byte[] nonceKek = ler(corpo, TAMANHO_NONCE);
        byte[] dekEmbrulhada = ler(corpo, TAMANHO_DEK_EMBRULHADA);
        byte[] nonce = ler(corpo, TAMANHO_NONCE);
        byte[] cifrado = ler(corpo, corpo.remaining());

        byte[] chaveKek = kek.chave();
        byte[] dek = null;
        try {
            dek = aplicar(Cipher.DECRYPT_MODE, chaveKek, nonceKek, cabecalho, aad, dekEmbrulhada);
            return new ResultadoDecifra.Decifrado(
                    aplicar(Cipher.DECRYPT_MODE, dek, nonce, cabecalho, aad, cifrado));
        } catch (GeneralSecurityException e) {
            return new ResultadoDecifra.Falha(FalhaAbertura.ENVELOPE_INVALIDO);
        } finally {
            Arrays.fill(chaveKek, (byte) 0);
            if (dek != null) {
                Arrays.fill(dek, (byte) 0);
            }
        }
    }

    private static byte[] aplicar(int modo, byte[] chave, byte[] nonce, byte[] cabecalho, byte[] aad, byte[] dados)
            throws GeneralSecurityException {
        Cipher cifra = Cipher.getInstance(TRANSFORMACAO);
        cifra.init(modo, new SecretKeySpec(chave, ALGORITMO_CHAVE),
                new GCMParameterSpec(TAMANHO_TAG * Byte.SIZE, nonce));
        cifra.updateAAD(cabecalho);
        cifra.updateAAD(aad);
        return cifra.doFinal(dados);
    }

    private static byte[] cabecalho(String kekId) {
        byte[] id = kekId.getBytes(StandardCharsets.UTF_8);
        if (id.length > MAX_KEK_ID) {
            throw new IllegalArgumentException("o id da KEK deve ter até " + MAX_KEK_ID + " bytes");
        }
        return ByteBuffer.allocate(TAMANHO_FIXO_CABECALHO + id.length)
                .put(VERSAO).put((byte) id.length).put(id)
                .array();
    }

    private byte[] novoNonce() {
        byte[] nonce = new byte[TAMANHO_NONCE];
        aleatorio.nextBytes(nonce);
        return nonce;
    }

    private static byte[] ler(ByteBuffer origem, int tamanho) {
        byte[] destino = new byte[tamanho];
        origem.get(destino);
        return destino;
    }
}
