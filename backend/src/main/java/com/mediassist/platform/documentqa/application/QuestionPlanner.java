package com.mediassist.platform.documentqa.application;

import com.mediassist.platform.documentqa.domain.QuestionPlan;

public interface QuestionPlanner {
    QuestionPlan plan(String question);
}
