# Editor de PDF — Contrato y arquitectura

Pipeline **PDF → HTML editable → PDF** con asistencia de IA, en el backend `pillihuaman-com-pe-engine`.

## Flujo

```
PDF ──► /editor/import ──► PDFBox extrae (texto+color+imágenes) ──► IA reconstruye tablas
                                    │
                                    ▼
                        PdfEditorDocument (HTML + elementos)
                                    │
                        frontend WYSIWYG edita (HTML/elementos)
                                    │
                                    ▼
                        /editor/export ──► openhtmltopdf por página ──► PDFBox merge ──► PDF final
```

## Endpoints

Base: `/private/v1/pdf/editor` (requiere JWT `Authorization`; cubierto por `/private/v1/pdf/**`).

### 1. Importar — `POST /editor/import` (multipart)
- `file`: PDF (`multipart/form-data`).
- Respuesta: `RespBase<PdfEditorDocument>` (200).

### 2. Exportar — `POST /editor/export` (JSON → PDF)
```json
{ "fileName": "factura.pdf", "pages": ["<!DOCTYPE html>…", "…"] }
```
- Cada `pages[i]` es el HTML completo (ya editado) de una página, con su `@page size`.
- Respuesta: PDF descargable (`application/pdf`).

### 3. Refinar — `POST /editor/refine` (JSON)
```json
{ "document": { "…": "PdfEditorDocument" }, "screenshots": ["data:image/png;base64,…"] }
```
- `screenshots[i]` = captura de la página `i` (opcional). La IA (OpenAI visión) corrige
  posiciones/tamaños/fuentes para igualar la captura.
- Respuesta: `RespBase<PdfEditorDocument>` refinado.

## Modelo `PdfEditorDocument`

```json
{
  "documentId": "doc_1699999999999",
  "metadata": null,
  "pages": [
    {
      "pageNumber": 1,
      "widthPt": 595,
      "heightPt": 842,
      "html": "<!DOCTYPE html>…",
      "elements": [
        {
          "id": "uuid",
          "type": "TEXT",          // TEXT | IMAGE | RECT | TABLE | TABLE_CELL
          "x": 72, "y": 100,       // esquina superior izquierda, en pt
          "width": 120, "height": 14,
          "text": "FACTURA",
          "style": { "fontFamily": "Helvetica", "fontSize": "12", "color": "#000000",
                     "fontWeight": "bold", "fontStyle": "normal" },
          "src": null,              // data URI en IMAGE
          "children": []            // celdas en TABLE
        }
      ]
    }
  ]
}
```

Unidades: **puntos (pt)** en todo el contrato (coincide con PDFBox y openhtmltopdf, sin conversión).

## Configuración

```properties
application.pdf.editor.ai-provider=openai   # openai | neuroia | none
application.pdf.editor.openai.model=gpt-4o
openai.api.key=sk-...
```

## Proveedores de IA (puerto `PdfVisionPort`)

| Provider | `enhance` (importar) | `refine` (fidelidad) |
|----------|----------------------|----------------------|
| `openai` | Tablas + OCR de escaneados (gpt-4o visión, JSON) | Corrige posiciones vs. screenshot (visión) |
| `neuroia` | Clasifica semánticamente los textos (`data-semantic`) | bridge pendiente (`PdfEditableStructure`) |
| `none`   | sin cambios | sin cambios |

Tolerancia a fallos: si la IA falla o no está configurada, el flujo devuelve el documento
determinístico sin cambios (nunca bloquea la importación/exportación).

## Estructura de clases (hexagonal)

```
domain/model            PdfEditorDocument, PdfEditorPage, PdfEditorElement, PdfEditorElementType
application/port/inbound  PdfEditorUseCase
application/port/outbound PdfToHtmlPort, PdfVisionPort
application/service     PdfEditorService
application/dto         PdfEditorExportRequest, PdfEditorRefineRequest
infrastructure/adapter/inbound/rest   PdfEditorController
infrastructure/adapter/outbound       PdfBoxToHtmlAdapter, PdfEditorHtmlBuilder
infrastructure/adapter/outbound/external  OpenAiPdfVisionAdapter, NeuroIaPdfVisionAdapter,
                                          NoOpPdfVisionAdapter, PdfVisionRouter
```

## Pendiente

1. Puente NeuroIA `refine` (comparación visual sobre `PdfEditableStructure`).
2. Verificación de compilación (ver más abajo).
3. Frontend WYSIWYG (CKEditor/TinyMCE/GrapesJS) que consuma este contrato.

## Compilar

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
mvn clean compile
```

Requiere JDK 25 y acceso al repositorio Maven (el parent `spring-boot-starter-parent:4.0.0-M1`
es milestone; configurar `repo.spring.io/milestone` si no está disponible en el mirror).
