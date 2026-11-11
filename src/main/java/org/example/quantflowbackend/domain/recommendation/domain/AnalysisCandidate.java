package org.example.quantflowbackend.domain.recommendation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.quantflowbackend.domain.stock.domain.Stock;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "analysis_candidates",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_analysis_candidates_1",
                        columnNames = {"analysis_id", "stock_id"}
                ),
                @UniqueConstraint(
                        name = "uk_analysis_candidates_2",
                        columnNames = {"analysis_id", "rank"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "candidate_id")
    private Long candidateId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "rank", nullable = false)
    private Short rank;

    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_ids", nullable = false, columnDefinition = "jsonb")
    private List<Long> evidenceIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> validationResult;

    @Builder
    private AnalysisCandidate(
            Analysis analysis,
            Stock stock,
            Short rank,
            BigDecimal score,
            String reason,
            List<Long> evidenceIds,
            Map<String, Object> validationResult
    ) {
        this.analysis = Objects.requireNonNull(analysis, "필수 값입니다: analysis");
        this.stock = Objects.requireNonNull(stock, "필수 값입니다: stock");
        this.rank = Objects.requireNonNull(rank, "필수 값입니다: rank");
        this.score = Objects.requireNonNull(score, "필수 값입니다: score");
        this.reason = Objects.requireNonNull(reason, "필수 값입니다: reason");
        this.evidenceIds = new ArrayList<>(Objects.requireNonNull(evidenceIds, "필수 값입니다: evidenceIds"));
        this.validationResult = new HashMap<>(Objects.requireNonNull(validationResult, "필수 값입니다: validationResult"));
    }

    public static AnalysisCandidate create(
            Analysis analysis,
            Stock stock,
            Short rank,
            BigDecimal score,
            String reason,
            List<Long> evidenceIds,
            Map<String, Object> validationResult
    ) {
        return AnalysisCandidate.builder()
                .analysis(analysis)
                .stock(stock)
                .rank(rank)
                .score(score)
                .reason(reason)
                .evidenceIds(evidenceIds)
                .validationResult(validationResult)
                .build();
    }
}
