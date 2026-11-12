package org.example.quantflowbackend.domain.broker.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class BrokerSecretCipherTest {
    private final BrokerSecretCipher cipher = new BrokerSecretCipher(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void ciphertextUsesRandomNonceAndCanBeDecrypted() {
        String first = cipher.encrypt("client-secret");
        String second = cipher.encrypt("client-secret");
        assertNotEquals(first, second);
        assertFalse(first.contains("client-secret"));
        assertEquals("client-secret", cipher.decrypt(first));
    }

    @Test
    void alteredCiphertextAndWrongKeyAreRejected() {
        String value = cipher.encrypt("client-secret");
        byte[] bytes = Base64.getDecoder().decode(value.substring(3));
        bytes[15] ^= 1;
        assertThrows(IllegalStateException.class,
                () -> cipher.decrypt("v1:" + Base64.getEncoder().encodeToString(bytes)));
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        BrokerSecretCipher other = new BrokerSecretCipher(Base64.getEncoder().encodeToString(otherKey));
        assertThrows(IllegalStateException.class, () -> other.decrypt(value));
    }

    @Test
    void fingerprintsAreStableAndSeparatedByPurpose() {
        assertEquals(cipher.fingerprint("account", "123"), cipher.fingerprint("account", "123"));
        assertNotEquals(cipher.fingerprint("account", "123"), cipher.fingerprint("ticket", "123"));
    }

    @Test
    void missingKeyDisablesOnlyBrokerOperations() {
        BrokerSecretCipher missing = new BrokerSecretCipher("");
        assertThrows(ResponseStatusException.class, () -> missing.encrypt("secret"));
        assertThrows(IllegalArgumentException.class, () -> new BrokerSecretCipher("invalid"));
    }
}
