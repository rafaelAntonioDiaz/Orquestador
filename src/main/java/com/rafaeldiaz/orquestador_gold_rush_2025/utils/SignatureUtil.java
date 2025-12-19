package com.rafaeldiaz.orquestador_gold_rush_2025.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64; // Importante para KuCoin

public class SignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    // Cache de Mac para velocidad (ThreadLocal)
    private static final ThreadLocal<Mac> MAC_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return Mac.getInstance(HMAC_SHA256);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("HMAC-SHA256 no disponible", e);
        }
    });

    /**
     * Genera firma en formato HEX (Para Binance, Bybit, MEXC).
     */
    public static String generateSignature(String secret, String message) {
        byte[] bytes = calculateHmacBytes(secret, message);
        return bytesToHex(bytes);
    }

    /**
     * Genera firma en formato BASE64 (Para KuCoin).
     */
    public static String generateSignatureBase64(String secret, String message) {
        byte[] bytes = calculateHmacBytes(secret, message);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Método privado centralizado para el cálculo matemático
    private static byte[] calculateHmacBytes(String secret, String message) {
        try {
            Mac sha256_HMAC = MAC_CACHE.get();
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            sha256_HMAC.init(secret_key);
            return sha256_HMAC.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Clave inválida", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}