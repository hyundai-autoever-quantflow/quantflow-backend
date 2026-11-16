package org.example.quantflowbackend.global.ai;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OpenAiResponseClient {
    private final OpenAIClient openAIClient;

    @Value("${app.openai.model}")
    private String model;

    public String generate(String instructions, String input) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .instructions(instructions)
                .input(input)
                .build();

        Response response = openAIClient.responses().create(params);

        String text = response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining("\n"));

        if (text.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI의 텍스트 응답을 받지 못했습니다."
            );
        }

        return text;
    }

}
