package org.example.quantflowbackend.domain.broker.dto;

/** Internal encrypted credential payload. Never return this record from an API. */
public record BrokerCredentialData(String clientId, String clientSecret, Long accountSeq) {
    @Override public String toString() { return "BrokerCredentialData[REDACTED]"; }
}
