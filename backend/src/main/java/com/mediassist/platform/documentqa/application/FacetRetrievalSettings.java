package com.mediassist.platform.documentqa.application;

public interface FacetRetrievalSettings {
    boolean isEnabled();
    int getMaxFacets();
    int getOriginalCandidateCount();
    int getFacetCandidateCount();
    int getFusionConstant();
    int getNominationRankLimit();
    int getMaxEvidenceTokens();
    int getPlannerOutputTokens();
}
