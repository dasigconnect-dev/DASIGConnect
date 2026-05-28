package com.dasigconnect.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AES-256-GCM encryption for the Facebook Page Access Token stored at rest.
 *
 * Key: 32-byte SHA-256 hash of FACEBOOK_APP_SECRET.
 * Storage format: base64(iv) + ":" + base64(ciphertext+tag)
 */
@Service
public class TokenEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final String appSecret;

    public TokenEncryptionService(@Value("${app.facebook.app-secret:}") String appSecret) {
        this.appSecret = appSecret;
    }

    public boolean isConfigured() {
        return appSecret != null && !appSecret.isBlank();
    }

    public String encryptToken(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            Base64.Encoder enc = Base64.getEncoder();
            return enc.encodeToString(iv) + ":" + enc.encodeToString(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException("Token encryption failed", ex);
        }
    }

    public String decryptToken(String stored) {
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid stored token format");
            }
            Base64.Decoder dec = Base64.getDecoder();
            byte[] iv = dec.decode(parts[0]);
            byte[] ciphertext = dec.decode(parts[1]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Token decryption failed", ex);
        }
    }

    private SecretKeySpec buildKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(appSecret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
