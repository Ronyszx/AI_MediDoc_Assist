package com.mediassist.platform.documentqa.application;

public interface DocumentEvidenceSettings {
    boolean isEnabled();
    int getMaxItems();
    int getMaxPassages();
    int getMaxPassageCharacters();
    int getMaxAnswerCharacters();
}
