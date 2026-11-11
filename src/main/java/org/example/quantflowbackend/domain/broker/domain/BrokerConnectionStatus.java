package org.example.quantflowbackend.domain.broker.domain;

public enum BrokerConnectionStatus {
    VERIFYING,
    CONNECTED,
    RECONNECT_REQUIRED,
    DISCONNECTING,
    DISCONNECTED,
    FAILED
}
