package com.mediassist.platform.documentqa.application;

public interface DocumentQaRetrievalSettings {

    boolean isDiversityEnabled();

    int getCandidateCount();

    double getRelevanceWeight();
}
