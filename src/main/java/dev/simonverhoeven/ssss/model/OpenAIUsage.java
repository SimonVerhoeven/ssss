package dev.simonverhoeven.ssss.model;

public record OpenAIUsage(
        int prompt_tokens,
        int completion_tokens,
        int total_tokens
) {
}
