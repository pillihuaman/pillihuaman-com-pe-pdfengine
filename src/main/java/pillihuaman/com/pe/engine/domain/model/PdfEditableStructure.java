package pillihuaman.com.pe.engine.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfEditableStructure(
        // >>> CHANGE
        @JsonProperty("documentId")
        @JsonAlias({"document_id", "docId", "doc_id"})
        String documentId,

        @JsonProperty("pages")
        @JsonAlias({"pages", "web_ready_pages", "webReadyPages", "page_list"})
        List<PdfPageContent> pages,

        @JsonProperty("info")
        PdfMetadata info
) {
}