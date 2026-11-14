package pillihuaman.com.pe.engine.infrastructure.adapter.outbound.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pillihuaman.com.pe.engine.application.port.outbound.PdfVisionPort;
import pillihuaman.com.pe.engine.domain.model.PdfEditorDocument;
import pillihuaman.com.pe.engine.domain.model.PdfEditorElement;
import pillihuaman.com.pe.engine.domain.model.PdfEditorElementType;
import pillihuaman.com.pe.engine.domain.model.PdfEditorPage;
import pillihuaman.com.pe.engine.infrastructure.adapter.outbound.PdfEditorHtmlBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mejora del documento editable usando OpenAI gpt-4o.
 *
 * <p>Su tarea principal es la <b>reconstrucción de tablas</b>: recibe las palabras de cada página
 * (con coordenadas) y devuelve regiones de tabla con celdas, que se integran al documento
 * sustituyendo las palabras correspondientes y regenerando el HTML.</p>
 */
@Slf4j
@Component
public class OpenAiPdfVisionAdapter implements PdfVisionPort {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String SYSTEM_PROMPT = """
            Eres un experto en análisis de layout de documentos PDF.
            Recibes las palabras de una página con sus coordenadas (x, y, w, h en puntos, origen
            arriba-izquierda) y las dimensiones de la página.
            Detecta las regiones que forman tablas (filas y columnas alineadas) y agrupa las palabras
            de cada región en celdas por fila y columna.
            Devuelve estrictamente JSON con el siguiente esquema:
            { "tables": [ { "x": number, "y": number, "width": number, "height": number,
                            "cells": [ { "x": number, "y": number, "width": number, "height": number,
                                         "text": string } ] } ] }
            Si no hay tablas, devuelve { "tables": [] }.
            Las coordenadas deben usar las mismas unidades (puntos). No añadas explicaciones.
            """;

    private static final String REFINE_SYSTEM_PROMPT = """
            Eres un experto en fidelidad visual de documentos PDF.
            Recibes la captura de una página y la lista de sus elementos de texto con posiciones actuales
            (x, y, width, height y fontSize, en puntos, origen arriba-izquierda).
            Compara la captura con las posiciones y devuelve las correcciones necesarias para que el texto
            coincida visualmente con la imagen.
            Devuelve estrictamente JSON con el esquema:
            { "corrections": [ { "id": string, "x": number, "y": number, "width": number,
                                  "height": number, "fontSize": number } ] }
            Solo incluye los elementos que necesitan corrección. Si todo coincide, devuelve { "corrections": [] }.
            No añadas explicaciones.
            """;

