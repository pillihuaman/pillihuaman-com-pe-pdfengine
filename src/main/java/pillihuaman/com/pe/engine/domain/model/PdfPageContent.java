package pillihuaman.com.pe.engine.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfPageContent(
        // >>> CHANGE
        @JsonProperty("pageNumber")
        @JsonAlias({"page_number", "pageNo"})
        int pageNumber,

        @JsonProperty("width")
        float width,

        @JsonProperty("height")
        float height,

        @JsonProperty("units")
        String units,

        @JsonProperty("rotation")
        int rotation,

        @JsonProperty("elements")
        @JsonAlias({"text_elements", "visual_elements", "textElements"})
        List<TextElement> elements,

        @JsonProperty("graphics")
        List<GraphicElement> graphics,

        @JsonProperty("rawText")
        @JsonAlias("raw_text")
        String rawText,

        @JsonProperty("hasImages")
        @JsonAlias("has_images")
        boolean hasImages,

        @JsonProperty("fontCount")
        @JsonAlias("font_count")
        int fontCount
        // <<< CHANGE
) implements Serializable {
}