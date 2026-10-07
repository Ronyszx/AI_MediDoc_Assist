package com.mediassist.platform.documentqa.application;

public interface DocumentEvidenceSettings {
    boolean isEnabled();
    boolean isBatchSelectionEnabled();
    int getSelectionBatchSize();
    int getMaxItems();
    int getMaxPassages();
    int getMaxPassageCharacters();
    int getMaxAnswerCharacters();
}
