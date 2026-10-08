package com.mediassist.platform.documentqa.application;

public class QuestionPlanningException extends RuntimeException {
    public QuestionPlanningException(String message) {
        super(message);
    }

    public QuestionPlanningException(String message, Throwable cause) {
        super(message, cause);
    }
}
