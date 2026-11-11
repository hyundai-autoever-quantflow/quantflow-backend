package org.example.quantflowbackend.domain.recommendation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.quantflowbackend.domain.evidence.domain.EvidenceSnapshot;

import java.util.Objects;

@Getter
@Entity
@Table(
        name = "analysis_inputs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_inputs_order",
                columnNames = {"analysis_id", "input_order"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisInput {
    @EmbeddedId
    private AnalysisInputId id;

    @MapsId("analysisId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @MapsId("evidenceId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false)
    private EvidenceSnapshot evidence;

    @Column(name = "input_order", nullable = false)
    private Integer inputOrder;

    @Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
    private String inputText;

    @Builder
    private AnalysisInput(
            Analysis analysis,
            EvidenceSnapshot evidence,
            Integer inputOrder,
            String inputText
    ) {
        this.analysis = Objects.requireNonNull(analysis, "분석은 필수입니다.");
        this.evidence = Objects.requireNonNull(evidence, "자료는 필수입니다.");
        this.id = new AnalysisInputId(analysis.getAnalysisId(), evidence.getEvidenceId());
        this.inputOrder = Objects.requireNonNull(inputOrder, "입력 순서는 필수입니다.");
        this.inputText = Objects.requireNonNull(inputText, "입력 텍스트는 필수입니다.");
    }

    public static AnalysisInput create(
            Analysis analysis,
            EvidenceSnapshot evidence,
            Integer inputOrder,
            String inputText
    ) {
        return AnalysisInput.builder()
                .analysis(analysis)
                .evidence(evidence)
                .inputOrder(inputOrder)
                .inputText(inputText)
                .build();
    }
}
