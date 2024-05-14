package dev.simonverhoeven.ssss.model;

import java.util.List;

public record OpenAIResponse (
    String id,
    String object,
    long created,
    String model,
    List<OpenAIChoice> choices,
    OpenAIUsage usage,
    String system_fingerprint) {
    public OpenAIResponse {
        choices = List.copyOf(choices);
    }
}