package org.example.quantflowbackend.domain.broker.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class BrokerSecretCipher {
    private static final byte[] AAD = "quantflow:broker:v1".getBytes(StandardCharsets.UTF_8);
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public BrokerSecretCipher(@Value("${app.broker.encryption-key-base64:}") String encodedKey) {
        if (encodedKey.isBlank()) {
            key = null; // Other APIs can start before broker integration is configured.
        } else {
            try {
                key = Base64.getDecoder().decode(encodedKey);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("BROKER_ENCRYPTION_KEY_BASE64는 Base64여야 합니다.");
            }
            if (key.length != 32) throw new IllegalArgumentException("증권사 암호화 키는 32바이트여야 합니다.");
        }
    }

    public String encrypt(String value) {
        requireKey();
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("증권사 인증정보를 암호화할 수 없습니다.");
        }
    }

    public String decrypt(String value) {
        requireKey();
        try {
            if (!value.startsWith("v1:")) throw new IllegalArgumentException();
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value.substring(3)));
            byte[] nonce = new byte[12];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new IllegalStateException("증권사 인증정보를 복호화할 수 없습니다.");
        }
    }

    public String fingerprint(String purpose, String value) {
        requireKey();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            // Domain separation prevents using an account hash as a ticket/cache identifier.
            return HexFormat.of().formatHex(mac.doFinal((purpose + "\0" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("증권사 식별값을 생성할 수 없습니다.");
        }
    }

    private void requireKey() {
        if (key == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "증권사 인증정보 암호화 키가 설정되지 않았습니다.");
    }
}
