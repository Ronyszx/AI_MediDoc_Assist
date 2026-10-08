package com.mediassist.platform.documentqa.domain;

import java.util.List;

public record DocumentEvidenceSelection(String modelName, List<String> passageIds) {
    public DocumentEvidenceSelection {
        passageIds = List.copyOf(passageIds);
    }
}
