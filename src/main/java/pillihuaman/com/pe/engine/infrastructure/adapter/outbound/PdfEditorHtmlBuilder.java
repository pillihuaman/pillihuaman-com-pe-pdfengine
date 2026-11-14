package pillihuaman.com.pe.engine.infrastructure.adapter.outbound;

import pillihuaman.com.pe.engine.domain.model.PdfEditorElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Construye el HTML editable (posicionamiento absoluto, unidades en pt) a partir de la lista
 * estructurada de elementos. Se comparte entre la conversión determinística (PdfBoxToHtmlAdapter)
 * y la capa de IA (que regenera el HTML tras reconstruir tablas).
 */
public final class PdfEditorHtmlBuilder {

    private PdfEditorHtmlBuilder() {
    }

    public static String buildPageHtml(
            final int pageNumber, final float widthPt, final float heightPt,
            final List<PdfEditorElement> elements) {

        final StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>");
        sb.append("@page{size:").append(formatPt(widthPt)).append("pt ")
                .append(formatPt(heightPt)).append("pt;margin:0;}");
        sb.append("*{box-sizing:border-box;}");
        sb.append("body{margin:0;padding:0;}");
        sb.append(".pdf-page{position:relative;width:").append(formatPt(widthPt)).append("pt;height:")
                .append(formatPt(heightPt)).append("pt;overflow:hidden;background:#ffffff;}");
        sb.append(".el{position:absolute;margin:0;padding:0;}");
        sb.append("</style></head><body><div class=\"pdf-page\" data-page=\"")
                .append(pageNumber).append("\">");
        for (PdfEditorElement element : elements) {
            sb.append(buildElementHtml(element));
        }
        sb.append("</div></body></html>");
        return sb.toString();
    }

    public static String buildElementHtml(final PdfEditorElement element) {
        switch (element.type()) {
            case TEXT, TABLE_CELL -> {
                final StringBuilder style = new StringBuilder(basePositionStyle(element));
                style.append("width:").append(formatPt(element.width())).append("pt;")
                        .append("height:").append(formatPt(element.height())).append("pt;")
                        .append("white-space:nowrap;overflow:hidden;");
                appendStyle(style, element.style());
                final String semantic = element.semanticType() == null ? ""
                        : " data-semantic=\"" + escapeHtml(element.semanticType()) + "\"";
                return "<div class=\"el\" data-id=\"" + element.id() + "\" data-type=\"text\"" + semantic
                        + " style=\"" + style + "\">" + escapeHtml(element.text()) + "</div>";
            }
            case IMAGE -> {
                final StringBuilder style = new StringBuilder(basePositionStyle(element));
                style.append("width:").append(formatPt(element.width())).append("pt;")
                        .append("height:").append(formatPt(element.height())).append("pt;");
                return "<img class=\"el\" data-id=\"" + element.id() + "\" data-type=\"image\" style=\""
                        + style + "\" src=\"" + (element.src() == null ? "" : element.src()) + "\"/>";
            }
            case RECT -> {
                final StringBuilder style = new StringBuilder(basePositionStyle(element));
                style.append("width:").append(formatPt(element.width())).append("pt;")
                        .append("height:").append(formatPt(element.height())).append("pt;");
                appendStyle(style, element.style());
                return "<div class=\"el\" data-id=\"" + element.id() + "\" data-type=\"rect\" style=\""
                        + style + "\"></div>";
            }
            case TABLE -> {
                return buildTableHtml(element);
            }
            default -> {
                return "";
            }
        }
    }

    private static String buildTableHtml(final PdfEditorElement table) {
        final List<PdfEditorElement> cells = table.children() == null ? List.of() : table.children();

        final TreeMap<Integer, List<PdfEditorElement>> rows = new TreeMap<>();
        for (PdfEditorElement cell : cells) {
            rows.computeIfAbsent(Math.round(cell.y()), k -> new ArrayList<>()).add(cell);
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"el\" data-id=\"").append(table.id()).append("\" data-type=\"table\" style=\"")
                .append(basePositionStyle(table))
                .append("width:").append(formatPt(table.width())).append("pt;")
                .append("height:").append(formatPt(table.height())).append("pt;\">");
        sb.append("<table style=\"width:100%;height:100%;border-collapse:collapse;\">");
        for (List<PdfEditorElement> row : rows.values()) {
            row.sort(Comparator.comparingDouble(PdfEditorElement::x));
            sb.append("<tr>");
            for (PdfEditorElement cell : row) {
                sb.append("<td style=\"").append(buildCellStyle(cell)).append("\">")
                        .append(escapeHtml(cell.text())).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table></div>");
        return sb.toString();
    }

    private static String buildCellStyle(final PdfEditorElement cell) {
        final StringBuilder sb = new StringBuilder("border:0.5pt solid #000000;vertical-align:middle;padding:0;");
        appendStyle(sb, cell.style());
        return sb.toString();
    }

    private static String basePositionStyle(final PdfEditorElement element) {
        return "left:" + formatPt(element.x()) + "pt;top:" + formatPt(element.y()) + "pt;";
    }

    private static void appendStyle(final StringBuilder sb, final Map<String, String> style) {
        if (style == null) {
            return;
        }
        for (Map.Entry<String, String> entry : style.entrySet()) {
            final String property = toCssProperty(entry.getKey());
            String value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (("font-size".equals(property) || "letter-spacing".equals(property))
                    && !value.endsWith("pt")) {
                value = value + "pt";
            }
            sb.append(property).append(':').append(value).append(';');
        }
    }

    private static String toCssProperty(final String key) {
        return switch (key) {
            case "fontFamily" -> "font-family";
            case "fontSize" -> "font-size";
            case "fontWeight" -> "font-weight";
            case "fontStyle" -> "font-style";
            case "letterSpacing" -> "letter-spacing";
            case "lineHeight" -> "line-height";
            case "textAlign" -> "text-align";
            case "backgroundColor" -> "background-color";
            case "borderColor" -> "border-color";
            case "borderWidth" -> "border-width";
            case "borderStyle" -> "border-style";
            default -> key;
        };
    }

    public static String formatPt(final float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static String escapeHtml(final String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
