package dev.simonverhoeven.ssss.model;

import java.util.List;

public record OpenAIRequest(
    String model,
    List<OpenAIMessage> messages,
    double temperature,
    int max_tokens
) {
    public OpenAIRequest {
        messages = List.copyOf(messages);
    }
}
