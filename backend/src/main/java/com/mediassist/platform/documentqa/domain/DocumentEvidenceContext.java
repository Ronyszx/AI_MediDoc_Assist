package com.mediassist.platform.documentqa.domain;

import java.util.List;

public record DocumentEvidenceContext(List<DocumentEvidenceItem> passages, boolean limited) {
    public DocumentEvidenceContext {
        passages = List.copyOf(passages);
    }
}
