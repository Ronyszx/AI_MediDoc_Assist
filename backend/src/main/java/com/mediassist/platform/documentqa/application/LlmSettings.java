package com.mediassist.platform.documentqa.application;

public interface LlmSettings {

    String getModelName();

    double getTemperature();

    int getMaxOutputTokens();
}
