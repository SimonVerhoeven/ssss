package dev.simonverhoeven.ssss.model;

public record OpenAIChoice(
        OpenAIMessage message,
        int index,
        Integer logprobs,
        String finish_reason
) {}
