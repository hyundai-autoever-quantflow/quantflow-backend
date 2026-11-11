package org.example.quantflowbackend.domain.automation.domain;

public enum AutomationRuntimeState {
    IDLE,
    SCHEDULED,
    ANALYZING,
    WATCHING,
    HOLDING,
    STOPPING,
    BLOCKED
}
