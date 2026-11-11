package org.example.quantflowbackend.domain.recommendation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisInputId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(name = "evidence_id")
    private Long evidenceId;

    public AnalysisInputId(Long analysisId, Long evidenceId) {
        this.analysisId = Objects.requireNonNull(analysisId, "분석 ID는 필수입니다.");
        this.evidenceId = Objects.requireNonNull(evidenceId, "자료 ID는 필수입니다.");
    }
}