    private static final String OCR_SYSTEM_PROMPT = """
            Eres un motor OCR de alta precisión. Recibes la imagen de una página escaneada y sus
            dimensiones en puntos. Extrae todo el texto visible y devuelve cada línea con su posición.
            Devuelve estrictamente JSON con el esquema:
            { "lines": [ { "text": string, "x": number, "y": number, "width": number, "height": number } ] }
            (x, y = esquina superior izquierda; width/height en puntos; origen arriba-izquierda).
            Si no hay texto, devuelve { "lines": [] }. No añadas explicaciones.
            """;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:default-secret-key}")
    private String openAiApiKey;

    @Value("${application.pdf.editor.openai.model:gpt-4o}")
    private String model;

    public OpenAiPdfVisionAdapter(final WebClient.Builder builder, final ObjectMapper objectMapper) {
        this.webClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<PdfEditorDocument> enhance(final PdfEditorDocument document, final String bearerToken) {
        if (document == null || document.pages() == null || document.pages().isEmpty()) {
            return Mono.justOrEmpty(document);
        }

        return Flux.fromIterable(document.pages())
                .flatMapSequential(this::enhancePage)
                .collectList()
                .map(pages -> new PdfEditorDocument(document.documentId(), pages, document.metadata()))
                .onErrorReturn(document);
    }

    @Override
    public Mono<PdfEditorDocument> refine(
            final PdfEditorDocument document, final List<String> screenshots, final String bearerToken) {
        if (document == null || document.pages() == null || document.pages().isEmpty()) {
            return Mono.justOrEmpty(document);
        }
        final List<String> shots = screenshots == null ? List.of() : screenshots;

        return Flux.fromIterable(document.pages())
                .flatMapSequential(page -> refinePage(page, shotFor(page, shots)))
                .collectList()
                .map(pages -> new PdfEditorDocument(document.documentId(), pages, document.metadata()))
                .onErrorReturn(document);
    }

    private String shotFor(final PdfEditorPage page, final List<String> shots) {
        final int index = page.pageNumber() - 1;
        return (index >= 0 && index < shots.size()) ? shots.get(index) : null;
    }

    private Mono<PdfEditorPage> refinePage(final PdfEditorPage page, final String screenshot) {
        if (screenshot == null || screenshot.isBlank()) {
            return Mono.just(page);
        }
        return Mono.fromCallable(() -> buildRefineContent(page))
                .flatMap(content -> callVision(content, screenshot))
                .map(corrections -> applyCorrections(page, corrections))
                .onErrorReturn(page);
    }

    private Mono<List<Correction>> callVision(final String refineContent, final String screenshot) {
        final Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", refineContent);

        final Map<String, Object> imageUrl = new HashMap<>();
        imageUrl.put("url", screenshot);

        final Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);

        final Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", REFINE_SYSTEM_PROMPT);

        final Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", List.of(textPart, imagePart));

        final Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(systemMessage, userMessage));
        body.put("response_format", Map.of("type", "json_object"));

        return webClient.post()
                .uri(OPENAI_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .timeout(TIMEOUT)
                .flatMap(this::parseCorrections);
    }

    private Mono<List<Correction>> parseCorrections(final OpenAiResponse response) {
        return Mono.fromCallable(() -> {
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return List.<Correction>of();
            }
            final String content = response.choices().get(0).message().content();
            if (content == null || content.isBlank()) {
                return List.<Correction>of();
            }
            final RefineResult result = objectMapper.readValue(content, RefineResult.class);
            return result.corrections() == null ? List.<Correction>of() : result.corrections();
        }).onErrorResume(e -> {
            log.warn("No se pudo interpretar las correcciones de OpenAI: {}", e.getMessage());
            return Mono.just(List.<Correction>of());
        });
    }

    private String buildRefineContent(final PdfEditorPage page) throws JsonProcessingException {
        final List<Map<String, Object>> elementList = new ArrayList<>();
        for (PdfEditorElement e : page.elements()) {
            if (e.type() != PdfEditorElementType.TEXT) {
                continue;
            }
            final Map<String, Object> m = new HashMap<>();
            m.put("id", e.id());
            m.put("text", e.text() == null ? "" : e.text());
            m.put("x", e.x());
            m.put("y", e.y());
            m.put("width", e.width());
            m.put("height", e.height());
            m.put("fontSize", e.style() != null ? e.style().getOrDefault("fontSize", "10") : "10");
            elementList.add(m);
        }
        final Map<String, Object> payload = new HashMap<>();
        payload.put("pageWidth", page.widthPt());
        payload.put("pageHeight", page.heightPt());
        payload.put("elements", elementList);
        return objectMapper.writeValueAsString(payload);
    }

    private PdfEditorPage applyCorrections(final PdfEditorPage page, final List<Correction> corrections) {
        if (corrections == null || corrections.isEmpty()) {
            return page;
        }
        final Map<String, Correction> byId = new HashMap<>();
        for (Correction c : corrections) {
            if (c.id() != null) {
                byId.put(c.id(), c);
            }
        }

        final List<PdfEditorElement> elements = new ArrayList<>();
        for (PdfEditorElement e : page.elements()) {
            final Correction c = byId.get(e.id());
            if (c != null && e.type() == PdfEditorElementType.TEXT) {
                final Map<String, String> style =
                        new LinkedHashMap<>(e.style() == null ? Map.of() : e.style());
                if (c.fontSize() != null && c.fontSize() > 0) {
                    style.put("fontSize", PdfEditorHtmlBuilder.formatPt(c.fontSize()));
                }
                elements.add(new PdfEditorElement(
                        e.id(), e.type(),
                        c.x() != null ? c.x() : e.x(),
                        c.y() != null ? c.y() : e.y(),
                        c.width() != null ? c.width() : e.width(),
                        c.height() != null ? c.height() : e.height(),
                        e.text(), style, e.src(), e.children(), e.semanticType()));
            } else {
                elements.add(e);
            }
        }

        final String html = PdfEditorHtmlBuilder.buildPageHtml(
                page.pageNumber(), page.widthPt(), page.heightPt(), elements);
        return new PdfEditorPage(page.pageNumber(), page.widthPt(), page.heightPt(), html, elements);
    }

    private Mono<PdfEditorPage> enhancePage(final PdfEditorPage page) {
        final List<PdfEditorElement> elements = page.elements() == null ? List.of() : page.elements();
        final List<PdfEditorElement> words = elements.stream()
                .filter(e -> e.type() == PdfEditorElementType.TEXT)
                .toList();

        if (words.isEmpty()) {
            // Posible página escaneada (sin capa de texto): OCR sobre la imagen de fondo.
            return elements.stream()
                    .filter(e -> e.type() == PdfEditorElementType.IMAGE && e.src() != null)
                    .findFirst()
                    .map(image -> ocrPage(page, image.src()))
                    .orElseGet(() -> Mono.just(page));
        }

        return detectTables(page, words)
                .map(tables -> mergeTables(page, tables))
                .onErrorReturn(page);
    }

    private Mono<PdfEditorPage> ocrPage(final PdfEditorPage page, final String imageDataUri) {
        return callOcr(imageDataUri, page)
                .map(boxes -> applyOcr(page, boxes))
                .onErrorReturn(page);
    }

    private Mono<List<OcrBox>> callOcr(final String imageDataUri, final PdfEditorPage page) {
        final Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "pageWidth: " + PdfEditorHtmlBuilder.formatPt(page.widthPt())
                + ", pageHeight: " + PdfEditorHtmlBuilder.formatPt(page.heightPt()));

        final Map<String, Object> imageUrl = new HashMap<>();
        imageUrl.put("url", imageDataUri);
        final Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);

        final Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", OCR_SYSTEM_PROMPT);

        final Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", List.of(textPart, imagePart));

        final Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(systemMessage, userMessage));
        body.put("response_format", Map.of("type", "json_object"));

        return webClient.post()
                .uri(OPENAI_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .timeout(TIMEOUT)
                .flatMap(this::parseOcr);
    }

    private Mono<List<OcrBox>> parseOcr(final OpenAiResponse response) {
        return Mono.fromCallable(() -> {
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return List.<OcrBox>of();
            }
            final String content = response.choices().get(0).message().content();
            if (content == null || content.isBlank()) {
                return List.<OcrBox>of();
            }
            final OcrResult result = objectMapper.readValue(content, OcrResult.class);
            return result.lines() == null ? List.<OcrBox>of() : result.lines();
        }).onErrorResume(e -> {
            log.warn("No se pudo interpretar el OCR de OpenAI: {}", e.getMessage());
            return Mono.just(List.<OcrBox>of());
        });
    }

    private PdfEditorPage applyOcr(final PdfEditorPage page, final List<OcrBox> lines) {
        if (lines == null || lines.isEmpty()) {
            return page;
        }
        // Conserva la imagen de fondo (el escaneo) y superpone el texto OCR editable.
        final List<PdfEditorElement> elements = new ArrayList<>(page.elements());
        for (OcrBox line : lines) {
            if (line.text() == null || line.text().isBlank()) {
                continue;
            }
            elements.add(new PdfEditorElement(
                    UUID.randomUUID().toString(),
                    PdfEditorElementType.TEXT,
                    line.x() != null ? line.x() : 0f,
                    line.y() != null ? line.y() : 0f,
                    line.width() != null ? line.width() : 0f,
                    line.height() != null ? line.height() : 0f,
                    line.text(), Map.of("fontSize", "10"), null, null, null));
        }
        final String html = PdfEditorHtmlBuilder.buildPageHtml(
                page.pageNumber(), page.widthPt(), page.heightPt(), elements);
        return new PdfEditorPage(page.pageNumber(), page.widthPt(), page.heightPt(), html, elements);
    }

    private Mono<List<DetectedTable>> detectTables(
            final PdfEditorPage page, final List<PdfEditorElement> words) {
        return Mono.fromCallable(() -> buildUserContent(page, words))
                .flatMap(this::callOpenAi)
                .onErrorResume(e -> {
                    log.warn("OpenAI no pudo analizar la página {}: {}", page.pageNumber(), e.getMessage());
                    return Mono.just(List.of());
                })
                .defaultIfEmpty(List.of());
    }

    private Mono<List<DetectedTable>> callOpenAi(final String userContent) {
        final Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userContent)),
                "response_format", Map.of("type", "json_object"));

        return webClient.post()
                .uri(OPENAI_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .timeout(TIMEOUT)
                .flatMap(this::parseContent);
    }

    private Mono<List<DetectedTable>> parseContent(final OpenAiResponse response) {
        return Mono.fromCallable(() -> {
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return List.<DetectedTable>of();
            }
            final String content = response.choices().get(0).message().content();
            if (content == null || content.isBlank()) {
                return List.<DetectedTable>of();
            }
            final TableDetectionResult result = objectMapper.readValue(content, TableDetectionResult.class);
            return result.tables() == null ? List.<DetectedTable>of() : result.tables();
        }).onErrorResume(e -> {
            log.warn("No se pudo interpretar la respuesta de tablas de OpenAI: {}", e.getMessage());
            return Mono.just(List.<DetectedTable>of());
        });
    }

    private String buildUserContent(final PdfEditorPage page, final List<PdfEditorElement> words)
            throws JsonProcessingException {
        final List<Map<String, Object>> wordList = new ArrayList<>();
        for (PdfEditorElement w : words) {
            final Map<String, Object> m = new HashMap<>();
            m.put("id", w.id());
            m.put("text", w.text() == null ? "" : w.text());
            m.put("x", w.x());
            m.put("y", w.y());
            m.put("w", w.width());
            m.put("h", w.height());
            wordList.add(m);
        }

        final Map<String, Object> payload = new HashMap<>();
        payload.put("pageWidth", page.widthPt());
        payload.put("pageHeight", page.heightPt());
        payload.put("words", wordList);
        return objectMapper.writeValueAsString(payload);
    }

    private PdfEditorPage mergeTables(final PdfEditorPage page, final List<DetectedTable> tables) {
        if (tables == null || tables.isEmpty()) {
            return page;
        }

        final List<PdfEditorElement> elements = new ArrayList<>();
        for (PdfEditorElement e : page.elements()) {
            if (e.type() == PdfEditorElementType.TEXT && insideAnyTable(e, tables)) {
                continue; // la palabra queda absorbida por una tabla
            }
            elements.add(e);
        }

        for (DetectedTable table : tables) {
            final List<PdfEditorElement> cells = new ArrayList<>();
            for (DetectedCell cell : table.cells()) {
                cells.add(new PdfEditorElement(
                        UUID.randomUUID().toString(),
                        PdfEditorElementType.TABLE_CELL,
                        cell.x(), cell.y(), cell.width(), cell.height(),
                        cell.text(), Map.of("fontSize", "10"), null, null, null));
            }
            elements.add(new PdfEditorElement(
                    UUID.randomUUID().toString(),
                    PdfEditorElementType.TABLE,
                    table.x(), table.y(), table.width(), table.height(),
                    null, Map.of(), null, cells, null));
        }

        final String html = PdfEditorHtmlBuilder.buildPageHtml(
                page.pageNumber(), page.widthPt(), page.heightPt(), elements);
        return new PdfEditorPage(page.pageNumber(), page.widthPt(), page.heightPt(), html, elements);
    }

    private boolean insideAnyTable(final PdfEditorElement element, final List<DetectedTable> tables) {
        final float cx = element.x() + element.width() / 2f;
        final float cy = element.y() + element.height() / 2f;
        for (DetectedTable table : tables) {
            if (cx >= table.x() && cx <= table.x() + table.width()
                    && cy >= table.y() && cy <= table.y() + table.height()) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================================
    // Modelos de respuesta
    // =====================================================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TableDetectionResult(List<DetectedTable> tables) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DetectedTable(float x, float y, float width, float height, List<DetectedCell> cells) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DetectedCell(float x, float y, float width, float height, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RefineResult(List<Correction> corrections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Correction(String id, Float x, Float y, Float width, Float height, Float fontSize) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OcrResult(List<OcrBox> lines) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OcrBox(String text, Float x, Float y, Float width, Float height) {
    }
}
