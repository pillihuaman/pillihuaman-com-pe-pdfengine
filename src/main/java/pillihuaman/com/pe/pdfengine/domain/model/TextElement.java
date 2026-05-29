package pillihuaman.com.pe.pdfengine.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TextElement(
        // >>> CHANGE
        @JsonProperty("id")
        String id,

        @JsonProperty("text")
        @JsonAlias({"content", "value"})
        String text,

        float left,
        float top,

        @JsonProperty("width")
        float width,

        @JsonProperty("height")
        float height,

        @JsonProperty("fontSize")
        @JsonAlias({"font_size", "size"})
        float fontSize,

        @JsonProperty("fontName")
        @JsonAlias({"font_family", "font"})
        String fontName,

        @JsonProperty("fontWeight")
        @JsonAlias("font_weight")
        String fontWeight,

        @JsonProperty("isItalic")
        @JsonAlias("italic")
        boolean isItalic,
        @JsonProperty("type") String type,
        @JsonProperty("resourceReference")
        String resourceReference,
        @JsonProperty("letterSpacing") String letterSpacing,
        @JsonProperty("lineHeight") String lineHeight,
        @JsonProperty("textAlign") String textAlign,
        @JsonProperty("cssTransform") String cssTransform

) implements Serializable {
}