package org.example.quantflowbackend.domain.trading.domain;

public enum OrderEventType {
    SUBMIT,
    ACK,
    FILL,
    CANCEL_REQUEST,
    CANCEL_ACK,
    RECONCILE
}
