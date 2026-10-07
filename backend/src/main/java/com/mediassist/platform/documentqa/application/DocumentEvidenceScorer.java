package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentqa.domain.DocumentEvidenceAssessment;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import java.util.List;

public interface DocumentEvidenceScorer {
    DocumentEvidenceAssessment assessEvidence(String question, List<DocumentEvidenceItem> passages);
}
