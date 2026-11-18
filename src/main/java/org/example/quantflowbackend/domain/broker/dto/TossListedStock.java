package org.example.quantflowbackend.domain.broker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossListedStock(

        @NotBlank
        String symbol,

        @NotBlank
        String name,

        @NotBlank
        String securityType,

        @NotNull
        Boolean isCommonShare,

        String isinCode
) {
}
