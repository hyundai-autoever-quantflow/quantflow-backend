package org.example.quantflowbackend.domain.trading.domain;

public enum OrderStatus {
    CREATED,
    SUBMITTING,
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCEL_PENDING,
    CANCELED,
    REJECTED,
    UNKNOWN
}
