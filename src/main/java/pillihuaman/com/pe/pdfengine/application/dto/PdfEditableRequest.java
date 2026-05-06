package pillihuaman.com.pe.pdfengine.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import pillihuaman.com.pe.pdfengine.domain.model.PdfPageContent;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfEditableRequest(


        @JsonProperty("base64OriginalPdf")
        String base64OriginalPdf,

        @JsonProperty("pages")
        List<PdfPageContent> pages
) {
}