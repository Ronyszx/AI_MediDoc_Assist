package com.mediassist.platform.documentqa.domain;

import java.util.List;

public record DocumentEvidenceSelection(String modelName, List<String> passageIds, boolean limited) {
    public DocumentEvidenceSelection {
        passageIds = List.copyOf(passageIds);
    }

    public DocumentEvidenceSelection(String modelName, List<String> passageIds) {
        this(modelName, passageIds, false);
    }
}
