package com.rafaeldiaz.orquestador_gold_rush_2025.connect;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utilidad de seguridad para firmar peticiones HMAC-SHA256.
 * Requerido por Task 2.1.2 para autenticación en exchanges.
 */
public final class SignatureUtil {

    private static final String ALGORITHM = "HmacSHA256";

    private SignatureUtil() {} // No instanciable

    public static String generateSignature(String apiSecret, String payload) {
        if (apiSecret == null || payload == null) {
            throw new IllegalArgumentException("Secret y Payload son obligatorios");
        }

        try {
            Mac sha256_HMAC = Mac.getInstance(ALGORITHM);
            SecretKeySpec secret_key = new SecretKeySpec(
                    apiSecret.getBytes(StandardCharsets.UTF_8),
                    ALGORITHM
            );
            sha256_HMAC.init(secret_key);

            // Java 25: HexFormat es nativo y eficiente
            return HexFormat.of().formatHex(
                    sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8))
            );

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Error fatal generando firma HMAC", e);
        }
    }
}