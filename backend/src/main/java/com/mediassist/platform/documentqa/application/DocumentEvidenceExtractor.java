package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentqa.domain.DocumentEvidenceItem;
import com.mediassist.platform.documentqa.domain.DocumentEvidenceSelection;
import java.util.List;

public interface DocumentEvidenceExtractor {
    DocumentEvidenceSelection selectEvidence(String question, List<DocumentEvidenceItem> passages);
}
