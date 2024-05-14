package dev.simonverhoeven.ssss.model;

public record OpenAIMessage(
        String role,
        String content
) {}
