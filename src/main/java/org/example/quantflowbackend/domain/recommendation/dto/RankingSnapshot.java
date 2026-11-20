package org.example.quantflowbackend.domain.recommendation.dto;

import org.example.quantflowbackend.domain.broker.dto.TossInvestorRankingResponse;
import org.example.quantflowbackend.domain.broker.dto.TossRankingResponse;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record RankingSnapshot(
        LocalDate rankingDate,
        OffsetDateTime collectedAt,
        TossRankingResponse volume,
        TossInvestorRankingResponse investor
) {}
