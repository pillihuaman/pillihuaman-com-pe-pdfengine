package pillihuaman.com.pe.engine.document.domain.exception;

import pillihuaman.com.pe.engine.domain.exception.BusinessLogicException;

/**
 * Excepción de negocio específica del módulo de generación de documentos.
 * Extiende {@link BusinessLogicException} para ser manejada por el
 * {@code ReactiveGlobalExceptionHandler} existente (HTTP 422).
 */
public class PdfGenerationException extends BusinessLogicException {

    public PdfGenerationException(final String message) {
        super(message);
    }

    public PdfGenerationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
