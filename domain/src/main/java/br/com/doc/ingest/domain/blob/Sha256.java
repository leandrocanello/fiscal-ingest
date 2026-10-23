package br.com.doc.ingest.domain.blob;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Utilitário puro de SHA-256 em hexadecimal minúsculo (64 caracteres). */
public final class Sha256 {

    private Sha256() {
    }

    public static String hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível no JDK", e);
        }
    }
}
