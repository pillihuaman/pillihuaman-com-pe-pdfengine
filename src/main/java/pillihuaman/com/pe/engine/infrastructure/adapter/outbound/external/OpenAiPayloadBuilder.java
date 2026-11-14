package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public final class OpenAiPayloadBuilder {

    private OpenAiPayloadBuilder() {
    }

    public static OpenAiRequest buildRequest(final String systemPrompt) {

        // El payload ahora es 100% texto estructurado (NLP / Razonamiento), ya no es Visión.
        final Message userMsg = new Message("user", systemPrompt);

        final Map<String, Object> schema =
                Map.of(
                        "type", "object",
                        "properties",
                        Map.of(
                                "chest", Map.of("type", "number"),
                                "waist", Map.of("type", "number"),
                                "hips", Map.of("type", "number"),
                                "clothingValidation", Map.of("type", "string"),
                                "status", Map.of("type", "string", "enum", List.of("ACCEPTED", "REJECTED"))),
                        "required", List.of("chest", "waist", "hips", "clothingValidation", "status"),
                        "additionalProperties", false);

        final ResponseFormat format =
                new ResponseFormat(
                        "json_schema", Map.of("name", "measurement_result", "schema", schema, "strict", true));

        return new OpenAiRequest("gpt-4o", List.of(userMsg), format);
    }

    public record OpenAiRequest(
            String model,
            List<Message> messages,
            @JsonProperty("response_format") ResponseFormat responseFormat
    ) {
    }

    public record Message(String role, String content) {
    }

    public record ResponseFormat(String type, @JsonProperty("json_schema") Map<String, Object> jsonSchema) {
    }

    public record OpenAiResponse(List<Choice> choices) {
    }

    public record Choice(MessageResponse message) {
    }

    public record MessageResponse(String content) {
    }
}